package com.wusper.zeyao.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectResolverTest {
    @Test
    fun ordinaryArticleUrls_doNotNeedNetworkResolve() {
        val urls = listOf(
            "https://www.bbc.com/news/world-123",
            "https://nytimes.com/2026/01/01/world/foo.html",
            "https://www.theguardian.com/world/2024/jan/01/story",
            "https://news.mingpao.com/pns/daily-news/article",
            "https://example.com/path?q=1",
        )
        for (url in urls) {
            assertFalse("HEAD should not run for $url", RedirectResolver.needsNetworkResolve(url))
            assertFalse(
                "unwrapped ordinary URL should not need HEAD",
                RedirectResolver.needsNetworkResolve(NewsHosts.unwrapUrl(url)),
            )
        }
    }

    @Test
    fun knownShorteners_needNetworkResolve() {
        val urls = listOf(
            "https://bit.ly/abc123",
            "https://t.co/xyz",
            "https://nyti.ms/abc",
            "https://bbc.in/2abc",
            "http://tinyurl.com/abcd",
        )
        for (url in urls) {
            assertTrue("shortener should resolve $url", RedirectResolver.needsNetworkResolve(url))
        }
    }
}
