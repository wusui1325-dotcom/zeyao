package com.wusper.zeyao.intercept

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wusper.zeyao.BuildConfig
import com.wusper.zeyao.data.BrowserPackages
import com.wusper.zeyao.data.DefaultBrowser
import com.wusper.zeyao.data.NewsHosts
import java.util.regex.Pattern

/**
 * Only reads the browser address bar. Never walks the page for the first
 * news link — that was picking related articles, records in 擇要, and
 * random on-screen URLs.
 */
class ZeyaoAccessibilityService : AccessibilityService() {

    private val lastUrlByPackage = mutableMapOf<String, String>()
    private val lastScanAt = mutableMapOf<String, Long>()
    @Volatile private var defaultBrowser: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        InterceptController.accessibility = this
        defaultBrowser = runCatching { DefaultBrowser.packageName(this) }.getOrNull()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (InterceptController.appUiVisible) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == BuildConfig.APPLICATION_ID) return

        // Click-intercept path (floating window armed)
        if (InterceptController.clickArmed &&
            (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        ) {
            handleArmedClick(event, pkg)
            return
        }

        if (!BrowserPackages.isBrowser(pkg, defaultBrowser)) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            return
        }
        // ... existing address-bar logic continues below
        val now = SystemClock.elapsedRealtime()
        val last = lastScanAt[pkg] ?: 0L
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - last < 250) return
        lastScanAt[pkg] = now

        val root = rootInActiveWindow ?: return
        try {
            val rootPkg = root.packageName?.toString().orEmpty()
            if (rootPkg == BuildConfig.APPLICATION_ID) return
            if (rootPkg.isNotBlank() && rootPkg != pkg) return
            val url = extractAddressBarUrl(root, pkg) ?: return
            if (lastUrlByPackage[pkg] == url) return
            lastUrlByPackage[pkg] = url
            InterceptController.onUrlDetected(url, title = "", packageName = pkg)
        } finally {
            root.recycleSafely()
        }
    }

    private fun handleArmedClick(event: AccessibilityEvent, pkg: String) {
        val source = event.source
        var url: String? = null
        if (source != null) {
            try {
                url = findUrlInNode(source)
            } finally {
                source.recycleSafely()
            }
        }
        if (url == null) {
            // WeChat and many apps put the real URL only in a sibling or in the window text.
            val root = rootInActiveWindow
            if (root != null) {
                try {
                    url = findUrlInNode(root)
                } finally {
                    root.recycleSafely()
                }
            }
        }
        if (url == null && isWeChatOrDiscover(pkg)) {
            url = readClipboardUrl()
        }
        if (url == null) {
            val hint = when {
                pkg == "com.tencent.mm" -> "微信這次節點沒有完整網址，可試長按複製連結後再點一次懸浮窗"
                pkg == "com.google.android.googlequicksearchbox" -> "發現頁這次卡片沒有露出網址"
                else -> "這次點擊的節點裡沒有完整網址"
            }
            android.widget.Toast.makeText(this, hint, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        InterceptController.clickArmed = false
        InterceptController.onUrlDetected(url, title = "", packageName = pkg)
        if (InterceptController.clickConsume) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun isWeChatOrDiscover(pkg: String): Boolean =
        pkg == "com.tencent.mm" || pkg == "com.google.android.googlequicksearchbox"

    private fun readClipboardUrl(): String? {
        return runCatching {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                ?: return null
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
            extractUrl(text)
        }.getOrNull()
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo): String? {
        val candidates = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 6) return
            n.urlCandidate().let { if (it.isNotBlank()) candidates += it }
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { child ->
                    collect(child, depth + 1)
                    child.recycleSafely()
                }
            }
        }
        collect(node, 0)
        var parent = node.parent
        var hops = 0
        while (parent != null && hops < 4) {
            parent.urlCandidate().let { if (it.isNotBlank()) candidates += it }
            val next = parent.parent
            parent.recycleSafely()
            parent = next
            hops++
        }
        return candidates.firstNotNullOfOrNull { extractUrl(it) }
    }

    private fun extractUrl(raw: String): String? {
        if (raw.isBlank()) return null
        // Full-string normalize first (address bar style).
        normalizeAddress(raw)?.let { return it }
        // Otherwise search for a URL inside longer text (WeChat title + link, etc.).
        val matcher = URL_IN_TEXT.matcher(raw)
        while (matcher.find()) {
            val found = matcher.group() ?: continue
            val withScheme = if (found.startsWith("http", ignoreCase = true)) found else "https://$found"
            if (NewsHosts.isClassifiableUrl(withScheme)) return withScheme
        }
        return null
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (InterceptController.accessibility === this) {
            InterceptController.accessibility = null
        }
        super.onDestroy()
    }

    fun tryGoBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun extractAddressBarUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val ids = BrowserPackages.urlViewIdsFor(pkg)
        for (viewId in ids) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull()
            if (nodes.isNullOrEmpty()) continue
            try {
                for (node in nodes) {
                    normalizeAddress(node.urlCandidate())?.let { return it }
                }
            } finally {
                nodes.forEach { it.recycleSafely() }
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.urlCandidate(): String {
        val parts = listOf(text, contentDescription, hintText)
        return parts.mapNotNull { it?.toString()?.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun normalizeAddress(raw: String): String? {
        if (raw.isBlank() || raw.length < 8) return null
        var trimmed = raw.trim()
            .removePrefix("查看 ")
            .removePrefix("檢視 ")
            .removePrefix("https://")
            .removePrefix("http://")
        if (trimmed.startsWith("about:") || trimmed.startsWith("chrome") ||
            trimmed.startsWith("javascript:") || trimmed.startsWith("data:") ||
            trimmed.startsWith("file:") || trimmed.contains(' ')
        ) {
            return null
        }
        // Chrome may show only the host while loading, or a search query without dots.
        val matcher = HOST_PATH.matcher(trimmed)
        if (!matcher.matches()) return null
        val maybe = "https://" + trimmed.trimEnd('/')
        return if (NewsHosts.isClassifiableUrl(maybe)) maybe else null
    }

    private fun AccessibilityNodeInfo.recycleSafely() {
        runCatching { recycle() }
    }

    private companion object {
        val HOST_PATH: Pattern = Pattern.compile(
            "^[\\w.-]+\\.[a-zA-Z]{2,}(?::\\d+)?(?:/[\\w\\-._~!$&'()*+,;=:@/%?#=]*)?$",
            Pattern.CASE_INSENSITIVE,
        )
        val URL_IN_TEXT: Pattern = Pattern.compile(
            "(?:https?://)?(?:[\\w.-]+\\.)?(?:mp\\.weixin\\.qq\\.com|news\\.google\\.com|weixin\\.qq\\.com|[\\w.-]+\\.[a-zA-Z]{2,})(?::\\d+)?(?:/[\\w\\-._~!$&'()*+,;=:@/%?#=]*)?",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
