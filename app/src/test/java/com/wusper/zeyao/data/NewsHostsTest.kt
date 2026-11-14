package com.wusper.zeyao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsHostsTest {
    @Test
    fun canonicalUrl_stripsTrackingAndWww() {
        val raw = "https://www.example.com/news/123?utm_source=twitter&fbclid=abc&id=1"
        val c = NewsHosts.canonicalUrl(raw)
        assertTrue(c.startsWith("https://example.com/news/123"))
        assertTrue(!c.contains("utm_source"))
        assertTrue(!c.contains("fbclid"))
    }

    @Test
    fun sameArticle_matchesSlugVariants() {
        val a = "https://news.example.com/article/2026/title"
        val b = "https://news.example.com/article/2026/title/extra"
        assertTrue(NewsHosts.sameArticle(a, b))
    }
}
