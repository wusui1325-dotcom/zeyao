package com.wusper.zeyao.intercept.helpers

import com.wusper.zeyao.data.NewsHosts
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlCooldownManagerTest {
    @Test
    fun sameArticle_isCooled() {
        val mgr = UrlCooldownManager(maxSize = 10)
        val url1 = "https://example.com/news/article-123"
        val url2 = "https://example.com/news/article-123?utm=1"
        mgr.mark(url1, 60_000L)
        assertTrue(mgr.isCooled(url2))
        assertTrue(NewsHosts.sameArticle(NewsHosts.canonicalUrl(url1), NewsHosts.canonicalUrl(url2)))
    }

    @Test
    fun differentArticle_notCooled() {
        val mgr = UrlCooldownManager()
        mgr.mark("https://example.com/news/a", 60_000L)
        assertFalse(mgr.isCooled("https://example.com/news/b"))
    }
}
