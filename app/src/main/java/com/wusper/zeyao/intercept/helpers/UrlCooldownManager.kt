package com.wusper.zeyao.intercept.helpers

import android.os.SystemClock
import com.wusper.zeyao.data.NewsHosts
import java.util.LinkedHashMap

/**
 * URL cooldown management extracted from InterceptController (item 37).
 * Uses LRU-style LinkedHashMap for bounded size.
 */
class UrlCooldownManager(
    private val maxSize: Int = 200,
) {
    private val urlCooldownUntil = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Long>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
                return size > maxSize
            }
        }
    )

    fun isCooled(url: String, suppressUntil: Long = 0L): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressUntil) return true
        val key = NewsHosts.canonicalUrl(url)
        val exact = urlCooldownUntil[key]
        if (exact != null && now < exact) return true
        for ((cooled, until) in urlCooldownUntil) {
            if (now < until && NewsHosts.sameArticle(cooled, key)) return true
        }
        return false
    }

    fun mark(url: String, durationMs: Long) {
        val key = NewsHosts.canonicalUrl(url)
        urlCooldownUntil[key] = SystemClock.elapsedRealtime() + durationMs
    }

    fun clear() = urlCooldownUntil.clear()
}
