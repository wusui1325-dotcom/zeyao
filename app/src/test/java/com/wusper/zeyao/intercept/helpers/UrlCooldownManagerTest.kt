package com.wusper.zeyao.intercept.helpers

import com.wusper.zeyao.data.NewsHosts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCooldownManagerTest {
    @Test
    fun sameArticle_isCooled() {
        var now = 1_000L
        val mgr = UrlCooldownManager(maxSize = 10, elapsedRealtime = { now })
        val url1 = "https://example.com/news/article-123"
        val url2 = "https://example.com/news/article-123?utm=1"
        mgr.mark(url1, 60_000L)
        assertTrue(mgr.isCooled(url2))
        assertTrue(NewsHosts.sameArticle(NewsHosts.canonicalUrl(url1), NewsHosts.canonicalUrl(url2)))
        now = 70_000L
        assertFalse(mgr.isCooled(url2))
    }

    @Test
    fun differentArticle_notCooled() {
        var now = 1_000L
        val mgr = UrlCooldownManager(elapsedRealtime = { now })
        mgr.mark("https://example.com/news/a", 60_000L)
        assertFalse(mgr.isCooled("https://example.com/news/b"))
    }
}
