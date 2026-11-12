package com.wusper.zeyao

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.NewsHosts
import com.wusper.zeyao.intercept.InterceptController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Default-browser entry: every web URL first hits the small「驗證中」float.
 * The chosen vendor's flash model decides whether to open the 擇要 page
 * or hand the link to the real browser.
 */
class LinkRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) handle(intent)
    }

    private fun handle(intent: Intent) {
        val raw = intent.data?.toString().orEmpty()
        if (raw.isBlank()) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val app = application as ZeyaoApp
            val settings = withContext(Dispatchers.IO) { app.settings.current() }
            val url = NewsHosts.unwrapUrl(raw)
            val armed = app.settings.isInterceptArmed(settings)
            val cooled = InterceptController.isUrlCooled(url)
            val intercept = armed && !cooled && (settings.interceptAllPages || NewsHosts.isClassifiableUrl(url))
            if (intercept) {
                if (settings.interceptAllPages && settings.askBeforeSummary) {
                    startActivity(
                        Intent(this@LinkRouterActivity, AskInterceptActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
                            putExtra(AskInterceptActivity.EXTRA_URL, url)
                            putExtra(AskInterceptActivity.EXTRA_TITLE, "")
                            putExtra(AskInterceptActivity.EXTRA_PACKAGE, callingPackage)
                        },
                    )
                } else if (settings.interceptAllPages) {
                    InterceptController.startOrRefine(url, title = "", packageName = callingPackage, startFrom = this@LinkRouterActivity)
                } else {
                    startActivity(
                        Intent(this@LinkRouterActivity, VerifyActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(VerifyActivity.EXTRA_URL, url)
                            putExtra(VerifyActivity.EXTRA_TITLE, "")
                            putExtra(VerifyActivity.EXTRA_PACKAGE, callingPackage)
                            putExtra(VerifyActivity.EXTRA_HANDOFF, true)
                        },
                    )
                }
                finish()
                overridePendingTransition(0, 0)
            } else {
                val ok = BrowserHandoff.open(app, url, settings.handoffBrowserPackage)
                if (!ok) {
                    startActivity(
                        Intent(this@LinkRouterActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                finish()
            }
        }
    }
}
