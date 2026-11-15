package com.wusper.zeyao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class RecordRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "recent_records.json")
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _records = MutableStateFlow<List<SummaryRecord>>(emptyList())
    val records: StateFlow<List<SummaryRecord>> = _records.asStateFlow()

    @Volatile
    private var loaded = false
    private var persistJob: Job? = null

    init {
        scope.launch { ensureLoaded() }
    }

    suspend fun upsert(record: SummaryRecord) {
        mutex.withLock {
            ensureLoadedLocked()
            val next = listOf(record) + _records.value.filterNot { it.id == record.id }
            persistLocked(next.take(MAX), flush = true)
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

    /**
     * @param flush When false, disk writes are debounced so streaming tokens
     * do not rewrite the records file on every delta.
     */
    suspend fun update(
        id: String,
        summary: String? = null,
        title: String? = null,
        url: String? = null,
        status: RecordStatus? = null,
        flush: Boolean = true,
    ) {
        mutex.withLock {
            ensureLoadedLocked()
            val next = _records.value.map { item ->
                if (item.id != id) item else item.copy(
                    summary = summary ?: item.summary,
                    title = title ?: item.title,
                    url = url ?: item.url,
                    status = status ?: item.status,
                )
            }
            persistLocked(next, flush = flush)
        }
    }

    suspend fun get(id: String): SummaryRecord? = mutex.withLock {
        ensureLoadedLocked()
        _records.value.find { it.id == id }
    }

    private suspend fun ensureLoaded() {
        mutex.withLock { ensureLoadedLocked() }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        _records.value = withContext(Dispatchers.IO) {
            runCatching { readUnlocked() }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private fun persistLocked(list: List<SummaryRecord>, flush: Boolean) {
        _records.value = list
        persistJob?.cancel()
        if (flush) {
            persistJob = scope.launch { writeUnlocked(list) }
        } else {
            persistJob = scope.launch {
                delay(DEBOUNCE_MS)
                writeUnlocked(list)
            }
        }
    }

    private suspend fun writeUnlocked(list: List<SummaryRecord>) {
        withContext(Dispatchers.IO + NonCancellable) {
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
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(arr.toString())
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
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
        const val DEBOUNCE_MS = 400L
    }
}
