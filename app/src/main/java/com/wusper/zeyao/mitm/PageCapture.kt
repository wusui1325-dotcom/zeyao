package com.wusper.zeyao.mitm

import com.wusper.zeyao.data.NewsHosts
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived HTML captured from an optional HTTPS decrypt session,
 * so the summary can use the page the browser actually requested
 * (including its cookies) instead of a bare GET.
 */
object PageCapture {
    private const val TTL_MS = 120_000L
    private const val MAX = 24

    private data class Entry(val html: String, val at: Long)

    private val map = ConcurrentHashMap<String, Entry>()

    fun put(url: String, html: String) {
        if (url.isBlank() || html.isBlank()) return
        evict()
        if (map.size >= MAX) {
            val oldest = map.minByOrNull { it.value.at }?.key
            if (oldest != null) map.remove(oldest)
        }
        map[NewsHosts.canonicalUrl(url)] = Entry(html, System.currentTimeMillis())
    }

    fun get(url: String): String? {
        evict()
        val key = NewsHosts.canonicalUrl(url)
        map[key]?.let { return it.html }
        for ((k, v) in map) {
            if (NewsHosts.sameArticle(k, key)) return v.html
        }
        return null
    }

    private fun evict() {
        val now = System.currentTimeMillis()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.at > TTL_MS) it.remove()
        }
    }
}
