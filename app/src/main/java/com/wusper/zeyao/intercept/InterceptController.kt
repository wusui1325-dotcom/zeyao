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
import com.wusper.zeyao.ai.SummaryCache
import com.wusper.zeyao.ai.AiClientFactory
import com.wusper.zeyao.ai.AiInitException
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.ai.GenerationParams
import com.wusper.zeyao.ai.HttpClients
import com.wusper.zeyao.ai.NewsClassifier
import com.wusper.zeyao.ai.VerifyClassifier
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.ModelRole
import com.wusper.zeyao.data.ModelRoleSettings
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.data.RedirectResolver
import com.wusper.zeyao.intercept.helpers.UrlCooldownManager
import com.wusper.zeyao.intercept.helpers.UrlDebouncer
import com.wusper.zeyao.intercept.helpers.VerifyOutcome
import com.wusper.zeyao.intercept.helpers.VerifyPlan
import com.wusper.zeyao.intercept.helpers.VerifyPlanInput
import com.wusper.zeyao.intercept.helpers.VerifyPlanner
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.extract.ArticleFetcher
import com.wusper.zeyao.mitm.LocalCa
import com.wusper.zeyao.mitm.PageCapture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val sources: List<com.wusper.zeyao.ai.FactChecker.Source> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val fromCache: Boolean = false,
)

/**
 * Coordinates intercept UI, summary generation, URL cooldown, and VPN holds.
 * Generation runs in the application scope so 「退出後繼續生成」 can finish after the page closes.
 */
object InterceptController {
    private const val ENTER_SUPPRESS_MS = 12_000L
    private const val DEFAULT_SITE_COOLDOWN_MS = 10 * 60 * 1000L
    private const val SESSION_KEEP_MS = 15 * 60 * 1000L
    private const val SESSION_MAX = 40

    @Volatile
    private var siteCooldownMs: Long = DEFAULT_SITE_COOLDOWN_MS

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: ZeyaoApp

    private val _sessions = MutableStateFlow<Map<String, InterceptSession>>(emptyMap())
    val sessions: StateFlow<Map<String, InterceptSession>> = _sessions.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val urlCooldown = UrlCooldownManager()
    private val urlDebouncer = UrlDebouncer()
    private val hostPassthroughUntil = ConcurrentHashMap<String, Long>()
    private val holds = ConcurrentHashMap<String, CopyOnWriteArrayList<HeldConnection>>()
    private val classifyJobs = ConcurrentHashMap<String, Job>()
    private val classifyResults = ConcurrentHashMap<String, CompletableDeferred<VerifyOutcome>>()

    @Volatile
    private var warmedProviderId: String? = null

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
    var httpsDecryptNewsOnlyCached: Boolean = true
        private set

    @Volatile
    var caInstalledCached: Boolean = false
        private set

    @Volatile
    private var skippedHostsCached: Set<String> = emptySet()

    fun attach(application: ZeyaoApp) {
        app = application
        scope.launch {
            app.settings.settings.collect { settings ->
                interceptArmedCached = app.settings.isInterceptArmed(settings)
                httpsDecryptCached = settings.httpsDecryptEnabled
                httpsDecryptNewsOnlyCached = settings.httpsDecryptNewsOnly
                skippedHostsCached = settings.skippedHosts
                siteCooldownMs = settings.siteCooldownSeconds.coerceIn(10, 600).toLong() * 1000L
                ScreenshotBuffer.enabled = settings.imageRecognitionEnabled
                if (settings.httpsDecryptEnabled) {
                    refreshCaInstalledAsync()
                }
                warmHotPath(settings)
            }
        }
    }

    fun refreshCaInstalledAsync() {
        if (!::app.isInitialized) return
        scope.launch(Dispatchers.IO) {
            refreshCaInstalled()
        }
    }

    fun refreshCaInstalled() {
        if (!::app.isInitialized) return
        caInstalledCached = runCatching {
            LocalCa.ensure(app)
            LocalCa.isInstalled(app)
        }.getOrDefault(false)
    }

    fun isHttpsDecryptActive(host: String = ""): Boolean {
        if (!httpsDecryptCached || !caInstalledCached) return false
        if (!httpsDecryptNewsOnlyCached) return true
        return host.isBlank() || NewsHosts.isNewsHost(host)
    }

    fun isSkipped(urlOrHost: String): Boolean =
        NewsHosts.isSkippedHost(urlOrHost, skippedHostsCached)

    fun sessionOf(id: String): InterceptSession? = _sessions.value[id]

    fun peek(id: String): InterceptSession? = _sessions.value[id]

    fun isUrlCooled(url: String): Boolean = urlCooldown.isCooled(url, suppressUntilElapsed)

    private fun markAllowed(url: String) {
        urlCooldown.mark(url, siteCooldownMs)
        suppressUntilElapsed = SystemClock.elapsedRealtime() + ENTER_SUPPRESS_MS
    }

    fun isHostPassthrough(host: String): Boolean {
        val until = hostPassthroughUntil[host.lowercase()] ?: return false
        return SystemClock.elapsedRealtime() < until
    }

    suspend fun shouldIntercept(url: String, packageName: String?): Boolean {
        if (appUiVisible) return false
        if (isSkipped(url)) return false
        if (!NewsHosts.isClassifiableUrl(url)) return false
        if (isUrlCooled(url)) return false
        val settings = app.settings.cached() ?: app.settings.current()
        if (!app.settings.isInterceptArmed(settings)) return false
        if (!app.settings.isAppTargeted(settings, packageName)) return false
        return true
    }

    fun cachedSettings(): AppSettings =
        if (::app.isInitialized) app.settings.cachedOrDefault() else AppSettings()

    /**
     * Called from the accessibility service (full URL) or VPN (host-only HTTPS URL).
     * Ordinary URLs start verify immediately; only known shorteners resolve in
     * the classify job, after the 驗證中 float is already showing.
     */
    fun onUrlDetected(url: String, title: String? = null, packageName: String? = null) {
        if (!::app.isInitialized) return
        val clean = sanitizeUrl(url) ?: return
        val unwrapped = NewsHosts.unwrapUrl(clean)
        beginVerify(
            unwrapped,
            title.orEmpty(),
            packageName,
            startFrom = null,
            handoffIfNotNews = false,
            resolveFirst = RedirectResolver.needsNetworkResolve(unwrapped),
        )
    }

    /**
     * Default-browser entry from [com.wusper.zeyao.LinkRouterActivity].
     * Must not wait on DataStore or redirect HEADs before returning so the
     * router can finish immediately.
     */
    fun onExternalLink(raw: String, packageName: String?, startFrom: Activity) {
        if (!::app.isInitialized) return
        val clean = sanitizeUrl(NewsHosts.unwrapUrl(raw))
        if (clean == null) {
            runCatching {
                startFrom.startActivity(
                    Intent(startFrom, com.wusper.zeyao.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        beginVerify(
            clean,
            title = "",
            packageName = packageName,
            startFrom = startFrom,
            handoffIfNotNews = true,
            fallbackToMain = true,
            resolveFirst = RedirectResolver.needsNetworkResolve(clean),
        )
    }

    fun markNotNews(url: String) {
        if (url.isBlank()) return
        val key = NewsHosts.canonicalUrl(url)
        NewsClassifier.remember(key, false)
        urlCooldown.mark(url, siteCooldownMs)
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
        if (clean == null || appUiVisible || isUrlCooled(clean) || isSkipped(clean)) {
            wrapped.release()
            return false
        }
        val host = NewsHosts.hostOf(clean).lowercase()
        if (host.isNotBlank() && isHostPassthrough(host)) {
            wrapped.release()
            return false
        }
        if (host.isNotBlank()) holds.getOrPut(host) { CopyOnWriteArrayList() }.add(wrapped)
        holds.getOrPut("url:" + NewsHosts.canonicalUrl(clean)) { CopyOnWriteArrayList() }.add(wrapped)
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
        fallbackToMain: Boolean = false,
        resolveFirst: Boolean = false,
    ) {
        if (!::app.isInitialized) return
        val clean = sanitizeUrl(url) ?: return
        val cached = app.settings.cached()
        if (cached != null) {
            beginVerifyWithSettings(
                clean, title, packageName, startFrom,
                handoffIfNotNews, fallbackToMain, resolveFirst, cached,
            )
        } else {
            scope.launch {
                val loaded = app.settings.current()
                beginVerifyWithSettings(
                    clean, title, packageName, startFrom,
                    handoffIfNotNews, fallbackToMain, resolveFirst, loaded,
                )
            }
        }
    }

    private fun beginVerifyWithSettings(
        clean: String,
        title: String,
        packageName: String?,
        startFrom: Context?,
        handoffIfNotNews: Boolean,
        fallbackToMain: Boolean,
        resolveFirst: Boolean,
        settings: AppSettings,
    ) {
        val plan = VerifyPlanner.plan(
            VerifyPlanInput(
                appUiVisible = appUiVisible,
                armed = app.settings.isInterceptArmed(settings),
                targeted = app.settings.isAppTargeted(settings, packageName),
                skipped = NewsHosts.isSkippedHost(clean, settings.skippedHosts) || isSkipped(clean),
                cooled = isUrlCooled(clean),
                classifiable = NewsHosts.isClassifiableUrl(clean) || resolveFirst,
                interceptAllPages = settings.interceptAllPages,
                askBeforeSummary = settings.askBeforeSummary,
                cachedNews = if (resolveFirst) null else NewsClassifier.cached(NewsHosts.canonicalUrl(clean)),
            ),
        )
        when (plan) {
            VerifyPlan.ABORT -> {
                if (handoffIfNotNews) {
                    openInBrowser(clean, settings.handoffBrowserPackage, fallbackToMain)
                }
            }
            VerifyPlan.ASK -> launchAsk(clean, title, packageName, startFrom)
            VerifyPlan.SUMMARIZE -> startOrRefine(clean, title, packageName, startFrom)
            VerifyPlan.CLASSIFY -> {
                if (!urlDebouncer.accept(clean)) return
                pruneTransientMaps(SystemClock.elapsedRealtime())
                ensureClassify(clean, title, settings, resolveFirst)
                launchVerifyActivity(clean, title, packageName, startFrom, handoffIfNotNews)
            }
        }
    }

    fun pendingClassify(url: String): Deferred<VerifyOutcome>? =
        classifyResults[NewsHosts.canonicalUrl(url)]

    fun cancelClassify(url: String) {
        val key = NewsHosts.canonicalUrl(url)
        classifyJobs.remove(key)?.cancel()
        classifyResults.remove(key)?.let { if (it.isActive) it.cancel() }
    }

    fun ensureClassify(
        url: String,
        title: String,
        settings: AppSettings,
        resolveFirst: Boolean = false,
    ): Deferred<VerifyOutcome> {
        val key = NewsHosts.canonicalUrl(url)
        classifyResults[key]?.let { existing ->
            if (existing.isActive) return existing
        }
        val deferred = CompletableDeferred<VerifyOutcome>()
        classifyResults[key] = deferred
        classifyJobs.remove(key)?.cancel()
        classifyJobs[key] = scope.launch {
            try {
                var working = url
                if (resolveFirst || RedirectResolver.needsNetworkResolve(url)) {
                    working = RedirectResolver.resolve(url)
                    if (isSkipped(working) || isUrlCooled(working)) {
                        deferred.complete(VerifyOutcome(working, news = null))
                        return@launch
                    }
                    NewsClassifier.cached(NewsHosts.canonicalUrl(working))?.let { cached ->
                        deferred.complete(VerifyOutcome(working, cached))
                        return@launch
                    }
                }
                val apiKey = app.secrets.getApiKeyForRole(settings.providerId, ModelRole.VERIFY.storageId)
                val news = VerifyClassifier.run(working, title, settings, apiKey)
                deferred.complete(VerifyOutcome(working, news))
            } catch (e: CancellationException) {
                if (deferred.isActive) deferred.cancel(e)
                throw e
            } catch (_: Exception) {
                if (deferred.isActive) deferred.complete(VerifyOutcome(url, news = null))
            }
        }
        return deferred
    }

    fun allowThrough(url: String) {
        markAllowed(url)
        val host = NewsHosts.hostOf(url).lowercase()
        // Brief host passthrough lets the current page load; article cooldown is URL-level.
        hostPassthroughUntil[host] = SystemClock.elapsedRealtime() + ENTER_SUPPRESS_MS
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

    private fun openInBrowser(url: String, preferred: String? = null, fallbackToMain: Boolean = false) {
        scope.launch(Dispatchers.Main.immediate) {
            val pkg = preferred?.ifBlank { null } ?: app.settings.cached()?.handoffBrowserPackage?.ifBlank { null }
            val ok = BrowserHandoff.open(app, url, pkg)
            if (!ok && fallbackToMain) {
                runCatching {
                    app.startActivity(
                        Intent(app, com.wusper.zeyao.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
        }
    }

    private fun warmHotPath(settings: AppSettings) {
        if (!settings.interceptEnabled && !app.settings.isInterceptArmed(settings)) return
        runCatching {
            app.secrets.getApiKeyForRole(settings.providerId, ModelRole.VERIFY.storageId)
            app.secrets.getApiKeyForRole(settings.providerId, ModelRole.SUMMARIZE.storageId)
            app.secrets.getApiKey(settings.providerId)
        }
        if (warmedProviderId != settings.providerId) {
            warmedProviderId = settings.providerId
            HttpClients.warmClassify(AiProvider.fromId(settings.providerId))
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
        holds.getOrPut(key) { CopyOnWriteArrayList() }.add(connection)
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
        pruneSessions()
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
            runCatching {
                app.settings.update { it.copy(interceptCount = it.interceptCount + 1) }
            }
        }
        launchActivity(id, url, session.title, packageName, startFrom)
        beginGeneration(id, url, session.title, host)
    }

    fun onEnter(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(decided = true) }
        markAllowed(session.url)
        hostPassthroughUntil[session.host.lowercase()] = SystemClock.elapsedRealtime() + ENTER_SUPPRESS_MS
        releaseHolds(session.host)
        maybeCancelOnExit(sessionId)
        scope.launch(Dispatchers.Main.immediate) {
            val preferred = app.settings.cached()?.handoffBrowserPackage?.ifBlank { null }
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

    fun skipSiteAndEnter(sessionId: String) {
        val session = _sessions.value[sessionId] ?: return
        val host = NewsHosts.normalizedHost(session.host.ifBlank { session.url })
        if (host.isNotBlank()) {
            scope.launch {
                runCatching {
                    app.settings.update { current ->
                        current.copy(skippedHosts = current.skippedHosts + host)
                    }
                }
            }
        }
        onEnter(sessionId)
    }

    fun regenerate(
        sessionId: String,
        length: com.wusper.zeyao.data.SummaryLength? = null,
        search: Boolean? = null,
    ) {
        val session = _sessions.value[sessionId] ?: return
        updateSession(sessionId) { it.copy(summary = "", streaming = true, error = null, fromCache = false) }
        beginGeneration(sessionId, session.url, session.title, session.host, length, search)
    }

    fun maybeCancelOnExit(sessionId: String) {
        scope.launch {
            val continueOnExit = app.settings.cached()?.continueOnExit
                ?: app.settings.current().continueOnExit
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
            if ((NewsHosts.isBareHostUrl(workingUrl) || !NewsHosts.isClassifiableUrl(workingUrl)) &&
                !(app.settings.cached()?.interceptAllPages ?: app.settings.current().interceptAllPages)
            ) {
                fail(id, errorString(com.wusper.zeyao.R.string.no_article_url_still_enter))
                return@launch
            }
            // Check short-lived cache before any network/capture wait so cache hits are instant.
            val cached = SummaryCache.get(workingUrl)
            if (cached != null && lengthOverride == null && searchOverride == null) {
                updateSession(id) { it.copy(summary = cached, streaming = false, error = null, fromCache = true) }
                app.records.update(id, summary = cached, title = workingTitle, status = RecordStatus.COMPLETED)
                return@launch
            }
            val settings = app.settings.cached() ?: app.settings.current()
            ScreenshotBuffer.enabled = settings.imageRecognitionEnabled
            val search = ModelRoleSettings.resolve(ModelRole.SEARCH, settings)
            val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, settings)
            val searchKey = app.secrets.getApiKeyForRole(settings.providerId, ModelRole.SEARCH.storageId)
            val summarizeKey = app.secrets.getApiKeyForRole(settings.providerId, ModelRole.SUMMARIZE.storageId)
            val key = summarizeKey
            if (key.isNullOrBlank()) {
                fail(id, errorString(com.wusper.zeyao.R.string.no_api_key_still_enter))
                return@launch
            }
            val effectiveSearchKey = searchKey ?: key
            try {
                // Overlap optional screenshot title extraction with article fetching.
                val (article, imageHint) = coroutineScope {
                    val articleDeferred = async {
                        var captured = PageCapture.get(workingUrl)
                        if (captured == null && isHttpsDecryptActive()) {
                            val waitUntil = SystemClock.elapsedRealtime() + 2_500
                            while (captured == null && SystemClock.elapsedRealtime() < waitUntil) {
                                delay(80)
                                captured = PageCapture.get(workingUrl)
                            }
                        }
                        if (captured != null) {
                            runCatching { ArticleFetcher.parse(workingUrl, captured) }.getOrNull()
                        } else {
                            runCatching { ArticleFetcher.fetch(workingUrl) }.getOrNull()
                        }
                    }
                    val imageHintDeferred = async {
                        if (!settings.imageRecognitionEnabled) return@async ""
                        val shot = ScreenshotBuffer.previous() ?: return@async ""
                        val extracted = com.wusper.zeyao.ai.ImageTitleExtractor.extract(
                            apiKey = effectiveSearchKey,
                            providerId = settings.providerId,
                            bitmap = shot,
                            model = search.model,
                            temperature = search.temperature,
                        )
                        if (extracted.isNullOrBlank()) return@async ""
                        buildString {
                            append("截圖辨識結果（優先標題）：").append(extracted).append('\n')
                            if (settings.providerId == "deepseek") {
                                val hits = com.wusper.zeyao.ai.DeepSeekSearch.search(
                                    effectiveSearchKey,
                                    extracted.take(100),
                                    count = search.searchCount.coerceAtLeast(3),
                                )
                                val formatted = com.wusper.zeyao.ai.DeepSeekSearch.formatForPrompt(hits)
                                if (formatted.isNotBlank()) append(formatted).append('\n')
                            }
                        }
                    }
                    articleDeferred.await() to imageHintDeferred.await()
                }
                if (article != null) {
                    workingTitle = article.title.ifBlank { workingTitle }
                    updateSession(id) { it.copy(title = workingTitle, url = workingUrl) }
                    app.records.update(id, title = workingTitle, url = workingUrl)
                }
                val client = AiClientFactory.create(settings.providerId)
                val useSearch = (searchOverride ?: settings.searchNewsEnabled) &&
                    settings.providerId == "deepseek"
                val useFactCheck = settings.authenticityCheckEnabled && settings.providerId == "deepseek"
                val (factSources, searchContext) = coroutineScope {
                    val factDeferred = async {
                        if (!useFactCheck) emptyList()
                        else com.wusper.zeyao.ai.FactChecker.check(
                            apiKey = effectiveSearchKey,
                            providerId = settings.providerId,
                            model = search.model,
                            title = workingTitle,
                            url = workingUrl,
                            articleText = article?.text.orEmpty(),
                            fallback = settings.searchNewsEnabled,
                            temperature = search.temperature,
                            maxTokens = maxOf(search.maxTokens, 200),
                            searchCount = search.factSearchCount,
                        )
                    }
                    val searchDeferred = async {
                        if (!useSearch) ""
                        else {
                            val hits = com.wusper.zeyao.ai.DeepSeekSearch.searchForArticle(
                                apiKey = effectiveSearchKey,
                                title = workingTitle,
                                url = workingUrl,
                                articleText = article?.text.orEmpty(),
                                model = search.model,
                                temperature = search.temperature,
                                maxTokens = search.maxTokens,
                                count = search.searchCount,
                                fallback = settings.searchNewsEnabled,
                            )
                            com.wusper.zeyao.ai.DeepSeekSearch.formatForPrompt(hits)
                        }
                    }
                    factDeferred.await() to searchDeferred.await()
                }
                val factContext = com.wusper.zeyao.ai.FactChecker.format(factSources)
                if (factSources.isNotEmpty()) {
                    updateSession(id) { it.copy(sources = factSources) }
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
                    if (factContext.isNotBlank()) {
                        append(factContext)
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
                        antiFraud = settings.antiFraudEnabled,
                        temperature = summarize.temperature,
                        maxTokens = summarize.maxTokens,
                        fallbackModels = summarize.models.joinToString(","),
                        reasoningEffort = summarize.reasoningEffort,
                        reasoningEffortHigh = summarize.reasoningEffort == "high",
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
                    snapshot?.let { app.records.update(id, summary = it.summary, title = it.title, flush = false) }
                }
                // Cache successful result
                val finalSession = _sessions.value[id]
                if (finalSession != null && finalSession.summary.isNotBlank() && finalSession.error == null) {
                    SummaryCache.put(workingUrl, finalSession.summary)
                }
                val final = finalSession
                if (final == null) return@launch
                val empty = final.summary.isBlank()
                if (empty) {
                    fail(id, errorString(com.wusper.zeyao.R.string.summary_failed_still_enter))
                } else {
                    updateSession(id) { it.copy(streaming = false, error = null) }
                    app.records.update(id, summary = final.summary, status = RecordStatus.COMPLETED)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val message = when (e) {
                    is AiInitException -> e.message ?: errorString(com.wusper.zeyao.R.string.summary_failed_still_enter)
                    else -> errorString(com.wusper.zeyao.R.string.summary_failed_still_enter)
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

    private fun pruneTransientMaps(now: Long) {
        if (hostPassthroughUntil.size > 64) {
            hostPassthroughUntil.entries.removeIf { it.value <= now }
        }
        if (classifyResults.size > 32) {
            classifyResults.entries.removeIf { !it.value.isActive && it.value.isCompleted }
            classifyJobs.entries.removeIf { (_, job) -> job.isCompleted || job.isCancelled }
        }
    }

    private fun errorString(resId: Int): String =
        com.wusper.zeyao.data.AppLocale.wrap(app).getString(resId)

    private fun pruneSessions() {

        val now = System.currentTimeMillis()
        _sessions.update { map ->
            val kept = map.filterValues { session ->
                session.streaming || !session.decided || now - session.createdAt < SESSION_KEEP_MS
            }
            if (kept.size <= SESSION_MAX) kept
            else kept.entries
                .sortedByDescending { it.value.createdAt }
                .take(SESSION_MAX)
                .associate { it.key to it.value }
        }
        jobs.entries.removeIf { (_, job) -> job.isCompleted || job.isCancelled }
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
