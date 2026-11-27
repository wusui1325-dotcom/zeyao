package com.wusper.zeyao.ai

import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal object HttpClients {
    /**
     * Single shared base client so [init], [classify], and [stream] reuse the
     * same [ConnectionPool] and [okhttp3.Dispatcher]. Warming or classifying a
     * provider host therefore keeps the HTTP/2 + TLS connection hot for the
     * immediately following summary stream.
     */
    private val base: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    val init: OkHttpClient = base.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val stream: OkHttpClient = base.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val classify: OkHttpClient = base.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val warmed = ConcurrentHashMap.newKeySet<String>()

    /**
     * Opens a TLS connection to the vendor API host and leaves it in the
     * shared pool so both the first classify request and the summary stream
     * can skip the handshake. No API key is sent. Safe to call repeatedly.
     */
    fun warmClassify(provider: AiProvider) {
        val root = when (provider) {
            AiProvider.DEEPSEEK -> "https://api.deepseek.com/"
            AiProvider.OPENAI -> "https://api.openai.com/"
            AiProvider.GEMINI -> "https://generativelanguage.googleapis.com/"
            AiProvider.CLAUDE -> "https://api.anthropic.com/"
            AiProvider.GROK -> "https://api.x.ai/"
        }
        if (!warmed.add(root)) return
        val request = Request.Builder()
            .url(root)
            .head()
            .header("User-Agent", "Zeyao")
            .build()
        classify.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                warmed.remove(root)
            }
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.close()
            }
        })
    }
}
