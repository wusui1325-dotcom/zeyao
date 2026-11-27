package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
        HttpClients.init.newCall(request).await().use { response ->
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
        val models = AiProvider.fallbackList(AiProvider.GROK, params.fallbackModels)
        val activeCall = AtomicReference<Call?>(null)
        val cancelled = AtomicBoolean(false)
        val worker = Thread({
            var lastError: Exception? = null
            for ((index, model) in models.withIndex()) {
                if (cancelled.get()) break
                try {
                    streamOnce(apiKey, title, url, articleText, params, model, activeCall) { trySend(it) }
                    lastError = null
                    break
                } catch (e: Exception) {
                    if (cancelled.get()) {
                        lastError = null
                        break
                    }
                    lastError = e
                    val retryable = e is ModelUnavailable && index < models.lastIndex
                    if (!retryable) break
                }
            }
            activeCall.set(null)
            if (lastError != null && !cancelled.get()) close(lastError) else close()
        }, "grok-stream")
        worker.start()
        awaitClose {
            cancelled.set(true)
            activeCall.getAndSet(null)?.cancel()
            worker.interrupt()
        }
    }

    private fun streamOnce(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
        model: String,
        activeCall: AtomicReference<Call?>,
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
        val call = HttpClients.stream.newCall(request)
        activeCall.set(call)
        call.execute().use { response ->
            if (response.code == 400 || response.code == 404) {
                throw ModelUnavailable(friendlyHttpError(response.code, "xAI Grok"))
            }
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "xAI Grok"))
            }
            SseReader.readEvents(response) { data ->
                val text = extractOpenAiDelta(data)
                if (text.isNotEmpty()) onDelta(text)
            }
        }
    }

    private class ModelUnavailable(message: String) : AiInitException(message)

    private companion object {
        const val BASE = "https://api.x.ai/v1"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
