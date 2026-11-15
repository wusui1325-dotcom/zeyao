package com.wusper.zeyao.data

import com.wusper.zeyao.ai.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoleSettingsTest {

    @Test
    fun defaults_matchCurrentProductBehavior() {
        val settings = AppSettings()
        val search = ModelRoleSettings.resolve(ModelRole.SEARCH, settings, AiProvider.DEEPSEEK)
        assertEquals("deepseek-flash", search.model)
        assertEquals(listOf("deepseek-flash", "deepseek-v4-flash"), search.models)
        assertEquals(0.3, search.temperature, 0.0)
        assertEquals(3, search.intensity)
        assertEquals(60, search.maxTokens)
        assertEquals(5, search.searchCount)
        assertEquals(2, search.factSearchCount)

        val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, settings, AiProvider.DEEPSEEK)
        assertEquals("deepseek-flash", summarize.model)
        assertEquals(1.0, summarize.temperature, 0.0)
        assertEquals(3, summarize.intensity)
        assertEquals(1200, summarize.maxTokens)
        assertEquals("high", summarize.reasoningEffort)

        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, settings, AiProvider.DEEPSEEK)
        assertEquals("deepseek-flash", verify.model)
        assertEquals(0.0, verify.temperature, 0.0)
        assertEquals(1, verify.intensity)
        assertEquals(8, verify.maxTokens)
        assertEquals("low", verify.reasoningEffort)
    }

    @Test
    fun eachRoleResolvesItsOwnModelTempIntensity() {
        val settings = AppSettings(
            searchModel = "search-a, search-b",
            summarizeModel = "sum-a",
            verifyModel = "verify-a",
            searchTemperature = 0.4,
            temperature = 1.1,
            verifyTemperature = 0.2,
            searchIntensity = 5,
            summarizeIntensity = 2,
            verifyIntensity = 4,
        )
        val search = ModelRoleSettings.resolve(ModelRole.SEARCH, settings, AiProvider.OPENAI)
        assertEquals(listOf("search-a", "search-b"), search.models)
        assertEquals("search-a", search.model)
        assertEquals(0.4, search.temperature, 0.0)
        assertEquals(5, search.intensity)
        assertEquals(120, search.maxTokens)
        assertEquals(10, search.searchCount)

        val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, settings, AiProvider.OPENAI)
        assertEquals(listOf("sum-a"), summarize.models)
        assertEquals(1.1, summarize.temperature, 0.0)
        assertEquals(2, summarize.intensity)
        assertEquals(800, summarize.maxTokens)
        assertEquals("low", summarize.reasoningEffort)

        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, settings, AiProvider.OPENAI)
        assertEquals(listOf("verify-a"), verify.models)
        assertEquals(0.2, verify.temperature, 0.0)
        assertEquals(4, verify.intensity)
        assertEquals(24, verify.maxTokens)
        assertEquals("medium", verify.reasoningEffort)
    }

    @Test
    fun summarizeFallsBackToLegacyFallbackModelsAndMaxTokens() {
        val settings = AppSettings(
            summarizeModel = "",
            fallbackModels = "custom-sum, alt-sum",
            maxTokens = 1500,
            summarizeIntensity = 0,
        )
        val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, settings, AiProvider.DEEPSEEK)
        assertEquals(listOf("custom-sum", "alt-sum"), summarize.models)
        assertEquals(4, summarize.intensity) // derived from 1500
        assertEquals(1500, summarize.maxTokens) // keep stored budget until intensity is set
    }

    @Test
    fun emptyOrInvalidValuesFailOpenToDefaults() {
        val settings = AppSettings(
            searchModel = "  ,  ， ",
            summarizeModel = "   ",
            verifyModel = "",
            searchTemperature = Double.NaN,
            temperature = 9.0,
            verifyTemperature = Double.NEGATIVE_INFINITY,
            searchIntensity = 0,
            summarizeIntensity = 99,
            verifyIntensity = -3,
            maxTokens = 0,
        )
        val search = ModelRoleSettings.resolve(ModelRole.SEARCH, settings)
        assertEquals(ModelRoleSettings.defaultModels(ModelRole.SEARCH, AiProvider.DEEPSEEK), search.models)
        assertEquals(0.3, search.temperature, 0.0)
        assertEquals(3, search.intensity)

        val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, settings)
        assertEquals(ModelRoleSettings.defaultModels(ModelRole.SUMMARIZE, AiProvider.DEEPSEEK), summarize.models)
        assertEquals(2.0, summarize.temperature, 0.0) // clamped, not NaN
        assertEquals(3, summarize.intensity) // invalid intensity derives from maxTokens fallback
        assertEquals(1200, summarize.maxTokens)

        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, settings)
        assertEquals(0.0, verify.temperature, 0.0)
        assertEquals(1, verify.intensity)
        assertEquals(8, verify.maxTokens)
    }

    @Test
    fun apiKeyFallsBackToSharedThenNull() {
        assertEquals("role-key", ApiKeyResolver.resolve("role-key", "shared-key"))
        assertEquals("role-key", ApiKeyResolver.resolve("  role-key  ", "shared-key"))
        assertEquals("shared-key", ApiKeyResolver.resolve("", "shared-key"))
        assertEquals("shared-key", ApiKeyResolver.resolve("   ", "  shared-key "))
        assertEquals("shared-key", ApiKeyResolver.resolve(null, "shared-key"))
        assertNull(ApiKeyResolver.resolve(null, null))
        assertNull(ApiKeyResolver.resolve("  ", ""))
        assertNull(ApiKeyResolver.resolve(null, "  "))
    }

    @Test
    fun snapshotRestoreRoundTripPreservesRoleSettings() {
        val original = AppSettings(
            providerId = "openai",
            searchModel = "gpt-4o-mini",
            summarizeModel = "gpt-4o",
            verifyModel = "gpt-4.1-nano",
            searchTemperature = 0.6,
            temperature = 1.4,
            verifyTemperature = 0.1,
            searchIntensity = 2,
            summarizeIntensity = 5,
            verifyIntensity = 3,
            maxTokens = 2000,
            fallbackModels = "legacy",
        )
        val restored = ModelRoleSettings.restore(ModelRoleSettings.snapshot(original))
        assertEquals(original.searchModel, restored.searchModel)
        assertEquals(original.summarizeModel, restored.summarizeModel)
        assertEquals(original.verifyModel, restored.verifyModel)
        assertEquals(original.searchTemperature, restored.searchTemperature, 0.0)
        assertEquals(original.temperature, restored.temperature, 0.0)
        assertEquals(original.verifyTemperature, restored.verifyTemperature, 0.0)
        assertEquals(original.searchIntensity, restored.searchIntensity)
        assertEquals(original.summarizeIntensity, restored.summarizeIntensity)
        assertEquals(original.verifyIntensity, restored.verifyIntensity)
        assertEquals(original.maxTokens, restored.maxTokens)
        assertEquals(original.fallbackModels, restored.fallbackModels)
        assertEquals(original.providerId, restored.providerId)

        val search = ModelRoleSettings.resolve(ModelRole.SEARCH, restored, AiProvider.OPENAI)
        val summarize = ModelRoleSettings.resolve(ModelRole.SUMMARIZE, restored, AiProvider.OPENAI)
        val verify = ModelRoleSettings.resolve(ModelRole.VERIFY, restored, AiProvider.OPENAI)
        assertEquals("gpt-4o-mini", search.model)
        assertEquals(0.6, search.temperature, 0.0)
        assertEquals(2, search.intensity)
        assertEquals("gpt-4o", summarize.model)
        assertEquals(1.4, summarize.temperature, 0.0)
        assertEquals(5, summarize.intensity)
        assertEquals(2000, summarize.maxTokens)
        assertEquals("gpt-4.1-nano", verify.model)
        assertEquals(0.1, verify.temperature, 0.0)
        assertEquals(3, verify.intensity)
        assertEquals("medium", verify.reasoningEffort)
    }

    @Test
    fun restore_clampsInvalidPersistedValues() {
        val restored = ModelRoleSettings.restore(
            mapOf(
                ModelRoleSettings.PrefKeys.SEARCH_TEMPERATURE to "9",
                "temperature" to "nan",
                ModelRoleSettings.PrefKeys.VERIFY_TEMPERATURE to "-1",
                ModelRoleSettings.PrefKeys.SEARCH_INTENSITY to "0",
                ModelRoleSettings.PrefKeys.SUMMARIZE_INTENSITY to "8",
                ModelRoleSettings.PrefKeys.VERIFY_INTENSITY to "abc",
                "max_tokens" to "-20",
                ModelRoleSettings.PrefKeys.SEARCH_MODEL to "  alpha  ",
            ),
        )
        assertEquals("alpha", restored.searchModel)
        assertEquals(2.0, restored.searchTemperature, 0.0)
        assertEquals(1.0, restored.temperature, 0.0)
        assertEquals(0.0, restored.verifyTemperature, 0.0)
        assertEquals(3, restored.searchIntensity)
        assertEquals(0, restored.summarizeIntensity) // invalid stored as unset
        assertEquals(1, restored.verifyIntensity)
        assertEquals(1200, restored.maxTokens)
    }

    @Test
    fun prefKeys_areStable() {
        assertEquals("search_model", ModelRoleSettings.PrefKeys.SEARCH_MODEL)
        assertEquals("summarize_model", ModelRoleSettings.PrefKeys.SUMMARIZE_MODEL)
        assertEquals("verify_model", ModelRoleSettings.PrefKeys.VERIFY_MODEL)
        assertEquals("search_temperature", ModelRoleSettings.PrefKeys.SEARCH_TEMPERATURE)
        assertEquals("verify_temperature", ModelRoleSettings.PrefKeys.VERIFY_TEMPERATURE)
        assertEquals("search_intensity", ModelRoleSettings.PrefKeys.SEARCH_INTENSITY)
        assertEquals("summarize_intensity", ModelRoleSettings.PrefKeys.SUMMARIZE_INTENSITY)
        assertEquals("verify_intensity", ModelRoleSettings.PrefKeys.VERIFY_INTENSITY)
        assertEquals("search", ModelRole.SEARCH.storageId)
        assertEquals("summarize", ModelRole.SUMMARIZE.storageId)
        assertEquals("verify", ModelRole.VERIFY.storageId)
    }

    @Test
    fun eachProviderHasBuiltInModelsForEveryRole() {
        for (provider in AiProvider.entries) {
            for (role in ModelRole.entries) {
                val models = ModelRoleSettings.defaultModels(role, provider)
                assertTrue(models.isNotEmpty())
                assertTrue(models.all { it.isNotBlank() })
            }
        }
    }
}
