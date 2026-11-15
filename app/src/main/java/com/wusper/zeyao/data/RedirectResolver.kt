package com.wusper.zeyao.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Follow 301/302/303/307/308 redirects and resolve short links to the
 * final article URL. Network follow is only used for known shorteners;
 * ordinary article URLs return immediately after a local unwrap.
 */
object RedirectResolver {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val SHORTENERS = setOf(
        "share.google",
        "t.co",
        "bit.ly",
        "bitly.com",
        "tinyurl.com",
        "goo.gl",
        "ow.ly",
        "buff.ly",
        "lnkd.in",
        "reut.rs",
        "nyti.ms",
        "bbc.in",
        "cnn.it",
        "on.ft.com",
        "trib.al",
        "ift.tt",
        "dlvr.it",
        "v.gd",
        "is.gd",
        "cutt.ly",
        "shorturl.at",
        "tiny.cc",
        "rb.gy",
    )

    fun needsNetworkResolve(url: String): Boolean {
        val host = NewsHosts.hostOf(url)
            .lowercase()
            .trim()
            .trimEnd('.')
        if (host.isBlank()) return false
        return SHORTENERS.any { host == it || host.endsWith(".$it") }
    }

    suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
        var current = NewsHosts.unwrapUrl(url)
        if (!needsNetworkResolve(current)) return@withContext current
        repeat(6) {
            val next = followOne(current) ?: return@withContext current
            if (next == current) return@withContext current
            current = NewsHosts.unwrapUrl(next)
            if (!needsNetworkResolve(current) && !looksLikeRedirectOnly(current)) {
                return@withContext current
            }
        }
        current
    }

    private fun looksLikeRedirectOnly(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val path = uri.path.orEmpty().trimEnd('/')
        return path.isEmpty() || path == "/" || needsNetworkResolve(url)
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
