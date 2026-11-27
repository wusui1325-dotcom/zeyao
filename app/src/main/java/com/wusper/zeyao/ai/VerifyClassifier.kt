package com.wusper.zeyao.ai

import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.ModelRole
import com.wusper.zeyao.data.ModelRoleSettings
import com.wusper.zeyao.data.NewsHosts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

/**
 * Runs the flash-class news check with fail-open semantics.
 * Does not fetch the article or run search/extract.
 */
object VerifyClassifier {
    const val TIMEOUT_MS = 8_000L

    suspend fun run(
        url: String,
        title: String,
        settings: AppSettings,
        apiKey: String?,
        client: OkHttpClient = HttpClients.classify,
        timeoutMs: Long = TIMEOUT_MS,
    ): Boolean? {
        val key = apiKey.orEmpty()
        val knownNews = NewsHosts.shouldInterceptUrl(url, loose = true)
        if (key.isBlank()) return if (knownNews) true else null
        val canonical = NewsHosts.canonicalUrl(url)
        NewsClassifier.cached(canonical)?.let { return it }
        val provider = AiProvider.fromId(settings.providerId)
        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, settings, provider)
        return try {
            val result = withTimeoutOrNull(timeoutMs) {
                NewsClassifier.classify(
                    provider = provider,
                    apiKey = key,
                    title = title,
                    url = url,
                    models = verify.models,
                    temperature = verify.temperature,
                    maxTokens = verify.maxTokens,
                    reasoningEffort = verify.reasoningEffort,
                    client = client,
                )
            }
            if (result != null) {
                NewsClassifier.remember(canonical, result)
                result
            } else {
                if (knownNews) true else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (knownNews) true else null
        }
    }
}
