package com.wusper.zeyao.data

import android.net.Uri
import java.util.Locale

/**
 * Built-in news / media hosts for v1 intercept.
 * Matching is by registrable-domain suffix after stripping common subdomains
 * (www / m / amp / mobile / news). Extend [HOSTS] to add outlets.
 *
 * Hosts not on this list are never intercepted.
 */
object NewsHosts {
    /**
     * Registrable domains (no leading www). Keep this list easy to extend.
     */
    val HOSTS: Set<String> = setOf(
        // International
        "bbc.com", "bbc.co.uk", "cnn.com", "reuters.com", "nytimes.com",
        "theguardian.com", "washingtonpost.com", "bloomberg.com", "apnews.com",
        "ap.org", "ft.com", "wsj.com", "economist.com", "time.com", "newsweek.com",
        "npr.org", "abcnews.go.com", "nbcnews.com", "cbsnews.com", "foxnews.com",
        "aljazeera.com", "theatlantic.com", "politico.com", "axios.com",
        "thehill.com", "latimes.com", "usatoday.com", "independent.co.uk",
        "telegraph.co.uk", "thetimes.co.uk", "sky.com", "news.sky.com",
        "thehindu.com", "hindustantimes.com", "indianexpress.com",
        "lemonde.fr", "lefigaro.fr", "spiegel.de", "zeit.de", "elpais.com",
        "abc.net.au", "smh.com.au", "theage.com.au", "news.com.au",
        "cbc.ca", "globalnews.ca", "theglobeandmail.com",
        "nhk.or.jp", "asahi.com", "mainichi.jp", "yomiuri.co.jp", "nikkei.com",
        "koreaherald.com", "koreatimes.co.kr", "joongang.co.kr",
        "channelnewsasia.com", "straitstimes.com", "zaobao.com.sg", "zaobao.com",
        "scmp.com",
        // Hong Kong
        "mingpao.com", "news.mingpao.com", "stheadline.com", "singtao.ca",
        "singtaousa.com", "singtao.com", "orientaldaily.com.hk", "on.cc",
        "theinitium.com", "initiummedia.com", "hk01.com", "rthk.hk", "news.rthk.hk",
        "thestandard.com.hk", "hket.com", "am730.com.hk", "bastillepost.com",
        "inmediahk.net", "wenweipo.com", "takungpao.com", "takungpao.com.hk",
        "hkcd.com.hk", "hk.news.yahoo.com", "news.yahoo.com", "now.com",
        "news.now.com", "tvmost.com.hk", "passiontimes.hk", "hkej.com",
        "headline.com.hk", "dbn.com.hk", "crntt.com",
        // Taiwan
        "cna.com.tw", "udn.com", "udn.com.tw", "chinatimes.com", "ltn.com.tw",
        "libertytimes.com.tw", "ftvnews.com.tw", "cts.com.tw", "ettoday.net",
        "setn.com", "tvbs.com.tw", "news.tvbs.com.tw", "storm.mg",
        "twreporter.org", "newtalk.tw", "taiwannews.com.tw", "taipeitimes.com",
        "focustaiwan.tw", "thenewslens.com", "upmedia.mg", "mirrormedia.mg",
        "bnext.com.tw", "tw.news.yahoo.com", "pts.org.tw", "news.pts.org.tw",
        "settv.com.tw", "ctitv.com.tw", "ebc.net.tw", "nextapple.com",
        "worldjournal.com", "rti.org.tw", "cnyes.com", "wealth.com.tw",
        // Macau / others in the region
        "exmoo.com", "macaodaily.com", "tdm.com.mo",
        "thepaper.cn", "caixin.com", "zaobao.sg",
        "news.google.com", "news.google.com.hk", "news.google.com.tw",
        "sina.com.cn", "news.sina.com.cn", "qq.com", "news.qq.com",
        "163.com", "news.163.com", "sohu.com", "news.sohu.com",
        "ifeng.com", "news.ifeng.com", "people.com.cn", "xinhuanet.com",
        "cctv.com", "news.cctv.com", "chinanews.com", "chinanews.com.cn",
        "stcn.com", "yicai.com", "cls.cn", "jiemian.com",
    )

    private val PREFIXES = listOf("www.", "m.", "amp.", "mobile.", "news.", "edition.", "legacy.")

    private val ASSET_EXTENSIONS = setOf(
        "css", "js", "mjs", "map", "png", "jpg", "jpeg", "gif", "webp", "svg",
        "ico", "woff", "woff2", "ttf", "otf", "mp4", "mp3", "webm", "m3u8",
        "json", "xml", "rss",
    )

    private val ARTICLE_HINTS = listOf(
        "/news/", "/article/", "/articles/", "/story/", "/stories/",
        "/world/", "/politics/", "/business/", "/opinion/", "/breaking/",
        "/sport/", "/sports/", "/tech/", "/science/", "/health/",
        "/instant", "/realtime", "/breakingnews",
        "新聞", "即時", "要聞", "國際", "政治", "財經",
        "article", "storyid", "news_id", "newsid",
    )

    fun canonicalHost(host: String): String {
        var h = host.lowercase(Locale.ROOT).trim().trimEnd('.')
        if (h.startsWith("[") && h.endsWith("]")) return h
        var changed: Boolean
        do {
            changed = false
            for (p in PREFIXES) {
                if (h.startsWith(p) && h.length > p.length) {
                    // Keep "news." only when the remainder itself is not a known host
                    // and the prefixed form is explicitly listed (e.g. news.yahoo.com).
                    val stripped = h.removePrefix(p)
                    if (p == "news." && (h in HOSTS || HOSTS.any { h == it || h.endsWith(".$it") })) {
                        break
                    }
                    h = stripped
                    changed = true
                    break
                }
            }
        } while (changed)
        return h
    }

    fun isNewsHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val raw = host.lowercase(Locale.ROOT).trim().trimEnd('.')
        if (matches(raw)) return true
        val canonical = canonicalHost(raw)
        return matches(canonical) || matches(raw)
    }

    private fun matches(h: String): Boolean {
        if (h in HOSTS) return true
        return HOSTS.any { h == it || h.endsWith(".$it") }
    }

    fun isProbablyAsset(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path.orEmpty() }.getOrDefault("")
        val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return ext in ASSET_EXTENSIONS
    }

    fun hasArticleHint(url: String): Boolean {
        val u = url.lowercase(Locale.ROOT)
        return ARTICLE_HINTS.any { it in u }
    }

    private val SKIP_PATH_PREFIXES = listOf(
        "/login", "/signin", "/signup", "/register", "/account", "/accounts",
        "/search", "/subscribe", "/subscription", "/privacy", "/terms",
        "/cookie", "/gdpr", "/settings", "/preferences", "/profile",
        "/user/", "/my/", "/cart", "/checkout", "/help", "/about",
        "/contact", "/advertise", "/jobs", "/careers", "/video/player",
    )

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "fbclid", "gclid", "gbraid", "wbraid", "mc_cid", "mc_eid",
        "igshid", "si", "spm", "ref", "ref_src", "ref_url", "ncid",
        "ocid", "yclid", "msclkid", "sref",
    )

    /**
     * Unwrap one or two common redirectors (Google, Facebook, etc.) so the
     * real article URL can be judged and summarised.
     */
    fun unwrapUrl(url: String): String {
        var current = url.trim()
        repeat(3) {
            val uri = runCatching { Uri.parse(current) }.getOrNull() ?: return current
            val host = uri.host.orEmpty().lowercase(Locale.ROOT)
            val path = uri.path.orEmpty().lowercase(Locale.ROOT)
            val next = when {
                (host == "google.com" || host.endsWith(".google.com")) &&
                    (path == "/url" || path == "/url/" || path == "/news/url") ->
                    uri.getQueryParameter("q") ?: uri.getQueryParameter("url") ?: uri.getQueryParameter("u")
                host == "share.google" || host.endsWith(".share.google") ->
                    uri.getQueryParameter("url") ?: uri.getQueryParameter("q")
                host.endsWith("facebook.com") && (path.contains("/l.php") || path == "/l.php") ->
                    uri.getQueryParameter("u")
                host.endsWith("instagram.com") && path.contains("/l.php") ->
                    uri.getQueryParameter("u")
                host == "href.li" ->
                    uri.query?.let { q -> if (q.startsWith("http")) q else null }
                else -> null
            }
            if (next.isNullOrBlank() || next == current) return current
            current = next
        }
        return current
    }

    /**
     * Start a 擇要 session only for a URL on a listed news host.
     * Bare homepages, assets, and account/search pages are skipped.
     *
     * [loose] is for the default-browser path: the Intent URI is the page the
     * user actually asked to open, so any non-home news URL is enough.
     */
    fun shouldInterceptUrl(url: String, loose: Boolean = false): Boolean {
        val resolved = unwrapUrl(url)
        val uri = runCatching { Uri.parse(resolved) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (!isNewsHost(host)) return false
        if (isProbablyAsset(resolved)) return false
        if (isSkipPath(uri)) return false
        if (isBareHostUrl(resolved)) return false
        if (loose) return true
        return isLikelyArticle(uri)
    }

    fun isSkipPath(uri: Uri): Boolean {
        val lower = uri.path.orEmpty().lowercase(Locale.ROOT)
        if (lower.isEmpty() || lower == "/") return false
        return SKIP_PATH_PREFIXES.any {
            val prefix = it.trimEnd('/')
            lower == prefix || lower.startsWith("$prefix/")
        }
    }

    /**
     * Any http(s) page that can be shown in the verify float — not limited
     * to the built-in news host list. Assets and non-web schemes are skipped.
     */
    fun isClassifiableUrl(url: String): Boolean {
        val resolved = unwrapUrl(url)
        val uri = runCatching { Uri.parse(resolved) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrBlank()) return false
        if (isProbablyAsset(resolved)) return false
        return true
    }

    fun isLikelyArticle(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return isLikelyArticle(uri)
    }

    fun isLikelyArticle(uri: Uri): Boolean {
        if (isSkipPath(uri)) return false
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isEmpty() || path == "/") return false
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return false
        val joined = uri.toString().lowercase(Locale.ROOT)
        if (hasArticleHint(joined)) return true
        val last = segments.last()
        val looksLikeSlug = last.length >= 8 || last.any { it.isDigit() }
        // Single short section path such as /news or /hk should not intercept.
        if (segments.size == 1 && !looksLikeSlug) return false
        if (segments.size >= 2 && looksLikeSlug) return true
        if (segments.size >= 3) return true
        return looksLikeSlug
    }

    fun isBareHostUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        val path = uri.path.orEmpty().trimEnd('/')
        return path.isEmpty() && uri.query.isNullOrBlank()
    }

    fun canonicalUrl(url: String): String {
        val resolved = unwrapUrl(url)
        val uri = runCatching { Uri.parse(resolved) }.getOrNull() ?: return resolved.lowercase(Locale.ROOT)
        var host = uri.host.orEmpty().lowercase(Locale.ROOT)
        if (host.startsWith("www.")) host = host.removePrefix("www.")
        var path = uri.path.orEmpty().ifEmpty { "/" }
        if (path.length > 1) path = path.trimEnd('/')
        val kept = uri.queryParameterNames
            .filter { it.lowercase(Locale.ROOT) !in TRACKING_PARAMS }
            .sorted()
            .mapNotNull { name ->
                val value = uri.getQueryParameter(name) ?: return@mapNotNull null
                if (value.isBlank()) null else "$name=$value"
            }
        return buildString {
            append("https://")
            append(host)
            append(path)
            if (kept.isNotEmpty()) {
                append('?')
                append(kept.joinToString("&"))
            }
        }
    }

    fun sameArticle(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        val ca = canonicalUrl(a)
        val cb = canonicalUrl(b)
        if (ca == cb) return true
        // Stronger slug / trailing-segment match (item 8)
        val shorter = if (ca.length <= cb.length) ca else cb
        val longer = if (ca.length <= cb.length) cb else ca
        if (longer.startsWith(shorter) && (longer.length == shorter.length || longer[shorter.length] == '/' || longer[shorter.length] == '?')) {
            return true
        }
        // Also treat differing trailing numeric or date slugs as same article when host+path prefix match
        val hostPathA = ca.substringBefore('?').substringBeforeLast('/')
        val hostPathB = cb.substringBefore('?').substringBeforeLast('/')
        return hostPathA.length > 12 && hostPathA == hostPathB
    }

    fun hostOf(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    }
}
