package com.wusper.zeyao.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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
    private val writeMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _records = MutableStateFlow<List<SummaryRecord>>(emptyList())
    val records: StateFlow<List<SummaryRecord>> = _records.asStateFlow()

    @Volatile
    private var loaded = false
    private var persistJob: Job? = null
    private val writeGen = AtomicInteger(0)

    init {
        scope.launch { ensureLoaded() }
    }

    suspend fun upsert(record: SummaryRecord) {
        mutex.withLock {
            ensureLoadedLocked()
            val next = listOf(record) + _records.value.filterNot { it.id == record.id }
            persistLocked(next, flush = true)
        }
    }

    suspend fun create(
        url: String,
        title: String,
        host: String,
        status: RecordStatus = RecordStatus.GENERATING,
        summary: String = "",
        id: String = UUID.randomUUID().toString(),
        pinned: Boolean = false,
    ): SummaryRecord {
        val record = SummaryRecord(
            id = id,
            url = url,
            title = title,
            host = host,
            summary = summary,
            createdAt = System.currentTimeMillis(),
            status = status,
            pinned = pinned,
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
        pinned: Boolean? = null,
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
                    pinned = pinned ?: item.pinned,
                )
            }
            persistLocked(next, flush = flush)
        }
    }

    suspend fun setPinned(id: String, pinned: Boolean) = update(id, pinned = pinned, flush = true)

    suspend fun delete(id: String): SummaryRecord? = mutex.withLock {
        ensureLoadedLocked()
        val removed = _records.value.find { it.id == id }
        if (removed != null) {
            persistLocked(_records.value.filterNot { it.id == id }, flush = true)
        }
        removed
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
            runCatching { sortRecords(readUnlocked()) }.getOrDefault(emptyList())
        }
        loaded = true
    }

    private fun persistLocked(list: List<SummaryRecord>, flush: Boolean) {
        val sorted = sortRecords(list)
        _records.value = sorted
        val gen = writeGen.incrementAndGet()
        persistJob?.cancel()
        persistJob = scope.launch {
            if (!flush) delay(DEBOUNCE_MS)
            writeIfLatest(sorted, gen)
        }
    }

    private fun sortRecords(list: List<SummaryRecord>): List<SummaryRecord> {
        val pinned = list.filter { it.pinned }.sortedByDescending { it.createdAt }
        val rest = list.filterNot { it.pinned }.sortedByDescending { it.createdAt }
        return (pinned + rest).take(MAX)
    }

    private suspend fun writeIfLatest(list: List<SummaryRecord>, gen: Int) {
        writeMutex.withLock {
            if (gen < writeGen.get()) return
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
                            .put("status", r.status.name)
                            .put("pinned", r.pinned),
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
    }

    private fun readUnlocked(): List<SummaryRecord> {
        if (!file.exists()) return emptyList()
        return parseRecordsJson(file.readText())
    }

    private companion object {
        const val MAX = 200
        const val DEBOUNCE_MS = 400L
    }
}

/** Parses the records file. Old entries without [SummaryRecord.pinned] stay unpinned. */
internal fun parseRecordsJson(text: String): List<SummaryRecord> {
    return parseObjectArray(text).mapNotNull { o ->
        val id = o.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SummaryRecord(
            id = id,
            url = o.string("url").orEmpty(),
            title = o.string("title").orEmpty(),
            host = o.string("host").orEmpty(),
            summary = o.string("summary").orEmpty(),
            createdAt = o.long("createdAt"),
            status = runCatching { RecordStatus.valueOf(o.string("status").orEmpty()) }
                .getOrDefault(RecordStatus.COMPLETED),
            pinned = o.bool("pinned"),
        )
    }
}

private data class JsonMap(private val values: Map<String, Any?>) {
    fun string(key: String): String? = values[key] as? String
    fun long(key: String): Long = when (val v = values[key]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
    fun bool(key: String): Boolean = when (val v = values[key]) {
        is Boolean -> v
        is String -> v.equals("true", ignoreCase = true)
        is Number -> v.toInt() != 0
        else -> false
    }
}

private fun parseObjectArray(text: String): List<JsonMap> {
    val s = text.trim()
    if (s.isEmpty()) return emptyList()
    return MiniJson(s).parseArray()
}

private class MiniJson(private val src: String) {
    private var i = 0

    fun parseArray(): List<JsonMap> {
        skip()
        if (!eat('[')) return emptyList()
        val out = mutableListOf<JsonMap>()
        skip()
        if (peek() == ']') {
            i++
            return out
        }
        while (i < src.length) {
            skip()
            if (peek() == ']') {
                i++
                break
            }
            val value = parseValue()
            if (value is JsonMap) out.add(value)
            skip()
            if (peek() == ',') {
                i++
                continue
            }
            if (peek() == ']') {
                i++
                break
            }
            break
        }
        return out
    }

    private fun parseValue(): Any? {
        skip()
        return when (val c = peek()) {
            '"' -> parseString()
            '{' -> parseObject()
            't' -> { expect("true"); true }
            'f' -> { expect("false"); false }
            'n' -> { expect("null"); null }
            else -> if (c == '-' || c in '0'..'9') parseNumber() else {
                i = src.length
                null
            }
        }
    }

    private fun parseObject(): JsonMap {
        eat('{')
        val map = mutableMapOf<String, Any?>()
        skip()
        if (peek() == '}') {
            i++
            return JsonMap(map)
        }
        while (i < src.length) {
            skip()
            if (peek() == '}') {
                i++
                break
            }
            if (peek() != '"') break
            val key = parseString()
            skip()
            if (!eat(':')) break
            map[key] = parseValue()
            skip()
            if (peek() == ',') {
                i++
                continue
            }
            if (peek() == '}') {
                i++
                break
            }
            break
        }
        return JsonMap(map)
    }

    private fun parseString(): String {
        if (!eat('"')) return ""
        val out = StringBuilder()
        while (i < src.length) {
            val c = src[i++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    if (i >= src.length) break
                    when (val e = src[i++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (i + 4 <= src.length) {
                                val hex = src.substring(i, i + 4)
                                i += 4
                                out.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            }
                        }
                        else -> out.append(e)
                    }
                }
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private fun parseNumber(): Number {
        val start = i
        if (peek() == '-') i++
        while (peek() in '0'..'9') i++
        var isDouble = false
        if (peek() == '.') {
            isDouble = true
            i++
            while (peek() in '0'..'9') i++
        }
        if (peek() == 'e' || peek() == 'E') {
            isDouble = true
            i++
            if (peek() == '+' || peek() == '-') i++
            while (peek() in '0'..'9') i++
        }
        val raw = src.substring(start, i)
        return if (isDouble) raw.toDoubleOrNull() ?: 0.0 else raw.toLongOrNull() ?: 0L
    }

    private fun skip() {
        while (i < src.length && src[i].isWhitespace()) i++
    }

    private fun peek(): Char = if (i < src.length) src[i] else '\u0000'

    private fun eat(c: Char): Boolean {
        skip()
        if (peek() != c) return false
        i++
        return true
    }

    private fun expect(token: String) {
        if (src.startsWith(token, i)) i += token.length else i = src.length
    }
}
