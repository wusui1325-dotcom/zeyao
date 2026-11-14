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
 * DeepSeek official Chat Completions API.
 *
 * Default mapping:
 * - model: deepseek-flash (legacy deepseek-v4-flash accepted by retry)
 * - thinking: { "type": "disabled" }  (deep thinking OFF)
 * - reasoning_effort: "high"          (independent of thinking; send BOTH)
 * - temperature: 1.2
 * - max_tokens: 1000
 * - stream: true
 *
 * Temperature only applies meaningfully when thinking is off, which is the default.
 */
class DeepSeekClient : AiClient {
    override val provider: AiProvider = AiProvider.DEEPSEEK

    override suspend fun initialize(apiKey: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE/models")
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        HttpClients.init.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "DeepSeek"))
            }
            "deepseek-flash"
        }
    }

    override fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams,
    ): Flow<String> = callbackFlow {
        val models = AiProvider.fallbackList(AiProvider.DEEPSEEK, params.fallbackModels)
        val worker = Thread({
            var lastError: Exception? = null
            for ((index, model) in models.withIndex()) {
                try {
                    streamOnce(apiKey, title, url, articleText, params, model) { delta ->
                        trySend(delta)
                    }
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    val retryable = e is ModelUnavailable && index < models.lastIndex
                    if (!retryable) break
                }
            }
            if (lastError != null) close(lastError) else close()
        }, "deepseek-stream")
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
        val thinkingType = if (params.thinkingEnabled) "enabled" else "disabled"
        val effort = if (params.reasoningEffortHigh) "high" else "low"
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "content",
                        SummaryPrompt.build(
                            title, url, articleText,
                            language = params.language,
                            length = params.summaryLength,
                            searchNews = params.searchNews,
                            antiFraud = params.antiFraud,
                        ),
                    ),
                ),
            )
            .put("thinking", JSONObject().put("type", thinkingType))
            .put("reasoning_effort", effort)
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
            if (response.code == 400 || response.code == 404) {
                throw ModelUnavailable()
            }
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "DeepSeek"))
            }
            SseReader.readEvents(response) { data ->
                val text = extractOpenAiDelta(data)
                if (text.isNotEmpty()) onDelta(text)
            }
        }
    }

    private class ModelUnavailable : Exception()

    private companion object {
        const val BASE = "https://api.deepseek.com"
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun extractOpenAiDelta(data: String): String {
    return runCatching {
        val obj = JSONObject(data)
        val choices = obj.optJSONArray("choices") ?: return ""
        if (choices.length() == 0) return ""
        val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return ""
        delta.optString("content")
    }.getOrDefault("")
}

internal fun friendlyHttpError(code: Int, vendor: String): String = when (code) {
    401, 403 -> "金鑰無效或沒有權限。請檢查 $vendor API Key 格式、是否複製完整，以及帳戶額度。"
    404 -> "$vendor 找不到指定的模型或接口。已嘗試備用模型，若仍失敗請在設定中指定可用模型。"
    429 -> "$vendor 請求過於頻繁或超出配額。請稍後再試，或檢查用量與付費狀態。"
    in 500..599 -> "$vendor 服務暫時無法使用（HTTP $code）。請稍後重試，或切換其他提供者。"
    else -> "$vendor 請求失敗（HTTP $code）。請檢查網路連線、金鑰或稍後再試。"
}
