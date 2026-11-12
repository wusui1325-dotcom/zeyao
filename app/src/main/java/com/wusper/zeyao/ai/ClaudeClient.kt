package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic Messages API (SSE).
 *
 * Mapping notes:
 * - Default model: claude-sonnet-4-0, with a fallback list of official aliases.
 * - Extended thinking is omitted (thinking OFF). Anthropic extended thinking is
 *   opt-in via a "thinking" object; we do not send it.
 * - There is no official reasoning_effort field; high "intensity" is not mapped.
 * - temperature 1.2, max_tokens 1000, stream true.
 */
class ClaudeClient : AiClient {
    override val provider: AiProvider = AiProvider.CLAUDE

    override suspend fun initialize(apiKey: String): String = withContext(Dispatchers.IO) {
        // Anthropic has no stable unauthenticated-free models list used here;
        // a 1-token Messages ping verifies the key without producing a summary.
        val body = JSONObject()
            .put("model", AiProvider.CLAUDE.defaultModel)
            .put("max_tokens", 1)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", "ping"),
                ),
            )
            .toString()
        val request = Request.Builder()
            .url("$BASE/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.init.newCall(request).execute().use { response ->
            if (response.code == 404) {
                // Model alias may differ; key is still checked by falling through to 401/403.
                // Retry is not needed for init if the code is 400 with a valid key.
            }
            if (response.code == 401 || response.code == 403) {
                throw AiInitException(friendlyHttpError(response.code, "Anthropic Claude"))
            }
            if (response.code !in 200..299 && response.code != 400 && response.code != 404) {
                throw AiInitException(friendlyHttpError(response.code, "Anthropic Claude"))
            }
            // 400/404 here often means model alias mismatch but a valid key.
            AiProvider.CLAUDE.defaultModel
        }
    }

    override fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
    ): Flow<String> = callbackFlow {
        val models = listOf(
            "claude-sonnet-4-0",
            "claude-sonnet-4-20250514",
            "claude-3-7-sonnet-latest",
            "claude-3-5-sonnet-latest",
        )
        val worker = Thread({
            var lastError: Exception? = null
            for ((index, model) in models.withIndex()) {
                try {
                    streamOnce(apiKey, title, url, articleText, params, model) { trySend(it) }
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    if (index == models.lastIndex) break
                    if (e is AiInitException) continue
                }
            }
            if (lastError != null) close(lastError) else close()
        }, "claude-stream")
        worker.start()
        awaitClose { worker.interrupt() }
    }

    private fun streamOnce(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
        model: String,
        onDelta: (String) -> Unit,
    ) {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", params.maxTokens)
            .put("temperature", params.temperature)
            .put("stream", true)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("content", SummaryPrompt.build(title, url, articleText, params.language, params.summaryLength, params.searchNews, params.antiFraud)),
                ),
            )
            .toString()
        val request = Request.Builder()
            .url("$BASE/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.stream.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "Anthropic Claude"))
            }
            SseReader.readEvents(response) { data ->
                val text = extractClaudeDelta(data)
                if (text.isNotEmpty()) onDelta(text)
            }
        }
    }

    private fun extractClaudeDelta(data: String): String = runCatching {
        val obj = JSONObject(data)
        when (obj.optString("type")) {
            "content_block_delta" -> {
                val delta = obj.optJSONObject("delta") ?: return ""
                if (delta.optString("type") == "text_delta") delta.optString("text") else ""
            }
            else -> ""
        }
    }.getOrDefault("")

    private companion object {
        const val BASE = "https://api.anthropic.com/v1"
        const val ANTHROPIC_VERSION = "2023-06-01"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
