package com.wusper.zeyao.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object DefaultBrowser {
    fun packageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val flags = PackageManager.MATCH_DEFAULT_ONLY
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(flags.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, flags)
        }
        val pkg = resolved?.activityInfo?.packageName
        return pkg?.takeIf { it.isNotBlank() && it != "android" }
    }
}

object BrowserPackages {
    val KNOWN: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.google.android.apps.chrome",
        "com.android.browser",
        "com.android.vending", // ignore later if not a browser url
        "com.sec.android.app.sbrowser",
        "com.samsung.android.app.sbrowseredge",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "org.mozilla.fenix",
        "org.mozilla.fennec_fdroid",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.huawei.browser",
        "com.mi.globalbrowser",
        "com.coloros.browser",
        "com.heytap.browser",
        "com.uc.browser",
        "com.uc.browser.en",
        "com.UCMobile",
        "mark.via.gp",
        "org.chromium.chrome",
        "com.ecosia.android",
        "com.atom.browser",
        "com.qihoo.browser",
    )

    val URL_VIEW_IDS: List<String> = listOf(
        "com.android.chrome:id/url_bar",
        "com.android.chrome:id/search_box_text",
        "com.chrome.beta:id/url_bar",
        "com.chrome.dev:id/url_bar",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.sec.android.app.sbrowser:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.firefox:id/url_bar_title",
        "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx:id/url_bar",
        "com.brave.browser:id/url_bar",
        "com.opera.browser:id/url_field",
        "com.duckduckgo.mobile.android:id/omniBarTextInput",
        "com.huawei.browser:id/url_bar",
        "com.mi.globalbrowser:id/url",
        "com.android.browser:id/url",
        "com.google.android.googlequicksearchbox:id/googleapp_search_box",
    )

    fun isBrowser(packageName: String?, defaultBrowser: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        if (packageName in KNOWN) return true
        if (defaultBrowser != null && packageName == defaultBrowser) return true
        return false
    }
}
