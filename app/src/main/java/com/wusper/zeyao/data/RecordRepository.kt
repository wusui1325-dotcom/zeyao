package com.wusper.zeyao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RecordRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "recent_records.json")
    private val mutex = Mutex()
    private val _records = MutableStateFlow<List<SummaryRecord>>(emptyList())
    val records: StateFlow<List<SummaryRecord>> = _records.asStateFlow()

    init {
        _records.value = runCatching { readUnlocked() }.getOrDefault(emptyList())
    }

    suspend fun upsert(record: SummaryRecord) {
        mutex.withLock {
            val next = listOf(record) + _records.value.filterNot { it.id == record.id }
            persist(next.take(MAX))
        }
    }

    suspend fun create(
        url: String,
        title: String,
        host: String,
        status: RecordStatus = RecordStatus.GENERATING,
        summary: String = "",
        id: String = UUID.randomUUID().toString(),
    ): SummaryRecord {
        val record = SummaryRecord(
            id = id,
            url = url,
            title = title,
            host = host,
            summary = summary,
            createdAt = System.currentTimeMillis(),
            status = status,
        )
        upsert(record)
        return record
    }

    suspend fun update(
        id: String,
        summary: String? = null,
        title: String? = null,
        url: String? = null,
        status: RecordStatus? = null,
    ) {
        mutex.withLock {
            val next = _records.value.map { item ->
                if (item.id != id) item else item.copy(
                    summary = summary ?: item.summary,
                    title = title ?: item.title,
                    url = url ?: item.url,
                    status = status ?: item.status,
                )
            }
            persist(next)
        }
    }

    suspend fun get(id: String): SummaryRecord? = mutex.withLock {
        _records.value.find { it.id == id }
    }

    private suspend fun persist(list: List<SummaryRecord>) {
        _records.value = list
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            list.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("id", r.id)
                        .put("url", r.url)
                        .put("title", r.title)
                        .put("host", r.host)
                        .put("summary", r.summary)
                        .put("createdAt", r.createdAt)
                        .put("status", r.status.name),
                )
            }
            file.writeText(arr.toString())
        }
    }

    private fun readUnlocked(): List<SummaryRecord> {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    SummaryRecord(
                        id = o.getString("id"),
                        url = o.optString("url"),
                        title = o.optString("title"),
                        host = o.optString("host"),
                        summary = o.optString("summary"),
                        createdAt = o.optLong("createdAt"),
                        status = runCatching { RecordStatus.valueOf(o.optString("status")) }
                            .getOrDefault(RecordStatus.COMPLETED),
                    ),
                )
            }
        }
    }

    private companion object {
        const val MAX = 200
    }
}
