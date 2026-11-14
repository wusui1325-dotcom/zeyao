package com.wusper.zeyao.extract

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

data class FetchedArticle(
    val url: String,
    val title: String,
    val text: String,
)

object ArticleFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun fetch(url: String): FetchedArticle = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-Hant,zh-TW,zh-HK,zh,en;q=0.8")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("無法讀取文章（HTTP ${response.code}）")
            }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) {
                // Fallback: return minimal article so caller can still try title-only summary
                return@withContext FetchedArticle(url, "", "")
            }
            parse(url, html)
        }
    }

    fun parse(url: String, html: String): FetchedArticle {
        val doc = Jsoup.parse(html, url)
        doc.select("script, style, noscript, nav, footer, header, iframe, svg, form, aside").remove()
        val title = sequenceOf(
            doc.selectFirst("meta[property=og:title]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content"),
            doc.title(),
            doc.selectFirst("h1")?.text(),
        ).map { it?.trim().orEmpty() }.firstOrNull { it.isNotBlank() }.orEmpty()

        val article = doc.selectFirst("article")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst("main")
        val source = article ?: doc.body()
        val paragraphs = source?.select("p")?.map { it.text().trim() }?.filter { it.length > 20 }.orEmpty()
        val text = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n")
        } else {
            source?.text().orEmpty().trim()
        }
        return FetchedArticle(url = url, title = title, text = text.take(16_000))
    }
}
