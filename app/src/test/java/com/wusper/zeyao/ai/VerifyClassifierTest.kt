
package com.wusper.zeyao.ai

import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.NewsHosts
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VerifyClassifierTest {
    @Test
    fun missingKey_doesNotClassifyAndFailsOpen() {
        runBlocking {
            // JVM unit tests cannot rely on android.net.Uri, so this uses a
            // host that is not on the news list and must fail open to null.
            assertNull(
                VerifyClassifier.run(
                    "https://example.com/not-a-listed-news-host/page",
                    title = "",
                    settings = AppSettings(),
                    apiKey = null,
                ),
            )
            assertNull(
                VerifyClassifier.run(
                    "https://example.com/not-a-listed-news-host/page",
                    title = "",
                    settings = AppSettings(),
                    apiKey = "  ",
                ),
            )
        }
    }

    @Test
    fun cachedResult_skipsNetwork() {
        runBlocking {
            val url = "https://www.bbc.com/news/world-cached-verify-9"
            val canonical = NewsHosts.canonicalUrl(url)
            NewsClassifier.remember(canonical, false)
            val server = MockWebServer()
            server.start()
            try {
                val client = server.toClient()
                val result = VerifyClassifier.run(
                    url,
                    title = "cached",
                    settings = AppSettings(),
                    apiKey = "sk-test",
                    client = client,
                )
                assertEquals(false, result)
                assertEquals(0, server.requestCount)
            } finally {
                server.shutdown()
            }
        }
    }
}

private fun MockWebServer.toClient(): okhttp3.OkHttpClient =
    okhttp3.OkHttpClient.Builder().build()
