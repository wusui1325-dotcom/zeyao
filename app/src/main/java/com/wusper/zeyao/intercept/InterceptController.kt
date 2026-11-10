package com.wusper.zeyao.intercept

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.wusper.zeyao.InterceptActivity
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.ai.AiClientFactory
import com.wusper.zeyao.ai.AiInitException
import com.wusper.zeyao.ai.GenerationParams
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.extract.ArticleFetcher
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InterceptSession(
    val id: String,
    val url: String,
    val title: String,
    val host: String,
    val sourcePackage: String?,
    val summary: String = "",
    val error: String? = null,
    val streaming: Boolean = true,
    val decided: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Coordinates intercept UI, summary generation, URL cooldown, and VPN holds.
 * Generation runs in the application scope so 「退出後繼續生成」 can finish after the page closes.
 */
object InterceptController {
    private const val COOLDOWN_MS = 3 * 60 * 1000L
    private const val HOST_PASSTHROUGH_MS = 45_000L
    private const val DUP_WINDOW_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: ZeyaoApp

    private val _sessions = MutableStateFlow<Map<String, InterceptSession>>(emptyMap())
    val sessions: StateFlow<Map<String, InterceptSession>> = _sessions.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val urlCooldownUntil = ConcurrentHashMap<String, Long>()
    private val hostPassthroughUntil = ConcurrentHashMap<String, Long>()
    private val lastSeenUrlAt = ConcurrentHashMap<String, Long>()
    private val holds = ConcurrentHashMap<String, MutableList<HeldConnection>>()

    @Volatile
    var accessibility: ZeyaoAccessibilityService? = null

    @Volatile
    var interceptArmedCached: Boolean = false
        private set

    fun attach(application: ZeyaoApp) {
        app = application
        scope.launch {
            app.settings.settings.collect { settings ->
                interceptArmedCached = app.settings.isInterceptArmed(settings)
            }
        }
    }

    fun sessionFlow(id: String): StateFlow<InterceptSession?> {
        val flow = MutableStateFlow(_sessions.value[id])
        scope.launch {
            sessions.collect { flow.value = it[id] }
        }
        return flow
    }

    fun peek(id: String): InterceptSession? = _sessions.value[id]

    fun isUrlCooled(url: String): Boolean {
        val until = urlCooldownUntil[normalizeUrl(url)] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    fun isHostPassthrough(host: String): Boolean {
        val until = hostPassthroughUntil[host.lowercase()] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    suspend fun shouldIntercept(url: String, packageName: String?): Boolean {
        if (!NewsHosts.shouldInterceptUrl(url)) return false
        if (isUrlCooled(url)) return false
        val settings = app.settings.current()
        if (!app.settings.isInterceptArmed(settings)) return false
        if (!app.settings.isAppTargeted(settings, packageName)) return false
        return true
    }

    /**
     * Called from the accessibility service (full URL) or VPN (host-only HTTPS URL).
     */
    fun onUrlDetected(url: String, title: String? = null, packageName: String? = null) {
        if (!::app.isInitialized) return
        val clean = sanitizeUrl(url) ?: return
        scope.launch {
            if (!shouldIntercept(clean, packageName)) return@launch
            val now = SystemClock.elapsedRealtime()
            val last = lastSeenUrlAt.put(normalizeUrl(clean), now)
            if (last != null && now - last < DUP_WINDOW_MS) {
                // Still allow refining a host-only session with a full path.
                val existing = findOpenSessionFor(clean)
                if (existing == null || existing.url == clean) return@launch
            }
            startOrRefine(clean, title.orEmpty(), packageName)
        }
    }

    fun onHostDetected(host: String, packageName: String? = null) {
        if (host.isBlank()) return
        if (isHostPassthrough(host)) return
        val url = "https://$host/"
        onUrlDetected(url, title = host, packageName = packageName)
    }

    fun registerHold(host: String, connection: HeldConnection) {
        val key = host.lowercase()
        if (isHostPassthrough(key)) {
            connection.release()
            return
        }
        holds.getOrPut(key) { mutableListOf() }.add(connection)
        // If no session is showing yet, open one from SNI so the overlay appears first.
        val hasOpen = _sessions.value.values.any { it.host.equals(host, true) && !it.decided }
        if (!hasOpen) {
            onHostDetected(host)
        }
    }

    fun startOrRefine(url: String, title: String, packageName: String?) {
        val host = NewsHosts.hostOf(url).ifBlank { Uri.parse(url).host.orEmpty() }
        val open = findOpenSessionFor(url)
        if (open != null) {
            val refined = shouldReplaceUrl(open.url, url)
            if (refined || (title.isNotBlank() && open.title.isBlank())) {
                val nextUrl = if (refined) url else open.url
                val nextTitle = title.ifBlank { open.title }
                updateSession(open.id) { it.copy(url = nextUrl, title = nextTitle, host = host.ifBlank { it.host }) }
                if (refined && isBareHostUrl(open.url) && !isBareHostUrl(url)) {
                    // Restart generation with the real article URL if we only had a host.
                    jobs.remove(open.id)?.cancel()
                    beginGeneration(open.id, nextUrl, nextTitle, host)
                }
            }
            launchActivity(open.id, if (refined) url else open.url, title.ifBlank { open.title }, packageName)
            return
        }
        val id = UUID.randomUUID().toString()
        val session = InterceptSession(
            id = id,
            url = url,
            title = title.ifBlank { host },
            host = host,
            sourcePackage = packageName,
        )
        _sessions.update { it + (id to session) }
        scope.launch {
            app.records.create(
                url = url,
                title = session.title,
                host = host,
                status = RecordStatus.GENERATING,
                id = id,
            )
        }
        launchActivity(id, url, session.title, packageName)
        beginGeneration(id, url, session.title, host)
    }

    fun onEnter(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(decided = true) }
        urlCooldownUntil[normalizeUrl(session.url)] = SystemClock.elapsedRealtime() + COOLDOWN_MS
        hostPassthroughUntil[session.host.lowercase()] = SystemClock.elapsedRealtime() + HOST_PASSTHROUGH_MS
        releaseHolds(session.host)
        maybeCancelOnExit(sessionId)
    }

    fun onBack(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(decided = true) }
        rejectHolds(session.host)
        maybeCancelOnExit(sessionId)
        scope.launch(Dispatchers.Main) {
            delay(280)
            accessibility?.tryGoBack()
        }
    }

    fun maybeCancelOnExit(sessionId: String) {
        scope.launch {
            val continueOnExit = app.settings.current().continueOnExit
            if (!continueOnExit) {
                jobs.remove(sessionId)?.cancel()
                val current = _sessions.value[sessionId]
                if (current != null && current.streaming) {
                    updateSession(sessionId) { it.copy(streaming = false) }
                    app.records.update(sessionId, status = RecordStatus.CANCELLED, summary = current.summary)
                }
            }
        }
    }

    private fun beginGeneration(id: String, url: String, title: String, host: String) {
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            // If we only have a bare host, wait briefly for accessibility to supply the path.
            var workingUrl = url
            var workingTitle = title
            if (isBareHostUrl(url)) {
                val deadline = SystemClock.elapsedRealtime() + 800
                while (SystemClock.elapsedRealtime() < deadline) {
                    val latest = _sessions.value[id] ?: break
                    if (!isBareHostUrl(latest.url)) {
                        workingUrl = latest.url
                        workingTitle = latest.title.ifBlank { workingTitle }
                        break
                    }
                    delay(50)
                }
            }
            val fetched = runCatching { ArticleFetcher.fetch(workingUrl) }
            val article = fetched.getOrNull()
            if (article != null) {
                workingTitle = article.title.ifBlank { workingTitle }
                updateSession(id) { it.copy(title = workingTitle, url = workingUrl) }
                app.records.update(id, title = workingTitle, url = workingUrl)
            }
            val settings = app.settings.current()
            val key = app.secrets.getApiKey(settings.providerId)
            if (key.isNullOrBlank()) {
                fail(id, "尚未設定 API Key，仍可進入或返回")
                return@launch
            }
            if (article == null && fetched.exceptionOrNull() != null && isBareHostUrl(workingUrl)) {
                // Still try a brief based on host/title so the page isn't empty forever.
            }
            if (article == null && fetched.exceptionOrNull() != null) {
                // Continue with whatever we have; the model may still produce a short note.
            }
            try {
                val client = AiClientFactory.create(settings.providerId)
                client.streamSummary(
                    apiKey = key,
                    title = workingTitle,
                    url = workingUrl,
                    articleText = article?.text.orEmpty(),
                    params = GenerationParams.DEFAULT,
                ).collect { delta ->
                    if (delta.isEmpty()) return@collect
                    var snapshot: InterceptSession? = null
                    _sessions.update { map ->
                        val cur = map[id] ?: return@update map
                        val next = cur.copy(summary = cur.summary + delta, error = null)
                        snapshot = next
                        map + (id to next)
                    }
                    snapshot?.let { app.records.update(id, summary = it.summary, title = it.title) }
                }
                val final = _sessions.value[id]
                if (final == null) return@launch
                val empty = final.summary.isBlank()
                if (empty) {
                    fail(id, "摘要失敗，仍可進入或返回")
                } else {
                    updateSession(id) { it.copy(streaming = false, error = null) }
                    app.records.update(id, summary = final.summary, status = RecordStatus.COMPLETED)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = when (e) {
                    is AiInitException -> e.message ?: "摘要失敗，仍可進入或返回"
                    else -> "摘要失敗，仍可進入或返回"
                }
                fail(id, message)
            }
        }
    }

    private suspend fun fail(id: String, message: String) {
        updateSession(id) { it.copy(streaming = false, error = message) }
        val summary = _sessions.value[id]?.summary.orEmpty()
        withContext(Dispatchers.Default) {
            app.records.update(id, summary = summary, status = RecordStatus.FAILED)
        }
    }

    private fun launchActivity(id: String, url: String, title: String, packageName: String?) {
        val context: Context = app
        val intent = Intent(context, InterceptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(InterceptActivity.EXTRA_SESSION_ID, id)
            putExtra(InterceptActivity.EXTRA_URL, url)
            putExtra(InterceptActivity.EXTRA_TITLE, title)
            putExtra(InterceptActivity.EXTRA_PACKAGE, packageName)
        }
        scope.launch(Dispatchers.Main) {
            runCatching { context.startActivity(intent) }
        }
    }

    private fun findOpenSessionFor(url: String): InterceptSession? {
        val host = NewsHosts.hostOf(url)
        return _sessions.value.values
            .filter { !it.decided }
            .firstOrNull { existing ->
                normalizeUrl(existing.url) == normalizeUrl(url) ||
                    (existing.host.equals(host, true) && (isBareHostUrl(existing.url) || isBareHostUrl(url)))
            }
    }

    private fun shouldReplaceUrl(current: String, incoming: String): Boolean {
        if (current == incoming) return false
        if (isBareHostUrl(current) && !isBareHostUrl(incoming)) return true
        return false
    }

    private fun isBareHostUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        val path = uri.path.orEmpty().trimEnd('/')
        return path.isEmpty() && uri.query.isNullOrBlank()
    }

    private fun updateSession(id: String, transform: (InterceptSession) -> InterceptSession) {
        _sessions.update { map ->
            val cur = map[id] ?: return@update map
            map + (id to transform(cur))
        }
    }

    private fun releaseHolds(host: String) {
        holds.remove(host.lowercase())?.forEach { runCatching { it.release() } }
    }

    private fun rejectHolds(host: String) {
        holds.remove(host.lowercase())?.forEach { runCatching { it.reject() } }
    }

    private fun sanitizeUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.startsWith("about:") || trimmed.startsWith("chrome") || trimmed.startsWith("data:") -> return null
            trimmed.contains(" ") -> return null
            else -> "https://$trimmed"
        }
        val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return uri.buildUpon().fragment(null).build().toString()
    }

    private fun normalizeUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url.lowercase()
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().ifEmpty { "/" }
        val query = uri.query
        return buildString {
            append(uri.scheme?.lowercase() ?: "https")
            append("://")
            append(host)
            append(path)
            if (!query.isNullOrBlank()) {
                append('?')
                append(query)
            }
        }
    }
}

interface HeldConnection {
    fun release()
    fun reject()
}
