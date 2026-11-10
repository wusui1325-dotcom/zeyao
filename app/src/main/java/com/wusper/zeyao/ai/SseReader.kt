package com.wusper.zeyao.ai

import okhttp3.Response
import okio.BufferedSource

/**
 * Minimal SSE parser. Does not log response bodies that might contain secrets.
 */
internal object SseReader {
    fun readEvents(response: Response, onData: (String) -> Unit) {
        val source: BufferedSource = response.body?.source()
            ?: throw AiInitException("服務沒有回傳內容")
        val sb = StringBuilder()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                val data = sb.toString()
                sb.clear()
                if (data.isNotEmpty() && data != "[DONE]") onData(data)
                continue
            }
            if (line.startsWith("data:")) {
                val payload = line.removePrefix("data:").trimStart()
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(payload)
            }
            // ignore event:/id:/retry: comments
        }
        if (sb.isNotEmpty()) {
            val data = sb.toString()
            if (data.isNotEmpty() && data != "[DONE]") onData(data)
        }
    }
}
