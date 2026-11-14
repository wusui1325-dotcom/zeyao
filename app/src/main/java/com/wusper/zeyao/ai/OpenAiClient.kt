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
 * OpenAI Chat Completions API.
 *
 * Mapping notes:
 * - Default model is gpt-4o: it has no "extended thinking" channel, which matches
 *   thinking OFF. Temperature 1.2 and max_tokens 1000 are sent as requested.
 * - reasoning_effort HIGH does not apply to gpt-4o. If a reasoning model is used
 *   later, send reasoning_effort=high; those models typically ignore temperature
 *   and cannot combine "thinking off" with high reasoning effort.
 * - stream: true
 */
class OpenAiClient : AiClient {
    override val provider: AiProvider = AiProvider.OPENAI

    override suspend fun initialize(apiKey: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        HttpClients.init.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "OpenAI"))
            }
            AiProvider.OPENAI.defaultModel
        }
    }

    override fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
    ): Flow<String> = callbackFlow {
        val models = AiProvider.fallbackList(AiProvider.OPENAI, params.fallbackModels)
        val worker = Thread({
            var lastError: Exception? = null
            for (model in models) {
                try {
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
                        if (response.code == 404 || response.code == 400) {
                            lastError = AiInitException(friendlyHttpError(response.code, "OpenAI"))
                            continue
                        }
                        if (!response.isSuccessful) {
                            throw AiInitException(friendlyHttpError(response.code, "OpenAI"))
                        }
                        SseReader.readEvents(response) { data ->
                            val text = extractOpenAiDelta(data)
                            if (text.isNotEmpty()) trySend(text)
                        }
                    }
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                }
            }
            if (lastError != null) close(lastError) else close()
        }, "openai-stream")
        worker.start()
        awaitClose { worker.interrupt() }
    }

    private companion object {
        const val BASE = "https://api.openai.com/v1"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
