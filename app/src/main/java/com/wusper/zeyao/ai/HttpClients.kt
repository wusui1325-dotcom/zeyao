package com.wusper.zeyao.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal object HttpClients {
    val init: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val stream: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    val classify: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
}
