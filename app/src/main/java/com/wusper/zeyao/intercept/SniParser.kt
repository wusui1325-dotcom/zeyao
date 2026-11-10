package com.wusper.zeyao.intercept

import java.nio.charset.StandardCharsets

object SniParser {
    /**
     * Parse a TLS ClientHello (possibly starting at a TLS record header)
     * and return the SNI hostname, or null if not yet complete / not TLS.
     */
    fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): String? {
        if (length < 6) return null
        if (data[offset].toInt() and 0xFF != 0x16) return null // not handshake
        var pos = offset + 5 // skip TLS record header
        if (pos + 4 > offset + length) return null
        if (data[pos].toInt() and 0xFF != 0x01) return null // not ClientHello
        val helloLen = readUint24(data, pos + 1)
        pos += 4
        val end = minOf(offset + length, pos + helloLen)
        if (pos + 2 + 32 + 1 > end) return null
        pos += 2 // client version
        pos += 32 // random
        val sessionIdLen = data[pos].toInt() and 0xFF
        pos += 1 + sessionIdLen
        if (pos + 2 > end) return null
        val cipherLen = readUint16(data, pos)
        pos += 2 + cipherLen
        if (pos + 1 > end) return null
        val compLen = data[pos].toInt() and 0xFF
        pos += 1 + compLen
        if (pos + 2 > end) return null
        val extLen = readUint16(data, pos)
        pos += 2
        val extEnd = minOf(end, pos + extLen)
        while (pos + 4 <= extEnd) {
            val type = readUint16(data, pos)
            val len = readUint16(data, pos + 2)
            pos += 4
            if (pos + len > extEnd) break
            if (type == 0) { // server_name
                return parseServerName(data, pos, len)
            }
            pos += len
        }
        return null
    }

    fun parseHttpHost(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): String? {
        val text = try {
            String(data, offset, length, StandardCharsets.US_ASCII)
        } catch (_: Exception) {
            return null
        }
        val first = text.substringBefore("\r\n\r\n", missingDelimiterValue = text)
        if (!first.startsWith("GET ") && !first.startsWith("POST ") &&
            !first.startsWith("HEAD ") && !first.startsWith("PUT ") &&
            !first.startsWith("OPTIONS ") && !first.startsWith("DELETE ") &&
            !first.startsWith("PATCH ") && !first.startsWith("CONNECT ")
        ) {
            // may still contain Host if headers arrived first — keep looking
            if (!first.contains("HTTP/1.")) return null
        }
        val regex = Regex("(?im)^Host:\\s*([^\\s:]+)")
        return regex.find(first)?.groupValues?.get(1)?.trim()?.trimEnd('.')
    }

    private fun parseServerName(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 5) return null
        var pos = offset + 2 // list length
        val end = offset + length
        while (pos + 3 <= end) {
            val nameType = data[pos].toInt() and 0xFF
            val nameLen = readUint16(data, pos + 1)
            pos += 3
            if (pos + nameLen > end) return null
            if (nameType == 0) {
                return String(data, pos, nameLen, StandardCharsets.US_ASCII)
                    .trim()
                    .trimEnd('.')
                    .lowercase()
            }
            pos += nameLen
        }
        return null
    }

    private fun readUint16(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
    }

    private fun readUint24(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }

}
