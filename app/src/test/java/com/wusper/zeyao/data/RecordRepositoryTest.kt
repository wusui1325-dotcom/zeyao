package com.wusper.zeyao.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordRepositoryTest {
    @Test
    fun parseRecordsJson_readsLegacyRecordsWithoutPinned() {
        val json = """
            [
              {
                "id": "a1",
                "url": "https://example.com/a",
                "title": "舊紀錄",
                "host": "example.com",
                "summary": "摘要",
                "createdAt": 1,
                "status": "COMPLETED"
              },
              {
                "id": "a2",
                "url": "https://example.com/b",
                "title": "失敗",
                "host": "example.com",
                "summary": "",
                "createdAt": 2,
                "status": "FAILED"
              }
            ]
        """.trimIndent()
        val records = parseRecordsJson(json)
        assertEquals(2, records.size)
        assertEquals("a1", records[0].id)
        assertFalse(records[0].pinned)
        assertEquals(RecordStatus.COMPLETED, records[0].status)
        assertEquals(RecordStatus.FAILED, records[1].status)
        assertFalse(records[1].pinned)
    }

    @Test
    fun parseRecordsJson_readsPinnedAndUnknownStatus() {
        val json = """
            [
              {
                "id": "p1",
                "url": "https://news.example/x",
                "title": "釘選",
                "host": "news.example",
                "summary": "ok",
                "createdAt": 9,
                "status": "NEW_STATUS",
                "pinned": true
              },
              {"not": "an object"}
            ]
        """.trimIndent()
        val records = parseRecordsJson(json)
        assertEquals(1, records.size)
        assertTrue(records[0].pinned)
        assertEquals(RecordStatus.COMPLETED, records[0].status)
    }

    @Test
    fun parseRecordsJson_skipsEntriesWithoutId() {
        val records = parseRecordsJson("""[{"url":"https://x.example"}]""")
        assertTrue(records.isEmpty())
    }
}
