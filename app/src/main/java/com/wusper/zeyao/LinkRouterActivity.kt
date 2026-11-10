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
 * Default-browser entry: news URLs open the 擇要 page; everything else
 * (and 進入) is handed to the user-chosen real browser.
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
        // Stay on the main thread after settings load so InterceptActivity
        // is started from this still-visible activity, not after we finish.
        CoroutineScope(Dispatchers.Main.immediate).launch {
            val app = application as ZeyaoApp
            val settings = withContext(Dispatchers.IO) { app.settings.current() }
            val url = NewsHosts.unwrapUrl(raw)
            val armed = app.settings.isInterceptArmed(settings)
            val news = NewsHosts.shouldInterceptUrl(url, loose = true)
            val cooled = InterceptController.isUrlCooled(url)
            if (armed && news && !cooled) {
                InterceptController.startOrRefine(
                    url,
                    title = "",
                    packageName = callingPackage,
                    startFrom = this@LinkRouterActivity,
                )
                finish()
            } else {
                val preferred = settings.handoffBrowserPackage
                val ok = BrowserHandoff.open(app, url, preferred)
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
