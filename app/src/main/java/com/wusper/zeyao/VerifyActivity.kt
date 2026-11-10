package com.wusper.zeyao

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.ai.NewsClassifier
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.intercept.InterceptController
import com.wusper.zeyao.ui.theme.ZeyaoTheme
import com.wusper.zeyao.ui.verify.VerifyScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class VerifyActivity : ComponentActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var decided = false
    private var handoffPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        bind()
        startClassify(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        decided = false
        startClassify(intent)
    }

    private fun bind() {
        setContent {
            val app = ZeyaoApp.instance
            val settings by app.settings.settings.collectAsState(initial = AppSettings())
            ZeyaoTheme(
                themeStyle = settings.themeStyle,
                typographyStyle = settings.typographyStyle,
                accent = Color(settings.accentColor),
            ) {
                BackHandler { openNormallyAndFinish() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    VerifyScreen()
                }
            }
        }
    }

    private fun startClassify(intent: Intent) {
        job?.cancel()
        val url = NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val handoffIfNotNews = intent.getBooleanExtra(EXTRA_HANDOFF, true)
        if (url.isBlank()) {
            openNormallyAndFinish()
            return
        }
        job = scope.launch {
            val app = ZeyaoApp.instance
            val settings = runCatching { app.settings.current() }.getOrNull()
            handoffPackage = settings?.handoffBrowserPackage
            val knownNews = NewsHosts.shouldInterceptUrl(url, loose = true)
            val outcome = runCatching {
                val key = settings?.let { app.secrets.getApiKey(it.providerId) }.orEmpty()
                if (key.isBlank()) return@runCatching if (knownNews) true else null
                val canonical = NewsHosts.canonicalUrl(url)
                val cached = NewsClassifier.cached(canonical)
                if (cached != null) return@runCatching cached
                val provider = AiProvider.fromId(settings?.providerId ?: "deepseek")
                val result = withTimeout(CLASSIFY_TIMEOUT_MS) {
                    NewsClassifier.classify(provider, key, title, url)
                }
                NewsClassifier.remember(canonical, result)
                result
            }.getOrElse { e ->
                if (e is CancellationException) throw e
                if (knownNews) true else null
            }
            when (outcome) {
                true -> {
                    decided = true
                    InterceptController.startOrRefine(
                        url,
                        title = title,
                        packageName = pkg,
                        startFrom = this@VerifyActivity,
                    )
                    finish()
                    overridePendingTransition(0, 0)
                }
                false -> {
                    InterceptController.markNotNews(url)
                    if (handoffIfNotNews) {
                        openNormallyAndFinish(mark = false)
                    } else {
                        decided = true
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }
                else -> {
                    // API error / missing key on an unknown site: let through, do not poison the cache.
                    InterceptController.releaseDocumentHolds(url)
                    if (handoffIfNotNews) {
                        openNormallyAndFinish(mark = false)
                    } else {
                        decided = true
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }
            }
        }
    }

    private fun openNormallyAndFinish(mark: Boolean = false) {
        if (decided) return
        decided = true
        job?.cancel()
        val url = NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        if (mark) InterceptController.markNotNews(url) else InterceptController.releaseDocumentHolds(url)
        if (url.isNotBlank()) {
            BrowserHandoff.open(this, url, handoffPackage)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_HANDOFF = "handoff"
        private const val CLASSIFY_TIMEOUT_MS = 12_000L
    }
}
