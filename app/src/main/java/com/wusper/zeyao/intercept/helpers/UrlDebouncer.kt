package com.wusper.zeyao.intercept.helpers

import android.os.SystemClock
import com.wusper.zeyao.data.NewsHosts
import java.util.concurrent.ConcurrentHashMap

/**
 * Drops duplicate intercepts of the same article within a short window
 * so accessibility bursts do not start a second classify or float.
 */
class UrlDebouncer(
    private val windowMs: Long = 2_000L,
    private val maxSize: Int = 128,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val lastSeenAt = ConcurrentHashMap<String, Long>()

    /**
     * @return true if this URL should proceed (not a duplicate in the window).
     */
    fun accept(url: String): Boolean {
        val key = NewsHosts.canonicalUrl(url)
        val now = elapsedRealtime()
        prune(now)
        val last = lastSeenAt.put(key, now)
        return last == null || now - last >= windowMs
    }

    fun clear() = lastSeenAt.clear()

    private fun prune(now: Long) {
        if (lastSeenAt.size <= maxSize) return
        lastSeenAt.entries.removeIf { now - it.value > windowMs * 8 }
    }
}
