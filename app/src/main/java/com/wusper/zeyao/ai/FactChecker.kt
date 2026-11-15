package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Authenticity check: extract key facts with the same model, then search each
 * one in parallel and return the combined results for the summary prompt.
 */
object FactChecker {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class Source(val query: String, val title: String, val url: String, val snippet: String)

    suspend fun check(
        apiKey: String,
        providerId: String,
        model: String,
        title: String,
        url: String,
        articleText: String,
        fallback: Boolean = false,
        temperature: Double = 0.2,
        maxTokens: Int = 200,
        searchCount: Int = 2,
    ): List<Source> = withContext(Dispatchers.IO) {
        val facts = extractFacts(apiKey, providerId, model, title, url, articleText, temperature, maxTokens)
        if (facts.isEmpty()) return@withContext emptyList()
        val count = searchCount.coerceIn(1, 10)
        coroutineScope {
            facts.map { fact ->
                async { searchOne(apiKey, fact, fallback, count) }
            }.awaitAll().flatten()
        }
    }

    private fun extractFacts(
        apiKey: String,
        providerId: String,
        model: String,
        title: String,
        url: String,
        articleText: String,
        temperature: Double,
        maxTokens: Int,
    ): List<String> {
        val prompt = buildString {
            appendLine("從以下文章抽出關鍵詞、關鍵人物、關鍵事件、關鍵時間。")
            appendLine("至少 10 個。每個不超過 10 個中文字或 20 個英文字。")
            appendLine("每行一個，不要編號，不要解釋。")
            appendLine("標題：$title")
            appendLine("網址：$url")
            appendLine("正文：")
            append(articleText.take(6000))
        }
        val content = callChat(apiKey, providerId, model, prompt, temperature, maxTokens) ?: return emptyList()
        return content.lines()
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotBlank() && it.length <= 20 }
            .distinct()
            .take(12)
    }

    private suspend fun searchOne(apiKey: String, query: String, fallback: Boolean, count: Int): List<Source> {
        val hits = DeepSeekSearch.searchWithFallback(apiKey, query, fallback, count = count)
        return hits.map { Source(query, it.title, it.url, it.summary.ifBlank { it.snippet }) }
    }

    fun format(sources: List<Source>): String {
        if (sources.isEmpty()) return ""
        return buildString {
            appendLine("以下是真實性與時效性核對的搜索結果：")
            sources.take(20).forEachIndexed { i, s ->
                append(i + 1).append(". [").append(s.query).append("] ")
                if (s.title.isNotBlank()) append(s.title).append(" ")
                if (s.url.isNotBlank()) append("(").append(s.url).append(") ")
                if (s.snippet.isNotBlank()) append(s.snippet.take(200))
                appendLine()
            }
        }
    }

    private fun callChat(
        apiKey: String,
        providerId: String,
        model: String,
        prompt: String,
        temperature: Double,
        maxTokens: Int,
    ): String? {
        val base = when (providerId) {
            "deepseek" -> "https://api.deepseek.com"
            "grok" -> "https://api.x.ai/v1"
            "openai" -> "https://api.openai.com/v1"
            else -> return null
        }
        val safeModel = model.trim().ifBlank { return null }
        val temp = if (temperature.isNaN() || temperature.isInfinite()) 0.2 else temperature.coerceIn(0.0, 2.0)
        val tokens = if (maxTokens <= 0) 200 else maxTokens.coerceIn(32, 800)
        val body = JSONObject()
            .put("model", safeModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_tokens", tokens)
            .put("temperature", temp)
            .put("stream", false)
            .toString()
        val request = Request.Builder()
            .url("$base/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val text = response.body?.string().orEmpty()
                val obj = JSONObject(text)
                val choices = obj.optJSONArray("choices") ?: return@use null
                if (choices.length() == 0) return@use null
                choices.getJSONObject(0).optJSONObject("message")?.optString("content")
            }
        }.getOrNull()
    }
}
