package com.wusper.zeyao.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NewsClassifierTest {
    @Test
    fun parseYesNo_recognizesYesAndNo() {
        assertEquals(true, NewsClassifier.parseYesNo("YES"))
        assertEquals(true, NewsClassifier.parseYesNo("yes\nextra"))
        assertEquals(true, NewsClassifier.parseYesNo("是"))
        assertEquals(false, NewsClassifier.parseYesNo("NO"))
        assertEquals(false, NewsClassifier.parseYesNo("否"))
        assertEquals(false, NewsClassifier.parseYesNo("不是新聞"))
    }

    @Test
    fun parseYesNo_emptyOrUnknownIsNull() {
        assertNull(NewsClassifier.parseYesNo(""))
        assertNull(NewsClassifier.parseYesNo("   "))
        assertNull(NewsClassifier.parseYesNo("maybe"))
        assertNull(NewsClassifier.parseYesNo("I think so"))
    }
}
