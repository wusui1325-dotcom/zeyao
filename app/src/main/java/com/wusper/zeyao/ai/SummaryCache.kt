package com.wusper.zeyao.ai

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Short-lived in-memory cache for summaries to avoid duplicate API calls.
 */
object SummaryCache {
    private const val TTL_MS = 15 * 60 * 1000L // 15 minutes
    private val cache = ConcurrentHashMap<String, Entry>()

    data class Entry(val text: String, val at: Long)

    fun get(url: String): String? {
        val key = url.trim().lowercase()
        val e = cache[key] ?: return null
        if (SystemClock.elapsedRealtime() - e.at > TTL_MS) {
            cache.remove(key)
            return null
        }
        return e.text
    }

    fun put(url: String, text: String) {
        if (text.isBlank()) return
        cache[url.trim().lowercase()] = Entry(text, SystemClock.elapsedRealtime())
        // Keep size bounded
        if (cache.size > 120) {
            val oldest = cache.entries.minByOrNull { it.value.at }?.key
            if (oldest != null) cache.remove(oldest)
        }
    }

    fun clear() = cache.clear()
}
