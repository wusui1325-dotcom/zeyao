package com.wusper.zeyao.extract

import com.wusper.zeyao.ai.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class FetchedArticle(
    val url: String,
    val title: String,
    val text: String,
)

object ArticleFetcher {
    private const val MAX_HTML_BYTES = 1024 * 1024 // 1 MB cap to avoid OOM / slowness on huge pages

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
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("無法讀取文章（HTTP ${response.code}）")
            }
            val body = response.body ?: return@withContext FetchedArticle(url, "", "")
            val mediaType = body.contentType()
            if (mediaType != null && !isTextual(mediaType.type, mediaType.subtype)) {
                return@withContext FetchedArticle(url, "", "")
            }
            val html = readCappedString(body)
            if (html.isBlank()) {
                return@withContext FetchedArticle(url, "", "")
            }
            parse(url, html)
        }
    }

    private fun isTextual(type: String, subtype: String): Boolean {
        if (type.equals("text", ignoreCase = true)) return true
        val sub = subtype.lowercase()
        return sub.contains("html") || sub.contains("xml") || sub.contains("json")
    }

    private fun readCappedString(body: okhttp3.ResponseBody): String {
        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        body.byteStream().use { input ->
            var remaining = MAX_HTML_BYTES
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size, remaining))
                if (n <= 0) break
                out.write(buf, 0, n)
                remaining -= n
            }
        }
        return String(out.toByteArray(), charset)
    }

    fun parse(url: String, html: String): FetchedArticle {
        val doc = Jsoup.parse(html, url)
        val metaDescription = sequenceOf(
            doc.selectFirst("meta[property=og:description]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:description]")?.attr("content"),
            doc.selectFirst("meta[name=description]")?.attr("content"),
        ).map { it?.trim().orEmpty() }.firstOrNull { it.isNotBlank() }.orEmpty()

        val title = sequenceOf(
            doc.selectFirst("meta[property=og:title]")?.attr("content"),
            doc.selectFirst("meta[name=twitter:title]")?.attr("content"),
            doc.title(),
            doc.selectFirst("h1")?.text(),
        ).map { it?.trim().orEmpty() }.firstOrNull { it.isNotBlank() }.orEmpty()

        doc.select("script, style, noscript, nav, footer, header, iframe, svg, form, aside").remove()

        val article = doc.selectFirst("article")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst("main")
        val source = article ?: doc.body()
        val paragraphs = source?.select("p")?.map { it.text().trim() }?.filter { it.length > 20 }.orEmpty()
        val rawText = if (paragraphs.isNotEmpty()) {
            paragraphs.joinToString("\n")
        } else {
            source?.text().orEmpty().trim()
        }
        val text = rawText.ifBlank { metaDescription }
        return FetchedArticle(url = url, title = title, text = text.take(16_000))
    }
}
