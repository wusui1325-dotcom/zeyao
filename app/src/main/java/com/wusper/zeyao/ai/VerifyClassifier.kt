package com.wusper.zeyao.ai

import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.ModelRole
import com.wusper.zeyao.data.ModelRoleSettings
import com.wusper.zeyao.data.NewsHosts
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
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
    ): Boolean? {
        val key = apiKey.orEmpty()
        val knownNews = NewsHosts.shouldInterceptUrl(url, loose = true)
        if (key.isBlank()) return if (knownNews) true else null
        val canonical = NewsHosts.canonicalUrl(url)
        NewsClassifier.cached(canonical)?.let { return it }
        val provider = AiProvider.fromId(settings.providerId)
        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, settings, provider)
        return try {
            val result = withTimeout(TIMEOUT_MS) {
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
            NewsClassifier.remember(canonical, result)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (knownNews) true else null
        }
    }
}
