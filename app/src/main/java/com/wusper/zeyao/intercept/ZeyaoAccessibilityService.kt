package com.wusper.zeyao.intercept

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wusper.zeyao.BuildConfig
import com.wusper.zeyao.ZeyaoApp
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
        // Refresh the consume flag once on connect.
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                InterceptController.clickConsume =
                    ZeyaoApp.instance.settings.current().clickInterceptConsume
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (InterceptController.appUiVisible) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == BuildConfig.APPLICATION_ID) return

        // Click-intercept path (floating window armed)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            InterceptController.clickArmed
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
        val source = event.source ?: return
        try {
            val url = findUrlInNode(source) ?: return
            InterceptController.clickArmed = false
            InterceptController.onUrlDetected(url, title = "", packageName = pkg)
            if (InterceptController.clickConsume) {
                // Best-effort: go back so the original navigation is less likely to proceed.
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } finally {
            source.recycleSafely()
        }
    }

    private fun findUrlInNode(node: AccessibilityNodeInfo): String? {
        // Prefer the clicked node itself, then walk parents and children a bit.
        val candidates = mutableListOf<String>()
        fun collect(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 4) return
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
        while (parent != null && hops < 3) {
            parent.urlCandidate().let { if (it.isNotBlank()) candidates += it }
            val next = parent.parent
            parent.recycleSafely()
            parent = next
            hops++
        }
        return candidates.firstNotNullOfOrNull { normalizeAddress(it) }
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
    }
}
