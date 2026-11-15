package com.wusper.zeyao.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsClassifierCacheTest {
    @Test
    fun remember_isReadableAndNotForBlankUnknown() {
        val url = "https://cache-test.example/article-classify-1"
        NewsClassifier.remember(url, true)
        assertEquals(true, NewsClassifier.cached(url))
        NewsClassifier.remember(url, false)
        assertEquals(false, NewsClassifier.cached(url))
        assertNull(NewsClassifier.cached("https://cache-test.example/never-seen"))
    }
}
