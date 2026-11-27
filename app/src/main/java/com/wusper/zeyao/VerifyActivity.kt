package com.wusper.zeyao

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.wusper.zeyao.data.AppLocale
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.data.ThemeStyle
import com.wusper.zeyao.intercept.InterceptController
import com.wusper.zeyao.intercept.helpers.VerifyOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Lightweight「驗證中」float. Classification is started by
 * [InterceptController] before this activity is created; this screen
 * only shows the float and applies the already-running result.
 * A static layout is used so the first frame does not wait on Compose
 * or a settings disk read.
 */
class VerifyActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var activeUrl: String = ""
    private var decided = false
    private var handoffPackage: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_verify)
        applyCachedColors()
        bindClassify(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val incomingUrl = NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        if (!decided && activeUrl.isNotBlank() && !NewsHosts.sameArticle(activeUrl, incomingUrl)) {
            InterceptController.cancelClassify(activeUrl)
            InterceptController.releaseDocumentHolds(activeUrl)
        }
        setIntent(intent)
        decided = false
        bindClassify(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        openNormallyAndFinish()
    }

    private fun applyCachedColors() {
        val settings = InterceptController.cachedSettings()
        val dark = when (settings.themeStyle) {
            ThemeStyle.DARK, ThemeStyle.HIGH_CONTRAST -> true
            ThemeStyle.LIGHT -> false
            ThemeStyle.FOLLOW_SYSTEM ->
                (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val highContrast = settings.themeStyle == ThemeStyle.HIGH_CONTRAST
        val cardColor = when {
            highContrast -> 0xFF000000.toInt()
            dark -> 0xFF221C18.toInt()
            else -> 0xFFFFFBF4.toInt()
        }
        val textColor = when {
            highContrast -> 0xFFFFFFFF.toInt()
            dark -> 0xFFEDE6DA.toInt()
            else -> 0xFF1C1410.toInt()
        }
        val accent = if (highContrast) 0xFFFFFFFF.toInt() else (settings.accentColor.toInt())
        val card = findViewById<LinearLayout>(R.id.verify_card)
        val drawable = GradientDrawable().apply {
            setColor(cardColor)
            cornerRadius = 28f * resources.displayMetrics.density
        }
        card.background = drawable
        findViewById<TextView>(R.id.verify_label).setTextColor(textColor)
        findViewById<ProgressBar>(R.id.verify_progress).indeterminateTintList =
            android.content.res.ColorStateList.valueOf(accent)
    }

    private fun bindClassify(intent: Intent) {
        job?.cancel()
        val url = NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty())
        activeUrl = url
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val handoffIfNotNews = intent.getBooleanExtra(EXTRA_HANDOFF, true)
        if (url.isBlank()) {
            openNormallyAndFinish()
            return
        }
        val settings = InterceptController.cachedSettings()
        handoffPackage = settings.handoffBrowserPackage.ifBlank { null }
        val pending = InterceptController.pendingClassify(url)
            ?: InterceptController.ensureClassify(url, title, settings)
        job = scope.launch {
            val outcome = runCatching { pending.await() }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    VerifyOutcome(url, news = null)
                }
            applyOutcome(outcome, title, pkg, handoffIfNotNews)
        }
    }

    private fun applyOutcome(
        outcome: VerifyOutcome,
        title: String,
        pkg: String?,
        handoffIfNotNews: Boolean,
    ) {
        if (isFinishing || decided) return
        val url = outcome.url.ifBlank { activeUrl.ifBlank { NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty()) } }
        when (outcome.news) {
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
                    openNormallyAndFinish(mark = false, urlOverride = url)
                } else {
                    decided = true
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
            else -> {
                InterceptController.releaseDocumentHolds(url)
                if (handoffIfNotNews) {
                    openNormallyAndFinish(mark = false, urlOverride = url)
                } else {
                    decided = true
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        }
    }

    private fun openNormallyAndFinish(mark: Boolean = false, urlOverride: String? = null) {
        if (decided) return
        decided = true
        job?.cancel()
        val url = urlOverride ?: activeUrl.ifBlank { NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty()) }
        InterceptController.cancelClassify(url)
        if (mark) InterceptController.markNotNews(url) else InterceptController.releaseDocumentHolds(url)
        if (url.isNotBlank()) {
            BrowserHandoff.open(this, url, handoffPackage)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        if (!decided && !isChangingConfigurations) {
            val url = activeUrl.ifBlank { NewsHosts.unwrapUrl(intent.getStringExtra(EXTRA_URL).orEmpty()) }
            if (url.isNotBlank()) {
                InterceptController.cancelClassify(url)
                InterceptController.releaseDocumentHolds(url)
            }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PACKAGE = "pkg"
        const val EXTRA_HANDOFF = "handoff"
    }
}
