package com.wusper.zeyao

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.wusper.zeyao.data.AppLocale
import com.wusper.zeyao.intercept.InterceptController

/**
 * Shown when intercept-all is on and ask-before-summary is on.
 * Yes = open 擇要. No = let the page through.
 */
class AskInterceptActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        setContent {
            AlertDialog(
                onDismissRequest = { decide(false, url) },
                title = { Text(stringResource(R.string.ask_dialog_title)) },
                text = { Text(title.ifBlank { url }) },
                confirmButton = {
                    TextButton(onClick = { decide(true, url, title, pkg) }) {
                        Text(stringResource(R.string.ask_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { decide(false, url) }) {
                        Text(stringResource(R.string.ask_no))
                    }
                },
            )
        }
    }

    private fun decide(yes: Boolean, url: String, title: String = "", pkg: String? = null) {
        if (yes) {
            InterceptController.startOrRefine(url, title, pkg, this)
        } else {
            InterceptController.allowThrough(url)
        }
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PACKAGE = "source_package"
    }
}
