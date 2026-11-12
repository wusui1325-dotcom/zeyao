package com.wusper.zeyao

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.wusper.zeyao.intercept.InterceptController

/**
 * Shown when intercept-all is on and ask-before-summary is on.
 * 是 = open 擇要. 否 = let the page through.
 */
class AskInterceptActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        setContent {
            AlertDialog(
                onDismissRequest = { decide(false, url) },
                title = { Text("是否擇要？") },
                text = { Text(title.ifBlank { url }) },
                confirmButton = {
                    TextButton(onClick = { decide(true, url, title, pkg) }) { Text("是") }
                },
                dismissButton = {
                    TextButton(onClick = { decide(false, url) }) { Text("否") }
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
