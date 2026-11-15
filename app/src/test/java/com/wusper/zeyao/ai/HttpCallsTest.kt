package com.wusper.zeyao.ai

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCallsTest {
    @Test
    fun await_cancelsTheCallWhenTheCoroutineIsCancelled() {
        runBlocking {
            val server = MockWebServer()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            try {
                val client = OkHttpClient.Builder()
                    .callTimeout(8, TimeUnit.SECONDS)
                    .build()
                val call = client.newCall(
                    Request.Builder().url(server.url("/hang")).build(),
                )
                val deferred = async { call.await() }
                delay(50)
                deferred.cancel()
                var cancelled = false
                try {
                    withTimeout(1_000) { deferred.await() }
                } catch (_: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertTrue(call.isCanceled())
            } finally {
                server.shutdown()
            }
        }
    }
}
