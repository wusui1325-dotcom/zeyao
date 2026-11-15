package com.wusper.zeyao.ai

import android.os.SystemClock
import com.wusper.zeyao.data.NewsHosts
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived in-memory cache for summaries to avoid duplicate API calls.
 */
object SummaryCache {
    private const val TTL_MS = 20 * 60 * 1000L // 20 minutes
    private val cache = ConcurrentHashMap<String, Entry>()

    data class Entry(val text: String, val at: Long)

    fun get(url: String): String? {
        val key = NewsHosts.canonicalUrl(url)
        val e = cache[key] ?: return null
        if (SystemClock.elapsedRealtime() - e.at > TTL_MS) {
            cache.remove(key)
            return null
        }
        return e.text
    }

    fun put(url: String, text: String) {
        if (text.isBlank()) return
        cache[NewsHosts.canonicalUrl(url)] = Entry(text, SystemClock.elapsedRealtime())
        // Keep size bounded
        if (cache.size > 120) {
            val oldest = cache.entries.minByOrNull { it.value.at }?.key
            if (oldest != null) cache.remove(oldest)
        }
    }

    fun clear() = cache.clear()
}
