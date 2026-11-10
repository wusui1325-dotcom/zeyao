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
        val pkg = event.packageName?.toString() ?: return
        if (pkg == BuildConfig.APPLICATION_ID) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        val last = lastScanAt[pkg] ?: 0L
        // Always scan immediately on window change; debounce content changes.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - last < 180) return
        lastScanAt[pkg] = now

        val isKnownBrowser = BrowserPackages.isBrowser(pkg, defaultBrowser)
        val root = rootInActiveWindow ?: event.source
        val url = extractUrl(root, pkg, isKnownBrowser)
        root.takeIf { it != event.source }?.recycleSafely()
        if (url.isNullOrBlank()) return
        if (lastUrlByPackage[pkg] == url) return
        lastUrlByPackage[pkg] = url
        val title = event.text?.joinToString(" ")?.toString()?.trim().orEmpty()
        InterceptController.onUrlDetected(url, title = title, packageName = pkg)
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

    private fun extractUrl(root: AccessibilityNodeInfo?, pkg: String, isBrowser: Boolean): String? {
        if (root == null) return null
        // Prefer known address-bar IDs in browsers (including the system default browser).
        if (isBrowser) {
            for (viewId in BrowserPackages.URL_VIEW_IDS) {
                val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull()
                if (nodes.isNullOrEmpty()) continue
                try {
                    for (node in nodes) {
                        normalizeCandidate(node.urlCandidate())?.let { return it }
                    }
                } finally {
                    nodes.forEach { it.recycleSafely() }
                }
            }
        }
        // Walk a limited depth looking for http(s) or news-host paths.
        val found = ArrayList<String>(2)
        walk(root, 0, 18, found)
        return found.firstOrNull()
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, maxDepth: Int, out: MutableList<String>) {
        if (out.isNotEmpty() || depth > maxDepth) return
        normalizeCandidate(node.urlCandidate())?.let {
            out.add(it)
            return
        }
        // WebView nodes sometimes expose the URL in the content description or hint.
        val className = node.className?.toString().orEmpty()
        if (className.contains("WebView", ignoreCase = true)) {
            normalizeCandidate(node.urlCandidate())?.let {
                out.add(it)
                return
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                walk(child, depth + 1, maxDepth, out)
            } finally {
                child.recycleSafely()
            }
            if (out.isNotEmpty()) return
        }
    }

    private fun AccessibilityNodeInfo.urlCandidate(): String {
        val parts = listOf(text, contentDescription, hintText)
        return parts.mapNotNull { it?.toString()?.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    private fun normalizeCandidate(raw: String): String? {
        if (raw.isBlank() || raw.length < 4) return null
        val trimmed = raw.trim().trimEnd('/')
            .removePrefix("查看 ")
            .removePrefix("檢視 ")
        if (trimmed.startsWith("about:") || trimmed.startsWith("chrome") ||
            trimmed.startsWith("javascript:") || trimmed.startsWith("data:")
        ) {
            return null
        }
        val fromPattern = URL_PATTERN.matcher(trimmed)
        if (fromPattern.find()) {
            val matched = fromPattern.group()
            return if (NewsHosts.shouldInterceptUrl(matched)) matched else null
        }
        // Chrome often shows the URL without a scheme: "www.bbc.com/news/..."
        val maybe = if (trimmed.startsWith("http")) trimmed else "https://$trimmed"
        return if (NewsHosts.shouldInterceptUrl(maybe)) maybe else null
    }

    private fun AccessibilityNodeInfo.recycleSafely() {
        runCatching { recycle() }
    }

    private companion object {
        val URL_PATTERN: Pattern = Pattern.compile(
            "https?://[\\w\\-.:]+(?:/[\\w\\-._~!$&'()*+,;=:@/%?#=]*)?",
            Pattern.CASE_INSENSITIVE,
        )
    }
}
