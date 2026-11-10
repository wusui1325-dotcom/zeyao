package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Uses each vendor's lightest flash-class model to decide whether a URL is a
 * news article. The call is non-streaming, thinking off, low effort, 8 tokens.
 */
object NewsClassifier {
    data class Result(val isNews: Boolean, val fromCache: Boolean)

    private val cache = ConcurrentHashMap<String, Boolean>()

    fun cached(canonicalUrl: String): Boolean? = cache[canonicalUrl]

    fun remember(canonicalUrl: String, isNews: Boolean) {
        if (cache.size > 300) cache.clear()
        cache[canonicalUrl] = isNews
    }

    suspend fun classify(
        provider: AiProvider,
        apiKey: String,
        title: String,
        url: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val models = flashModels(provider)
        var lastError: Exception? = null
        for ((index, model) in models.withIndex()) {
            try {
                val raw = when (provider) {
                    AiProvider.GEMINI -> geminiOnce(apiKey, model, title, url)
                    AiProvider.CLAUDE -> claudeOnce(apiKey, model, title, url)
                    else -> openAiStyleOnce(provider, apiKey, model, title, url)
                }
                return@withContext parseYesNo(raw)
            } catch (e: Exception) {
                lastError = e
                if (index == models.lastIndex) break
            }
        }
        throw lastError ?: AiInitException("無法判斷是否為新聞")
    }

    private fun flashModels(provider: AiProvider): List<String> = when (provider) {
        AiProvider.DEEPSEEK -> listOf("deepseek-flash", "deepseek-v4-flash")
        AiProvider.OPENAI -> listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1-nano")
        AiProvider.GEMINI -> listOf(
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash-lite-001",
            "gemini-2.0-flash",
            "gemini-1.5-flash-8b",
        )
        AiProvider.CLAUDE -> listOf(
            "claude-haiku-4-0",
            "claude-3-5-haiku-latest",
            "claude-3-haiku-20240307",
        )
        AiProvider.GROK -> listOf("grok-3-mini", "grok-2-mini", "grok-3", "grok-2")
    }

    private fun prompt(title: String, url: String): String = buildString {
        appendLine("判斷這個網頁是不是一則新聞或時事報導。只回覆 YES 或 NO，不要其他文字。")
        appendLine("YES：新聞、即時報導、專題、評論、通訊社稿、深度報導。")
        appendLine("NO：搜尋、登入、購物、社群動態、影片首頁、工具、設定、一般公司或個人網站、僅有頻道首頁。")
        append("標題："); appendLine(title.ifBlank { "（無）" })
        append("網址："); append(url)
    }

    private fun openAiStyleOnce(
        provider: AiProvider,
        apiKey: String,
        model: String,
        title: String,
        url: String,
    ): String {
        val endpoint = when (provider) {
            AiProvider.DEEPSEEK -> "https://api.deepseek.com/chat/completions"
            AiProvider.GROK -> "https://api.x.ai/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt(title, url)),
                ),
            )
            .put("temperature", 0)
            .put("max_tokens", 8)
            .put("stream", false)
        if (provider == AiProvider.DEEPSEEK) {
            body.put("thinking", JSONObject().put("type", "disabled"))
            body.put("reasoning_effort", "low")
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
        HttpClients.classify.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, provider.displayName))
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val choices = json.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            return choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty()
        }
    }

    private fun geminiOnce(apiKey: String, model: String, title: String, url: String): String {
        val generationConfig = JSONObject()
            .put("temperature", 0)
            .put("maxOutputTokens", 8)
            .put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt(title, url))),
                        ),
                ),
            )
            .put("generationConfig", generationConfig)
            .toString()
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        val request = Request.Builder()
            .url(endpoint)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.classify.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "Google Gemini"))
            }
            val obj = JSONObject(response.body?.string().orEmpty())
            val candidates = obj.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: return ""
            return buildString {
                for (i in 0 until parts.length()) {
                    append(parts.getJSONObject(i).optString("text"))
                }
            }
        }
    }

    private fun claudeOnce(apiKey: String, model: String, title: String, url: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 8)
            .put("temperature", 0)
            .put("stream", false)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put("content", prompt(title, url)),
                ),
            )
            .toString()
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        HttpClients.classify.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiInitException(friendlyHttpError(response.code, "Anthropic Claude"))
            }
            val obj = JSONObject(response.body?.string().orEmpty())
            val content = obj.optJSONArray("content") ?: return ""
            if (content.length() == 0) return ""
            return content.getJSONObject(0).optString("text")
        }
    }

    internal fun parseYesNo(text: String): Boolean {
        val t = text.trim().uppercase()
            .replace("`", "")
            .replace("\"", "")
            .replace("'", "")
        if (t.isEmpty()) return false
        val first = t.lineSequence().first().trim()
        if (first.startsWith("YES") || first == "Y" || first.startsWith("是") || first.startsWith("TRUE")) {
            return true
        }
        return false
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()
}
