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
     * Start a 擇要 session only for a real article URL on a listed news host.
     * Bare homepages, section fronts, assets, and account/search pages are skipped.
     */
    fun shouldInterceptUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (!isNewsHost(host)) return false
        if (isProbablyAsset(url)) return false
        return isLikelyArticle(uri)
    }

    fun isLikelyArticle(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return isLikelyArticle(uri)
    }

    fun isLikelyArticle(uri: Uri): Boolean {
        val path = uri.path.orEmpty().trimEnd('/')
        if (path.isEmpty() || path == "/") return false
        val lower = path.lowercase(Locale.ROOT)
        if (SKIP_PATH_PREFIXES.any { lower == it || lower.startsWith("$it/") || lower.startsWith(it) && it.endsWith("/") }) {
            // allow if the skipped prefix is only a short start of a longer article path? keep skip.
            if (SKIP_PATH_PREFIXES.any { lower == it.trimEnd('/') || lower.startsWith(it.trimEnd('/') + "/") }) return false
        }
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
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return url.lowercase(Locale.ROOT)
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
        // Treat a redirect that only appends a slug after a shared prefix as the same article.
        val shorter = if (ca.length <= cb.length) ca else cb
        val longer = if (ca.length <= cb.length) cb else ca
        return longer.startsWith(shorter) && (longer.length == shorter.length || longer[shorter.length] == '/' || longer[shorter.length] == '?')
    }

    fun hostOf(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    }
}
