package com.wusper.zeyao.mitm

import com.wusper.zeyao.data.NewsHosts
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Peeks at decrypted HTTP/1.1 traffic to recover the exact request URL and,
 * when the response is HTML, a copy of the body for summarising.
 */
class HttpInspect(
    private val onDocumentUrl: (url: String) -> Boolean,
) {
    private val reqBuf = ByteArrayOutputStream()
    private var requestHeadersDone = false
    private var documentUrl: String? = null
    private var holdingResponse = false
    private val held = ByteArrayOutputStream()
    private val respHead = ByteArrayOutputStream()
    private var responseHeadersDone = false
    private var html = false
    private var encoding: String? = null
    private val htmlBody = ByteArrayOutputStream()
    private var released = false

    @Volatile
    var capturedUrl: String? = null
        private set

    fun onClientPlain(data: ByteArray): ByteArray {
        if (requestHeadersDone) return data
        reqBuf.write(data)
        val bytes = reqBuf.toByteArray()
        val split = indexOfHeaderEnd(bytes) ?: return data
        requestHeadersDone = true
        val header = String(bytes, 0, split, StandardCharsets.ISO_8859_1)
        val url = parseRequestUrl(header)
        if (url != null && isDocumentRequest(header, url)) {
            documentUrl = url
            capturedUrl = url
            holdingResponse = onDocumentUrl(url)
        }
        return data
    }

    /**
     * @return bytes that should go to the browser now. Null means hold.
     */
    fun onServerPlain(data: ByteArray): ByteArray? {
        if (holdingResponse && !released) {
            held.write(data)
            captureIfPossible(data)
            return null
        }
        captureIfPossible(data)
        return data
    }

    fun releaseHeld(): ByteArray {
        released = true
        holdingResponse = false
        val out = held.toByteArray()
        held.reset()
        return out
    }

    fun isHolding(): Boolean = holdingResponse && !released

    private fun captureIfPossible(data: ByteArray) {
        val url = documentUrl ?: return
        if (!responseHeadersDone) {
            respHead.write(data)
            val bytes = respHead.toByteArray()
            val split = indexOfHeaderEnd(bytes) ?: return
            responseHeadersDone = true
            val header = String(bytes, 0, split, StandardCharsets.ISO_8859_1)
            html = isHtmlResponse(header)
            encoding = headerValue(header, "content-encoding")?.lowercase(Locale.ROOT)
            val bodyStart = split + 4
            if (html && bodyStart < bytes.size) {
                htmlBody.write(bytes, bodyStart, bytes.size - bodyStart)
                maybeStore(url)
            }
            return
        }
        if (html && htmlBody.size() < MAX_HTML) {
            val room = MAX_HTML - htmlBody.size()
            if (data.size <= room) htmlBody.write(data) else htmlBody.write(data, 0, room)
            maybeStore(url)
        }
    }

    private fun maybeStore(url: String) {
        if (htmlBody.size() < 256) return
        val raw = htmlBody.toByteArray()
        val decoded = decodeBody(raw, encoding) ?: return
        val text = String(decoded, charsetOf(decoded))
        if (text.contains("<html", ignoreCase = true) || text.contains("<body", ignoreCase = true) ||
            text.contains("<article", ignoreCase = true)
        ) {
            PageCapture.put(url, text)
        }
    }

    companion object {
        private const val MAX_HTML = 512_000
        private val H2_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)

        fun looksLikeHttp2(data: ByteArray): Boolean {
            if (data.size < H2_PREFACE.size) return false
            return H2_PREFACE.indices.all { data[it] == H2_PREFACE[it] }
        }

        private fun indexOfHeaderEnd(bytes: ByteArray): Int? {
            if (bytes.size < 4) return null
            for (i in 0..bytes.size - 4) {
                if (bytes[i] == 0x0D.toByte() && bytes[i + 1] == 0x0A.toByte() &&
                    bytes[i + 2] == 0x0D.toByte() && bytes[i + 3] == 0x0A.toByte()
                ) return i
            }
            return null
        }

        private fun parseRequestUrl(header: String): String? {
            val first = header.lineSequence().firstOrNull()?.trim() ?: return null
            val parts = first.split(' ')
            if (parts.size < 2) return null
            val method = parts[0].uppercase(Locale.ROOT)
            if (method != "GET" && method != "HEAD" && method != "POST") return null
            val target = parts[1]
            if (target.startsWith("http://") || target.startsWith("https://")) {
                return NewsHosts.unwrapUrl(target)
            }
            val host = headerValue(header, "host") ?: return null
            val path = if (target.startsWith("/")) target else "/$target"
            return NewsHosts.unwrapUrl("https://$host$path")
        }

        private fun isDocumentRequest(header: String, url: String): Boolean {
            if (NewsHosts.isProbablyAsset(url) || NewsHosts.isSkipPath(android.net.Uri.parse(url))) return false
            val method = header.lineSequence().firstOrNull()?.substringBefore(' ')?.uppercase(Locale.ROOT)
            if (method != "GET" && method != "HEAD") return false
            val accept = headerValue(header, "accept").orEmpty().lowercase(Locale.ROOT)
            if (accept.isNotBlank() && "text/html" !in accept && "*/*" !in accept && "application/xhtml" !in accept) {
                return false
            }
            return NewsHosts.isClassifiableUrl(url)
        }

        private fun isHtmlResponse(header: String): Boolean {
            val first = header.lineSequence().firstOrNull().orEmpty()
            if (!first.startsWith("HTTP/")) return false
            val ct = headerValue(header, "content-type").orEmpty().lowercase(Locale.ROOT)
            return "text/html" in ct || "application/xhtml" in ct || ct.isBlank()
        }

        private fun headerValue(header: String, name: String): String? {
            val prefix = "$name:"
            for (line in header.lineSequence()) {
                if (line.length >= prefix.length &&
                    line.substring(0, prefix.length).equals(prefix, ignoreCase = true)
                ) {
                    return line.substring(prefix.length).trim()
                }
            }
            return null
        }

        private fun decodeBody(raw: ByteArray, encoding: String?): ByteArray? = try {
            when (encoding) {
                null, "", "identity" -> raw
                "gzip", "x-gzip" -> GZIPInputStream(ByteArrayInputStream(raw)).use { it.readBytes() }
                "deflate" -> InflaterInputStream(ByteArrayInputStream(raw), Inflater(true)).use { it.readBytes() }
                else -> raw
            }
        } catch (_: Exception) {
            // Incomplete gzip is common while the body is still arriving.
            null
        }

        private fun charsetOf(bytes: ByteArray): java.nio.charset.Charset {
            return StandardCharsets.UTF_8
        }
    }
}
