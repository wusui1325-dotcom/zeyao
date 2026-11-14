package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini generateContent (SSE via alt=sse).
 *
 * Mapping notes:
 * - Default model: gemini-2.0-flash.
 * - thinkingConfig.thinkingBudget = 0 turns thinking OFF when the field is
 *   accepted; ignored by models that do not support it.
 * - There is no official "reasoning_effort = high" on flash models.
 * - temperature 1.2, maxOutputTokens 1000, streamed.
 */
class GeminiClient : AiClient {
    override val provider: AiProvider = AiProvider.GEMINI

    override suspend fun initialize(apiKey: String): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models".toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        val request = Request.Builder().url(url).get().build()
        HttpClients.init.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "Google Gemini"))
            }
            AiProvider.GEMINI.defaultModel
        }
    }

    override fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
    ): Flow<String> = callbackFlow {
        val models = AiProvider.fallbackList(AiProvider.GEMINI, params.fallbackModels)
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
        }, "gemini-stream")
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
        val generationConfig = JSONObject()
            .put("temperature", params.temperature)
            .put("maxOutputTokens", params.maxTokens)
        // thinkingBudget=0 is the official way to disable thinking on models that support it.
        if (!params.thinkingEnabled) {
            generationConfig.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(
                                JSONObject().put("text", SummaryPrompt.build(title, url, articleText, params.language, params.summaryLength, params.searchNews, params.antiFraud)),
                            ),
                        ),
                ),
            )
            .put("generationConfig", generationConfig)
            .toString()
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .addQueryParameter("alt", "sse")
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.stream.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "Google Gemini"))
            }
            SseReader.readEvents(response) { data ->
                val text = extractGeminiDelta(data)
                if (text.isNotEmpty()) onDelta(text)
            }
        }
    }

    private fun extractGeminiDelta(data: String): String = runCatching {
        val obj = JSONObject(data)
        val candidates = obj.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        buildString {
            for (i in 0 until parts.length()) {
                append(parts.getJSONObject(i).optString("text"))
            }
        }
    }.getOrDefault("")

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
