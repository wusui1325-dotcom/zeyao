package com.wusper.zeyao.intercept.helpers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDebouncerTest {
    @Test
    fun duplicateWithinWindow_isRejected() {
        var now = 1_000L
        val debouncer = UrlDebouncer(windowMs = 2_000L, elapsedRealtime = { now })
        val url = "https://www.bbc.com/news/world-123"
        assertTrue(debouncer.accept(url))
        now = 2_500L
        assertFalse(debouncer.accept(url))
        assertFalse(debouncer.accept("https://bbc.com/news/world-123?utm_source=x"))
    }

    @Test
    fun afterWindow_isAccepted() {
        var now = 1_000L
        val debouncer = UrlDebouncer(windowMs = 2_000L, elapsedRealtime = { now })
        assertTrue(debouncer.accept("https://example.com/a"))
        now = 3_000L
        assertTrue(debouncer.accept("https://example.com/a"))
    }

    @Test
    fun differentArticles_areIndependent() {
        var now = 1_000L
        val debouncer = UrlDebouncer(windowMs = 2_000L, elapsedRealtime = { now })
        assertTrue(debouncer.accept("https://example.com/news/a"))
        assertTrue(debouncer.accept("https://example.com/news/b"))
    }
}
