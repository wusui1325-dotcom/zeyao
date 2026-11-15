package com.wusper.zeyao

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.wusper.zeyao.data.AppLocale
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.intercept.InterceptController
import com.wusper.zeyao.intercept.InterceptSession
import com.wusper.zeyao.ui.intercept.InterceptScreen
import com.wusper.zeyao.ui.theme.ZeyaoTheme
import kotlinx.coroutines.launch

class InterceptActivity : ComponentActivity() {
    private var sessionId: String = ""

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        bindContent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        bindContent()
    }

    private fun bindContent() {
        val extrasUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        val extrasTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val extrasPkg = intent.getStringExtra(EXTRA_PACKAGE)
        val fallback = InterceptSession(
            id = sessionId,
            url = extrasUrl,
            title = extrasTitle,
            host = NewsHosts.hostOf(extrasUrl),
            sourcePackage = extrasPkg,
        )
        setContent {
            val app = ZeyaoApp.instance
            val scope = rememberCoroutineScope()
            val initial = remember { InterceptController.cachedSettings() }
            val settings by app.settings.settings.collectAsState(initial = initial)
            val sessions by InterceptController.sessions.collectAsState()
            val session = sessions[sessionId] ?: InterceptController.peek(sessionId) ?: fallback

            ZeyaoTheme(
                themeStyle = settings.themeStyle,
                typographyStyle = settings.typographyStyle,
                accent = Color(settings.accentColor),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    BackHandler { decideBack(session.id) }
                    InterceptScreen(
                        session = session,
                        buttonPosition = settings.enterButtonPosition,
                        searchEnabled = settings.searchNewsEnabled,
                        fontScale = settings.summaryFontScale,
                        onBack = { decideBack(session.id) },
                        onEnter = { decideEnter(session.id) },
                        onShorter = {
                            InterceptController.regenerate(session.id, length = com.wusper.zeyao.data.SummaryLength.SHORT)
                        },
                        onLonger = {
                            InterceptController.regenerate(session.id, length = com.wusper.zeyao.data.SummaryLength.LONG)
                        },
                        onRetry = {
                            InterceptController.regenerate(session.id)
                        },
                        onToggleSearch = { enabled ->
                            scope.launch {
                                app.settings.update { it.copy(searchNewsEnabled = enabled) }
                            }
                        },
                        onCopy = { copySummary(session) },
                        onSkipSite = { decideSkip(session.id) },
                        onFontSmaller = {
                            scope.launch { adjustFont(-0.15f) }
                        },
                        onFontLarger = {
                            scope.launch { adjustFont(0.15f) }
                        },
                    )
                }
            }
        }
    }

    private fun decideBack(id: String) {
        InterceptController.onBack(id)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun decideEnter(id: String) {
        InterceptController.onEnter(id)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun decideSkip(id: String) {
        InterceptController.skipSiteAndEnter(id)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun copySummary(session: InterceptSession) {
        val text = session.summary.ifBlank { session.url }
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private suspend fun adjustFont(delta: Float) {
        ZeyaoApp.instance.settings.update {
            it.copy(summaryFontScale = (it.summaryFontScale + delta).coerceIn(0.85f, 1.6f))
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PACKAGE = "source_package"
    }
}
