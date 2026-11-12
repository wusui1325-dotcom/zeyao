package com.wusper.zeyao.intercept

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import com.wusper.zeyao.InterceptActivity
import com.wusper.zeyao.VerifyActivity
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.ai.AiClientFactory
import com.wusper.zeyao.ai.AiInitException
import com.wusper.zeyao.ai.GenerationParams
import com.wusper.zeyao.ai.NewsClassifier
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.extract.ArticleFetcher
import com.wusper.zeyao.mitm.LocalCa
import com.wusper.zeyao.mitm.PageCapture
import java.util.concurrent.atomic.AtomicBoolean
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
    private const val DUP_WINDOW_MS = 2_000L
    private const val ENTER_SUPPRESS_MS = 12_000L
    private const val DEFAULT_SITE_COOLDOWN_MS = 10 * 60 * 1000L

    @Volatile
    private var siteCooldownMs: Long = DEFAULT_SITE_COOLDOWN_MS

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
    private var suppressUntilElapsed: Long = 0L

    /**
     * True while the 擇要 main/settings UI is in front. Used so opening the
     * app, or looking at recent records, never starts a new intercept.
     */
    @Volatile
    var appUiVisible: Boolean = false

    @Volatile
    var accessibility: ZeyaoAccessibilityService? = null

    /** Armed by the floating overlay for the next link click. */
    @Volatile
    var clickArmed: Boolean = false

    /** Whether to attempt to consume the original click after intercept. */
    @Volatile
    var clickConsume: Boolean = false

    @Volatile
    var interceptArmedCached: Boolean = false
        private set

    @Volatile
    var httpsDecryptCached: Boolean = false
        private set

    @Volatile
    var caInstalledCached: Boolean = false
        private set

    fun attach(application: ZeyaoApp) {
        app = application
        runCatching { LocalCa.ensure(application) }
        refreshCaInstalled()
        scope.launch {
            app.settings.settings.collect { settings ->
                interceptArmedCached = app.settings.isInterceptArmed(settings)
                httpsDecryptCached = settings.httpsDecryptEnabled
                siteCooldownMs = settings.siteCooldownSeconds.coerceIn(10, 600).toLong() * 1000L
                refreshCaInstalled()
            }
        }
    }

    fun refreshCaInstalled() {
        if (!::app.isInitialized) return
        caInstalledCached = runCatching {
            LocalCa.ensure(app)
            LocalCa.isInstalled(app)
        }.getOrDefault(false)
    }

    fun isHttpsDecryptActive(): Boolean = httpsDecryptCached && caInstalledCached

    fun sessionFlow(id: String): StateFlow<InterceptSession?> {
        val flow = MutableStateFlow(_sessions.value[id])
        scope.launch {
            sessions.collect { flow.value = it[id] }
        }
        return flow
    }

    fun peek(id: String): InterceptSession? = _sessions.value[id]

    fun isUrlCooled(url: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < suppressUntilElapsed) return true
        val key = NewsHosts.canonicalUrl(url)
        val exact = urlCooldownUntil[key]
        if (exact != null && now < exact) return true
        // Also treat a just-allowed article as cooled if the new URL is a
        // redirect/slug extension of a cooled one (or vice versa).
        for ((cooled, until) in urlCooldownUntil) {
            if (now < until && NewsHosts.sameArticle(cooled, key)) return true
        }
        return false
    }

    private fun markAllowed(url: String) {
        val now = SystemClock.elapsedRealtime()
        urlCooldownUntil[NewsHosts.canonicalUrl(url)] = now + siteCooldownMs
        suppressUntilElapsed = now + ENTER_SUPPRESS_MS
    }

    fun isHostPassthrough(host: String): Boolean {
        val until = hostPassthroughUntil[host.lowercase()] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    suspend fun shouldIntercept(url: String, packageName: String?): Boolean {
        if (appUiVisible) return false
        if (!NewsHosts.isClassifiableUrl(url)) return false
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
        beginVerify(clean, title.orEmpty(), packageName, startFrom = null, handoffIfNotNews = false)
    }

    fun markNotNews(url: String) {
        if (url.isBlank()) return
        val key = NewsHosts.canonicalUrl(url)
        NewsClassifier.remember(key, false)
        urlCooldownUntil[key] = SystemClock.elapsedRealtime() + siteCooldownMs
        releaseDocumentHolds(url)
    }

    fun releaseDocumentHolds(url: String) {
        if (url.isBlank()) return
        val host = NewsHosts.hostOf(url)
        if (host.isNotBlank()) releaseHolds(host)
        releaseHolds("url:" + NewsHosts.canonicalUrl(url))
    }

    fun rejectDocumentHolds(url: String) {
        if (url.isBlank()) return
        val host = NewsHosts.hostOf(url)
        if (host.isNotBlank()) rejectHolds(host)
        rejectHolds("url:" + NewsHosts.canonicalUrl(url))
    }

    /**
     * Exact URL recovered from optional HTTPS decryption. Hold the origin
     * response until verify / 進入 / 返回 decides.
     */
    fun onDecryptedDocument(url: String, connection: HeldConnection): Boolean {
        val wrapped = IdempotentHold(connection)
        val clean = sanitizeUrl(url)
        if (clean == null || appUiVisible || isUrlCooled(clean)) {
            wrapped.release()
            return false
        }
        val host = NewsHosts.hostOf(clean).lowercase()
        if (host.isNotBlank() && isHostPassthrough(host)) {
            wrapped.release()
            return false
        }
        if (host.isNotBlank()) holds.getOrPut(host) { mutableListOf() }.add(wrapped)
        holds.getOrPut("url:" + NewsHosts.canonicalUrl(clean)) { mutableListOf() }.add(wrapped)
        beginVerify(clean, title = "", packageName = null, startFrom = null, handoffIfNotNews = false)
        return true
    }

    /**
     * Show the small「驗證中」float, classify with the vendor flash model,
     * then open the 擇要 page or let the real browser continue.
     */
    fun beginVerify(
        url: String,
        title: String,
        packageName: String?,
        startFrom: Context? = null,
        handoffIfNotNews: Boolean,
    ) {
        if (!::app.isInitialized) return
        val clean = sanitizeUrl(url) ?: return
        scope.launch {
            if (appUiVisible) {
                if (handoffIfNotNews) openInBrowser(clean)
                return@launch
            }
            val settings = app.settings.current()
            if (!app.settings.isInterceptArmed(settings) || !app.settings.isAppTargeted(settings, packageName)) {
                if (handoffIfNotNews) openInBrowser(clean, settings.handoffBrowserPackage)
                return@launch
            }
            if (!NewsHosts.isClassifiableUrl(clean) || isUrlCooled(clean)) {
                if (handoffIfNotNews) openInBrowser(clean, settings.handoffBrowserPackage)
                return@launch
            }
            if (settings.interceptAllPages) {
                if (settings.askBeforeSummary) {
                    launchAsk(clean, title, packageName, startFrom)
                } else {
                    startOrRefine(clean, title, packageName, startFrom)
                }
                return@launch
            }
            val now = SystemClock.elapsedRealtime()
            val last = lastSeenUrlAt.put(normalizeUrl(clean), now)
            if (last != null && now - last < DUP_WINDOW_MS) return@launch
            val cached = NewsClassifier.cached(NewsHosts.canonicalUrl(clean))
            if (cached == true) {
                startOrRefine(clean, title, packageName, startFrom)
                return@launch
            }
            if (cached == false) {
                if (handoffIfNotNews) openInBrowser(clean, settings.handoffBrowserPackage)
                return@launch
            }
            launchVerifyActivity(clean, title, packageName, startFrom, handoffIfNotNews)
        }
    }

    fun allowThrough(url: String) {
        markAllowed(url)
        val host = NewsHosts.hostOf(url).lowercase()
        hostPassthroughUntil[host] = SystemClock.elapsedRealtime() + siteCooldownMs
        releaseHolds(host)
        openInBrowser(url)
    }

    private fun launchAsk(url: String, title: String, packageName: String?, startFrom: Context?) {
        val context: Context = startFrom ?: app
        val intent = Intent(context, com.wusper.zeyao.AskInterceptActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(com.wusper.zeyao.AskInterceptActivity.EXTRA_URL, url)
            putExtra(com.wusper.zeyao.AskInterceptActivity.EXTRA_TITLE, title)
            putExtra(com.wusper.zeyao.AskInterceptActivity.EXTRA_PACKAGE, packageName)
        }
        startNow(context, intent)
    }

    private fun openInBrowser(url: String, preferred: String? = null) {
        scope.launch(Dispatchers.Main.immediate) {
            val pkg = preferred ?: runCatching { app.settings.current().handoffBrowserPackage }.getOrNull()
            BrowserHandoff.open(app, url, pkg)
        }
    }

    fun onHostDetected(host: String, packageName: String? = null) {
        // SNI / Host only tells us the site, not the article. Never start a
        // 擇要 session or summarize the homepage from a bare host.
        if (host.isBlank()) return
        if (isHostPassthrough(host)) return
    }

    fun registerHold(host: String, connection: HeldConnection) {
        val key = host.lowercase()
        if (isHostPassthrough(key) || SystemClock.elapsedRealtime() < suppressUntilElapsed) {
            connection.release()
            return
        }
        val hasOpen = _sessions.value.values.any { it.host.equals(host, true) && !it.decided }
        if (!hasOpen) {
            // No article URL yet: do not stall the page and do not pop 擇要.
            connection.release()
            return
        }
        holds.getOrPut(key) { mutableListOf() }.add(connection)
    }

    fun startOrRefine(
        url: String,
        title: String,
        packageName: String?,
        startFrom: Context? = null,
    ) {
        val host = NewsHosts.hostOf(url).ifBlank { Uri.parse(url).host.orEmpty() }
        val open = findOpenSessionFor(url)
        if (open != null) {
            val refined = shouldReplaceUrl(open.url, url)
            if (refined || (title.isNotBlank() && open.title.isBlank())) {
                val nextUrl = if (refined) url else open.url
                val nextTitle = title.ifBlank { open.title }
                updateSession(open.id) { it.copy(url = nextUrl, title = nextTitle, host = host.ifBlank { it.host }) }
                if (refined && NewsHosts.isBareHostUrl(open.url) && !NewsHosts.isBareHostUrl(url)) {
                    // Restart generation with the real article URL if we only had a host.
                    jobs.remove(open.id)?.cancel()
                    beginGeneration(open.id, nextUrl, nextTitle, host)
                }
            }
            launchActivity(open.id, if (refined) url else open.url, title.ifBlank { open.title }, packageName, startFrom)
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
        launchActivity(id, url, session.title, packageName, startFrom)
        beginGeneration(id, url, session.title, host)
    }

    fun onEnter(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(decided = true) }
        markAllowed(session.url)
        hostPassthroughUntil[session.host.lowercase()] = SystemClock.elapsedRealtime() + siteCooldownMs
        releaseHolds(session.host)
        maybeCancelOnExit(sessionId)
        scope.launch(Dispatchers.Main) {
            val preferred = runCatching { app.settings.current().handoffBrowserPackage }.getOrNull()
            BrowserHandoff.open(app, session.url, preferred)
        }
    }

    fun onBack(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(decided = true) }
        suppressUntilElapsed = SystemClock.elapsedRealtime() + ENTER_SUPPRESS_MS
        rejectHolds(session.host)
        maybeCancelOnExit(sessionId)
        scope.launch(Dispatchers.Main) {
            delay(280)
            accessibility?.tryGoBack()
        }
    }

    fun regenerate(
        sessionId: String,
        length: com.wusper.zeyao.data.SummaryLength? = null,
        search: Boolean? = null,
    ) {
        val session = _sessions.value[sessionId] ?: return
        jobs[sessionId]?.cancel()
        updateSession(sessionId) { it.copy(summary = "", streaming = true, error = null) }
        jobs[sessionId] = scope.launch {
            beginGeneration(sessionId, session.url, session.title, session.host, length, search)
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

    private fun beginGeneration(
        id: String,
        url: String,
        title: String,
        host: String,
        lengthOverride: com.wusper.zeyao.data.SummaryLength? = null,
        searchOverride: Boolean? = null,
    ) {
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            // If we only have a bare host, wait briefly for accessibility to supply the path.
            var workingUrl = url
            var workingTitle = title
            if (NewsHosts.isBareHostUrl(url) || !NewsHosts.isClassifiableUrl(url)) {
                val deadline = SystemClock.elapsedRealtime() + 1_500
                while (SystemClock.elapsedRealtime() < deadline) {
                    val latest = _sessions.value[id] ?: break
                    if (!NewsHosts.isBareHostUrl(latest.url) && NewsHosts.isClassifiableUrl(latest.url)) {
                        workingUrl = latest.url
                        workingTitle = latest.title.ifBlank { workingTitle }
                        break
                    }
                    delay(50)
                }
            }
            if (NewsHosts.isBareHostUrl(workingUrl) || !NewsHosts.isClassifiableUrl(workingUrl)) {
                fail(id, "尚未取得文章網址，仍可進入或返回")
                return@launch
            }
            var captured = PageCapture.get(workingUrl)
            if (captured == null && isHttpsDecryptActive()) {
                val waitUntil = SystemClock.elapsedRealtime() + 2_500
                while (captured == null && SystemClock.elapsedRealtime() < waitUntil) {
                    delay(80)
                    captured = PageCapture.get(workingUrl)
                }
            }
            val fetched = if (captured != null) {
                Result.success(ArticleFetcher.parse(workingUrl, captured))
            } else {
                runCatching { ArticleFetcher.fetch(workingUrl) }
            }
            val article = fetched.getOrNull()
            if (article != null) {
                workingTitle = article.title.ifBlank { workingTitle }
                updateSession(id) { it.copy(title = workingTitle, url = workingUrl) }
                app.records.update(id, title = workingTitle, url = workingUrl)
            }
            val settings = app.settings.current()
            ScreenshotBuffer.enabled = settings.imageRecognitionEnabled
            val key = app.secrets.getApiKey(settings.providerId)
            if (key.isNullOrBlank()) {
                fail(id, "尚未設定 API Key，仍可進入或返回")
                return@launch
            }
            if (article == null && fetched.exceptionOrNull() != null && NewsHosts.isBareHostUrl(workingUrl)) {
                // Still try a brief based on host/title so the page isn't empty forever.
            }
            if (article == null && fetched.exceptionOrNull() != null) {
                // Continue with whatever we have; the model may still produce a short note.
            }
            try {
                val client = AiClientFactory.create(settings.providerId)
                var imageHint = ""
                if (settings.imageRecognitionEnabled) {
                    val shot = ScreenshotBuffer.previous()
                    if (shot != null) {
                        val extracted = com.wusper.zeyao.ai.ImageTitleExtractor.extract(
                            apiKey = key,
                            providerId = settings.providerId,
                            bitmap = shot,
                        )
                        if (!extracted.isNullOrBlank()) {
                            imageHint = "截圖辨識結果（優先標題）：$extracted\n"
                            if (settings.providerId == "deepseek") {
                                val hits = com.wusper.zeyao.ai.DeepSeekSearch.search(key, extracted.take(100), count = 3)
                                val formatted = com.wusper.zeyao.ai.DeepSeekSearch.formatForPrompt(hits)
                                if (formatted.isNotBlank()) imageHint += formatted + "\n"
                            }
                        }
                    }
                }
                val useSearch = (searchOverride ?: settings.searchNewsEnabled) &&
                    settings.providerId == "deepseek"
                val searchContext = if (useSearch) {
                    val hits = com.wusper.zeyao.ai.DeepSeekSearch.searchForArticle(
                        apiKey = key,
                        title = workingTitle,
                        url = workingUrl,
                        articleText = article?.text.orEmpty(),
                    )
                    com.wusper.zeyao.ai.DeepSeekSearch.formatForPrompt(hits)
                } else {
                    ""
                }
                val articleText = buildString {
                    if (imageHint.isNotBlank()) {
                        append(imageHint)
                        appendLine("---")
                    }
                    if (searchContext.isNotBlank()) {
                        append(searchContext)
                        appendLine()
                        appendLine("---")
                    }
                    append(article?.text.orEmpty())
                }
                client.streamSummary(
                    apiKey = key,
                    title = workingTitle,
                    url = workingUrl,
                    articleText = articleText,
                    params = GenerationParams(
                        language = settings.language,
                        summaryLength = lengthOverride ?: settings.summaryLength,
                        searchNews = useSearch,
                    ),
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

    private fun launchActivity(
        id: String,
        url: String,
        title: String,
        packageName: String?,
        startFrom: Context? = null,
    ) {
        val context: Context = startFrom ?: app
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
        startNow(context, intent)
    }

    private fun launchVerifyActivity(
        url: String,
        title: String,
        packageName: String?,
        startFrom: Context?,
        handoffIfNotNews: Boolean,
    ) {
        val context: Context = startFrom ?: app
        val intent = Intent(context, VerifyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(VerifyActivity.EXTRA_URL, url)
            putExtra(VerifyActivity.EXTRA_TITLE, title)
            putExtra(VerifyActivity.EXTRA_PACKAGE, packageName)
            putExtra(VerifyActivity.EXTRA_HANDOFF, handoffIfNotNews)
        }
        startNow(context, intent)
    }

    private fun startNow(context: Context, intent: Intent) {
        val start = { runCatching { context.startActivity(intent) } }
        if (context is Activity || Looper.myLooper() == Looper.getMainLooper()) {
            start()
        } else {
            scope.launch(Dispatchers.Main.immediate) { start() }
        }
    }

    private fun findOpenSessionFor(url: String): InterceptSession? {
        val host = NewsHosts.hostOf(url)
        return _sessions.value.values
            .filter { !it.decided }
            .firstOrNull { existing ->
                NewsHosts.sameArticle(existing.url, url) ||
                    (existing.host.equals(host, true) && (NewsHosts.isBareHostUrl(existing.url) || NewsHosts.isBareHostUrl(url)))
            }
    }

    private fun shouldReplaceUrl(current: String, incoming: String): Boolean {
        if (current == incoming) return false
        if (NewsHosts.isBareHostUrl(current) && !NewsHosts.isBareHostUrl(incoming)) return true
        return false
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

    private fun normalizeUrl(url: String): String = NewsHosts.canonicalUrl(url)
}

private class IdempotentHold(private val inner: HeldConnection) : HeldConnection {
    private val done = AtomicBoolean(false)
    override fun release() {
        if (done.compareAndSet(false, true)) runCatching { inner.release() }
    }
    override fun reject() {
        if (done.compareAndSet(false, true)) runCatching { inner.reject() }
    }
}

interface HeldConnection {
    fun release()
    fun reject()
}
