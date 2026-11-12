package com.wusper.zeyao.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * DeepSeek Search API (POST /v1/tools/search).
 * Docs: https://api-docs.deepseek.com/api/search
 *
 * Used when the user enables "搜索新聞" and the provider is DeepSeek.
 * The Chat Completions endpoint does not support a web_search tool
 * (only the Responses API with deepseek-v4-pro does), so we call Search
 * ourselves and inject the results into the summary prompt.
 */
object DeepSeekSearch {

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private const val ENDPOINT = "https://api.deepseek.com/v1/tools/search"

    data class Hit(
        val title: String,
        val url: String,
        val summary: String,
        val snippet: String,
    )

    suspend fun search(apiKey: String, query: String, count: Int = 5): List<Hit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(128)
        if (q.isBlank()) return@withContext emptyList()
        val body = JSONObject()
            .put("query", q)
            .put("count", count.coerceIn(1, 10))
            .put("need_content", false)
            .toString()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val text = response.body?.string().orEmpty()
                parse(text)
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(raw: String): List<Hit> {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val arr = obj.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Hit>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            out += Hit(
                title = item.optString("title"),
                url = item.optString("url"),
                summary = item.optString("summary"),
                snippet = item.optString("snippet"),
            )
        }
        return out
    }

    fun formatForPrompt(hits: List<Hit>): String {
        if (hits.isEmpty()) return ""
        return buildString {
            appendLine("以下是 DeepSeek Search API 的搜索結果，請優先核對這些資料再撰寫摘要：")
            hits.take(5).forEachIndexed { index, hit ->
                append(index + 1).append(". ")
                if (hit.title.isNotBlank()) append(hit.title).append(" ")
                if (hit.url.isNotBlank()) append("(").append(hit.url).append(") ")
                val body = hit.summary.ifBlank { hit.snippet }
                if (body.isNotBlank()) append(body.take(400))
                appendLine()
            }
        }
    }
}
