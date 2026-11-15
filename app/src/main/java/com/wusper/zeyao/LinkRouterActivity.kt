package com.wusper.zeyao

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.wusper.zeyao.intercept.InterceptController

/**
 * Default-browser entry: every web URL first hits the small「驗證中」float.
 * The chosen vendor's flash model decides whether to open the 擇要 page
 * or hand the link to the real browser.
 *
 * This activity stays off the critical path: it hands the URL to
 * [InterceptController] and finishes immediately, without waiting on
 * DataStore or redirect HEADs.
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
            overridePendingTransition(0, 0)
            return
        }
        InterceptController.onExternalLink(raw, callingPackage, startFrom = this)
        finish()
        overridePendingTransition(0, 0)
    }
}
