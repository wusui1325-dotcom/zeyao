package com.wusper.zeyao.ai

import okhttp3.Response
import okio.BufferedSource

/**
 * Robust SSE parser supporting multi-line data, comments, and [DONE].
 * Does not log bodies that might contain secrets.
 */
internal object SseReader {
    fun readEvents(response: Response, onData: (String) -> Unit) {
        val source: BufferedSource = response.body?.source()
            ?: throw AiInitException("服務沒有回傳內容")
        val dataLines = mutableListOf<String>()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (dataLines.isNotEmpty()) {
                        val data = dataLines.joinToString("\n")
                        dataLines.clear()
                        if (data.isNotEmpty() && data != "[DONE]") onData(data)
                    }
                }
                line.startsWith(":") -> { /* comment */ }
                line.startsWith("data:") -> {
                    dataLines.add(line.removePrefix("data:").trimStart())
                }
                line.startsWith("id:") || line.startsWith("retry:") || line.startsWith("event:") -> {
                    // explicitly ignore control fields
                }
            }
        }
        if (dataLines.isNotEmpty()) {
            val data = dataLines.joinToString("\n")
            if (data.isNotEmpty() && data != "[DONE]") onData(data)
        }
    }
}
