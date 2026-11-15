package com.wusper.zeyao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue(c.contains("id=1"))
    }

    @Test
    fun sameArticle_matchesSlugVariants() {
        val a = "https://news.example.com/article/2026/title"
        val b = "https://news.example.com/article/2026/title/extra"
        assertTrue(NewsHosts.sameArticle(a, b))
    }

    @Test
    fun sameArticle_doesNotMatchSiblingArticles() {
        assertFalse(
            NewsHosts.sameArticle(
                "https://example.com/news/article-1",
                "https://example.com/news/article-2",
            ),
        )
    }

    @Test
    fun skippedHost_matchesSiteAndSubdomain() {
        val skipped = setOf("bbc.com")
        assertTrue(NewsHosts.isSkippedHost("https://www.bbc.com/news/world-123", skipped))
        assertTrue(NewsHosts.isSkippedHost("https://news.bbc.com/story", skipped))
        assertFalse(NewsHosts.isSkippedHost("https://nytimes.com/2026/01/01/world", skipped))
    }

    @Test
    fun unwrapUrl_extractsGoogleRedirect() {
        val raw = "https://www.google.com/url?q=https://www.example.com/news/123&sa=U"
        assertEquals("https://www.example.com/news/123", NewsHosts.unwrapUrl(raw))
    }
}
