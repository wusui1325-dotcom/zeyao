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
 * xAI Grok OpenAI-compatible Chat Completions API.
 *
 * Mapping notes:
 * - Default model grok-3. No official "deep thinking" flag is sent (thinking OFF).
 * - reasoning_effort is omitted; xAI does not document a matching official field
 *   for all Grok chat models. Temperature 1.2, max_tokens 1000, stream true.
 */
class GrokClient : AiClient {
    override val provider: AiProvider = AiProvider.GROK

    override suspend fun initialize(apiKey: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        HttpClients.init.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "xAI Grok"))
            }
            AiProvider.GROK.defaultModel
        }
    }

    override fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
    ): Flow<String> = callbackFlow {
        val models = listOf("grok-3", "grok-2", "grok-2-latest")
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
                    if (e is AiInitException && e.message?.contains("找不到") == true) continue
                    if (e is AiInitException && e.message?.contains("HTTP 4") == true && index < models.lastIndex) continue
                    break
                }
            }
            if (lastError != null) close(lastError) else close()
        }, "grok-stream")
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
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("content", SummaryPrompt.build(title, url, articleText, params.language, params.summaryLength, params.searchNews, params.antiFraud)),
                ),
            )
            .put("temperature", params.temperature)
            .put("max_tokens", params.maxTokens)
            .put("stream", true)
            .toString()
        val request = Request.Builder()
            .url("$BASE/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.stream.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "xAI Grok"))
            }
            SseReader.readEvents(response) { data ->
                val text = extractOpenAiDelta(data)
                if (text.isNotEmpty()) onDelta(text)
            }
        }
    }

    private companion object {
        const val BASE = "https://api.x.ai/v1"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
