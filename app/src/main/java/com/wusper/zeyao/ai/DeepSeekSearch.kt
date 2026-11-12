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
    private const val CHAT = "https://api.deepseek.com/chat/completions"

    data class Hit(
        val title: String,
        val url: String,
        val summary: String,
        val snippet: String,
    )

    /**
     * Use the cheapest model to extract a short keyword query from the title + body,
     * then search with that query instead of the raw title.
     */
    suspend fun searchForArticle(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
    ): List<Hit> = withContext(Dispatchers.IO) {
        val keywords = extractKeywords(apiKey, title, url, articleText)
        val query = keywords.ifBlank { title.ifBlank { url } }.take(128)
        search(apiKey, query, count = 5)
    }

    private fun extractKeywords(apiKey: String, title: String, url: String, articleText: String): String {
        val bodySnippet = articleText.trim().take(2000)
        val prompt = buildString {
            appendLine("從以下新聞抽出 3～6 個最能代表這則新聞的關鍵詞或短語，用空格分隔，不要解釋，不要標點。")
            appendLine("標題：$title")
            appendLine("網址：$url")
            if (bodySnippet.isNotBlank()) {
                appendLine("正文片段：")
                append(bodySnippet)
            }
        }
        val body = JSONObject()
            .put("model", "deepseek-flash")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .put("max_tokens", 60)
            .put("temperature", 0.3)
            .put("stream", false)
            .toString()
        val request = Request.Builder()
            .url(CHAT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val text = response.body?.string().orEmpty()
                val obj = JSONObject(text)
                val choices = obj.optJSONArray("choices") ?: return@use ""
                if (choices.length() == 0) return@use ""
                val content = choices.getJSONObject(0)
                    .optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                content.trim().lines().firstOrNull().orEmpty().take(128)
            }
        }.getOrDefault("")
    }

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
            appendLine("以下是搜索結果，請優先核對這些資料再撰寫摘要：")
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

    suspend fun searchWithFallback(apiKey: String, query: String, fallback: Boolean, count: Int = 5): List<Hit> {
        val official = search(apiKey, query, count)
        if (official.isNotEmpty() || !fallback) return official
        return duckDuckGo(query, count)
    }

    private suspend fun duckDuckGo(query: String, count: Int): List<Hit> = withContext(Dispatchers.IO) {
        val q = query.trim().take(128)
        if (q.isBlank()) return@withContext emptyList()
        val encoded = java.net.URLEncoder.encode(q, "UTF-8")
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/?q=$encoded")
            .header("User-Agent", "Mozilla/5.0")
            .get()
            .build()
        runCatching {
            HttpClients.init.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parseDuck(response.body?.string().orEmpty(), count)
            }
        }.getOrDefault(emptyList())
    }

    private fun parseDuck(html: String, count: Int): List<Hit> {
        val out = mutableListOf<Hit>()
        val link = Regex("""<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>""", RegexOption.IGNORE_CASE)
        val snippet = Regex("""class="result__snippet"[^>]*>(.*?)</""", RegexOption.IGNORE_CASE)
        val links = link.findAll(html).toList()
        val snippets = snippet.findAll(html).map { clean(it.groupValues[1]) }.toList()
        links.take(count).forEachIndexed { i, m ->
            val url = decodeDuck(m.groupValues[1])
            val title = clean(m.groupValues[2])
            if (url.startsWith("http")) out += Hit(title, url, snippets.getOrElse(i) { "" }, "")
        }
        return out
    }

    private fun decodeDuck(href: String): String {
        val uddg = Regex("""uddg=([^&]+)""").find(href)?.groupValues?.getOrNull(1)
        return if (uddg != null) java.net.URLDecoder.decode(uddg, "UTF-8") else href
    }

    private fun clean(raw: String): String =
        raw.replace(Regex("<[^>]+>"), "").replace("&", "&").trim()
}
