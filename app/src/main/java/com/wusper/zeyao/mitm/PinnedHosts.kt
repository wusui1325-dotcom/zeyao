package com.wusper.zeyao.mitm

/**
 * Hosts that often pin certificates or belong to the system account/update
 * path. Traffic to these is forwarded without decryption even when the
 * toggle is on, so the device does not break sign-in or Play services.
 */
object PinnedHosts {
    private val exact = setOf(
        "accounts.google.com",
        "play.google.com",
        "android.googleapis.com",
        "mtalk.google.com",
        "www.googleapis.com",
        "oauthaccountmanager.googleapis.com",
        "connectivitycheck.gstatic.com",
        "clients3.google.com",
        "login.microsoftonline.com",
        "login.live.com",
        "login.windows.net",
    )

    private val suffixes = listOf(
        ".gvt1.com",
        ".gvt2.com",
        ".gvt3.com",
        ".ggpht.com",
        ".googleusercontent.com",
        ".apple.com",
        ".icloud.com",
        ".icloud-content.com",
        ".mzstatic.com",
        ".apple-cloudkit.com",
        ".windows.com",
        ".microsoftonline.com",
        ".office.com",
        ".live.com",
        ".samsung.com",
        ".samsungcloud.com",
        ".hicloud.com",
        ".gstatic.com",
    )

    fun shouldSkip(host: String): Boolean {
        val h = host.trim().lowercase().trimEnd('.')
        if (h.isBlank()) return true
        if (h in exact) return true
        if (h.startsWith("ocsp.") || h.startsWith("crl.") || h.startsWith("pki.")) return true
        return suffixes.any { h == it.removePrefix(".") || h.endsWith(it) }
    }
}
