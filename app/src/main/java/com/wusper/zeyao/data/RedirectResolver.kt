package com.wusper.zeyao.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Follow 301/302/303/307/308 redirects and resolve share.google short links
 * to the final article URL. Used before intercept so the real page is summarized.
 */
object RedirectResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        var current = NewsHosts.unwrapUrl(url)
        repeat(6) {
            val next = followOne(current) ?: return@withContext current
            if (next == current) return@withContext current
            current = next
        }
        current
    }

    private fun followOne(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .head()
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val code = response.code
                if (code !in 300..399) return@use null
                val location = response.header("Location") ?: return@use null
                val absolute = if (location.startsWith("http")) {
                    location
                } else {
                    val base = response.request.url
                    base.resolve(location)?.toString() ?: return@use null
                }
                NewsHosts.unwrapUrl(absolute)
            }
        }.getOrNull()
    }
}
