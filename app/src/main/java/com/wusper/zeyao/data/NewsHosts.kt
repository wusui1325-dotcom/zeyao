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

    /**
     * v1 policy: intercept only when the host is on the list.
     * Path keywords are a weak extra signal used to skip obvious non-articles
     * (bare homepages still count; static assets do not).
     */
    fun shouldInterceptUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host ?: return false
        if (!isNewsHost(host)) return false
        if (isProbablyAsset(url)) return false
        return true
    }

    fun hostOf(url: String): String {
        return runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
    }
}
