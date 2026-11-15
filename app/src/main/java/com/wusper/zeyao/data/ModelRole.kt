package com.wusper.zeyao.data

import com.wusper.zeyao.ai.AiProvider

enum class ModelRole {
    SEARCH,
    SUMMARIZE,
    VERIFY,
    ;

    val storageId: String
        get() = when (this) {
            SEARCH -> "search"
            SUMMARIZE -> "summarize"
            VERIFY -> "verify"
        }
}

data class RoleRuntimeConfig(
    val role: ModelRole,
    val provider: AiProvider,
    val model: String,
    val models: List<String>,
    val temperature: Double,
    val intensity: Int,
    val maxTokens: Int,
    val reasoningEffort: String,
    val searchCount: Int,
    val factSearchCount: Int,
)

/**
 * Resolves per-role model, temperature, intensity, and derived request
 * parameters. Invalid or empty values fail open to safe defaults.
 */
object ModelRoleSettings {
    const val MIN_TEMPERATURE = 0.0
    const val MAX_TEMPERATURE = 2.0
    const val MIN_INTENSITY = 1
    const val MAX_INTENSITY = 5
    const val DEFAULT_SEARCH_TEMPERATURE = 0.3
    const val DEFAULT_SUMMARIZE_TEMPERATURE = 1.0
    const val DEFAULT_VERIFY_TEMPERATURE = 0.0
    const val DEFAULT_SEARCH_INTENSITY = 3
    const val DEFAULT_SUMMARIZE_INTENSITY = 3
    const val DEFAULT_VERIFY_INTENSITY = 1
    const val DEFAULT_MAX_TOKENS = 1200

    fun sanitizeTemperature(value: Double, default: Double): Double {
        if (value.isNaN() || value.isInfinite()) return default
        return value.coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
    }

    fun sanitizeIntensity(value: Int, default: Int = DEFAULT_SUMMARIZE_INTENSITY): Int {
        if (value !in MIN_INTENSITY..MAX_INTENSITY) return default.coerceIn(MIN_INTENSITY, MAX_INTENSITY)
        return value
    }

    fun parseModels(custom: String?): List<String> =
        custom.orEmpty().split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }

    fun defaultModels(role: ModelRole, provider: AiProvider): List<String> = when (role) {
        ModelRole.SUMMARIZE -> AiProvider.fallbackList(provider, "")
        ModelRole.SEARCH, ModelRole.VERIFY -> when (provider) {
            AiProvider.DEEPSEEK -> listOf("deepseek-flash", "deepseek-v4-flash")
            AiProvider.OPENAI -> listOf("gpt-4o-mini", "gpt-4.1-mini", "gpt-4.1-nano")
            AiProvider.GEMINI -> listOf(
                "gemini-2.0-flash-lite",
                "gemini-2.0-flash-lite-001",
                "gemini-2.0-flash",
                "gemini-1.5-flash-8b",
            )
            AiProvider.CLAUDE -> listOf(
                "claude-haiku-4-0",
                "claude-3-5-haiku-latest",
                "claude-3-haiku-20240307",
            )
            AiProvider.GROK -> listOf("grok-3-mini", "grok-2-mini", "grok-3", "grok-2")
        }
    }

    fun customModelText(role: ModelRole, settings: AppSettings): String = when (role) {
        ModelRole.SEARCH -> settings.searchModel
        ModelRole.SUMMARIZE -> settings.summarizeModel.ifBlank { settings.fallbackModels }
        ModelRole.VERIFY -> settings.verifyModel
    }

    fun displayIntensity(role: ModelRole, settings: AppSettings): Int = when (role) {
        ModelRole.SEARCH -> sanitizeIntensity(settings.searchIntensity, DEFAULT_SEARCH_INTENSITY)
        ModelRole.SUMMARIZE -> summarizeIntensity(settings)
        ModelRole.VERIFY -> sanitizeIntensity(settings.verifyIntensity, DEFAULT_VERIFY_INTENSITY)
    }

    fun summarizeIntensity(settings: AppSettings): Int =
        if (settings.summarizeIntensity in MIN_INTENSITY..MAX_INTENSITY) {
            settings.summarizeIntensity
        } else {
            intensityFromMaxTokens(settings.maxTokens)
        }

    fun intensityFromMaxTokens(tokens: Int): Int = when {
        tokens <= 0 -> DEFAULT_SUMMARIZE_INTENSITY
        tokens <= 500 -> 1
        tokens <= 900 -> 2
        tokens <= 1400 -> 3
        tokens <= 1800 -> 4
        else -> 5
    }

    fun summarizeTokens(intensity: Int): Int = when (sanitizeIntensity(intensity, DEFAULT_SUMMARIZE_INTENSITY)) {
        1 -> 400
        2 -> 800
        3 -> 1200
        4 -> 1600
        else -> 2000
    }

    fun extractTokens(intensity: Int): Int = when (sanitizeIntensity(intensity, DEFAULT_SEARCH_INTENSITY)) {
        1 -> 40
        2 -> 50
        3 -> 60
        4 -> 80
        else -> 120
    }

    fun verifyTokens(intensity: Int): Int = when (sanitizeIntensity(intensity, DEFAULT_VERIFY_INTENSITY)) {
        1 -> 8
        2 -> 12
        3 -> 16
        4 -> 24
        else -> 32
    }

    fun searchCount(intensity: Int): Int = when (sanitizeIntensity(intensity, DEFAULT_SEARCH_INTENSITY)) {
        1 -> 3
        2 -> 4
        3 -> 5
        4 -> 7
        else -> 10
    }

    fun factSearchCount(intensity: Int): Int = when (sanitizeIntensity(intensity, DEFAULT_SEARCH_INTENSITY)) {
        1 -> 1
        2, 3 -> 2
        4 -> 3
        else -> 4
    }

    fun reasoningEffort(role: ModelRole, intensity: Int): String {
        val level = sanitizeIntensity(
            intensity,
            when (role) {
                ModelRole.SEARCH -> DEFAULT_SEARCH_INTENSITY
                ModelRole.SUMMARIZE -> DEFAULT_SUMMARIZE_INTENSITY
                ModelRole.VERIFY -> DEFAULT_VERIFY_INTENSITY
            },
        )
        return when (role) {
            ModelRole.SUMMARIZE -> if (level <= 2) "low" else "high"
            ModelRole.VERIFY -> when {
                level <= 2 -> "low"
                level <= 4 -> "medium"
                else -> "high"
            }
            ModelRole.SEARCH -> if (level <= 2) "low" else "medium"
        }
    }

    fun resolve(
        role: ModelRole,
        settings: AppSettings,
        provider: AiProvider = AiProvider.fromId(settings.providerId),
    ): RoleRuntimeConfig {
        val models = parseModels(customModelText(role, settings)).ifEmpty { defaultModels(role, provider) }
        val defaultTemp = when (role) {
            ModelRole.SEARCH -> DEFAULT_SEARCH_TEMPERATURE
            ModelRole.SUMMARIZE -> DEFAULT_SUMMARIZE_TEMPERATURE
            ModelRole.VERIFY -> DEFAULT_VERIFY_TEMPERATURE
        }
        val rawTemp = when (role) {
            ModelRole.SEARCH -> settings.searchTemperature
            ModelRole.SUMMARIZE -> settings.temperature
            ModelRole.VERIFY -> settings.verifyTemperature
        }
        val temperature = sanitizeTemperature(rawTemp, defaultTemp)
        val intensity = displayIntensity(role, settings)
        val maxTokens = when (role) {
            ModelRole.SEARCH -> extractTokens(intensity)
            ModelRole.SUMMARIZE -> if (settings.summarizeIntensity in MIN_INTENSITY..MAX_INTENSITY) {
                summarizeTokens(intensity)
            } else {
                val stored = settings.maxTokens
                if (stored <= 0) DEFAULT_MAX_TOKENS else stored.coerceIn(200, 4000)
            }
            ModelRole.VERIFY -> verifyTokens(intensity)
        }
        return RoleRuntimeConfig(
            role = role,
            provider = provider,
            model = models.first(),
            models = models,
            temperature = temperature,
            intensity = intensity,
            maxTokens = maxTokens,
            reasoningEffort = reasoningEffort(role, intensity),
            searchCount = searchCount(intensity),
            factSearchCount = factSearchCount(intensity),
        )
    }

    /**
     * Stable preference key names used by [SettingsRepository].
     * Exposed so persistence can be unit-tested without DataStore.
     */
    object PrefKeys {
        const val SEARCH_MODEL = "search_model"
        const val SUMMARIZE_MODEL = "summarize_model"
        const val VERIFY_MODEL = "verify_model"
        const val SEARCH_TEMPERATURE = "search_temperature"
        const val VERIFY_TEMPERATURE = "verify_temperature"
        const val SEARCH_INTENSITY = "search_intensity"
        const val SUMMARIZE_INTENSITY = "summarize_intensity"
        const val VERIFY_INTENSITY = "verify_intensity"
    }

    fun snapshot(settings: AppSettings): Map<String, String> = mapOf(
        PrefKeys.SEARCH_MODEL to settings.searchModel,
        PrefKeys.SUMMARIZE_MODEL to settings.summarizeModel,
        PrefKeys.VERIFY_MODEL to settings.verifyModel,
        PrefKeys.SEARCH_TEMPERATURE to settings.searchTemperature.toString(),
        PrefKeys.VERIFY_TEMPERATURE to settings.verifyTemperature.toString(),
        PrefKeys.SEARCH_INTENSITY to settings.searchIntensity.toString(),
        PrefKeys.SUMMARIZE_INTENSITY to settings.summarizeIntensity.toString(),
        PrefKeys.VERIFY_INTENSITY to settings.verifyIntensity.toString(),
        "temperature" to settings.temperature.toString(),
        "max_tokens" to settings.maxTokens.toString(),
        "fallback_models" to settings.fallbackModels,
        "provider" to settings.providerId,
    )

    fun restore(values: Map<String, String>, base: AppSettings = AppSettings()): AppSettings {
        fun d(key: String, default: Double): Double =
            values[key]?.toDoubleOrNull()?.let { sanitizeTemperature(it, default) } ?: default

        fun i(key: String, default: Int): Int =
            values[key]?.toIntOrNull() ?: default

        val maxTokens = (values["max_tokens"]?.toIntOrNull() ?: base.maxTokens).let {
            if (it <= 0) DEFAULT_MAX_TOKENS else it.coerceIn(200, 4000)
        }
        val summarizeIntensityRaw = i(PrefKeys.SUMMARIZE_INTENSITY, 0)
        return base.copy(
            providerId = values["provider"]?.trim()?.ifBlank { null } ?: base.providerId,
            searchModel = values[PrefKeys.SEARCH_MODEL]?.trim().orEmpty(),
            summarizeModel = values[PrefKeys.SUMMARIZE_MODEL]?.trim().orEmpty(),
            verifyModel = values[PrefKeys.VERIFY_MODEL]?.trim().orEmpty(),
            searchTemperature = d(PrefKeys.SEARCH_TEMPERATURE, DEFAULT_SEARCH_TEMPERATURE),
            temperature = d("temperature", DEFAULT_SUMMARIZE_TEMPERATURE),
            verifyTemperature = d(PrefKeys.VERIFY_TEMPERATURE, DEFAULT_VERIFY_TEMPERATURE),
            searchIntensity = sanitizeIntensity(i(PrefKeys.SEARCH_INTENSITY, DEFAULT_SEARCH_INTENSITY), DEFAULT_SEARCH_INTENSITY),
            summarizeIntensity = if (summarizeIntensityRaw in MIN_INTENSITY..MAX_INTENSITY) summarizeIntensityRaw else 0,
            verifyIntensity = sanitizeIntensity(i(PrefKeys.VERIFY_INTENSITY, DEFAULT_VERIFY_INTENSITY), DEFAULT_VERIFY_INTENSITY),
            maxTokens = maxTokens,
            fallbackModels = values["fallback_models"]?.trim().orEmpty(),
        )
    }
}

object ApiKeyResolver {
    fun resolve(roleKey: String?, sharedKey: String?): String? =
        roleKey?.trim()?.takeIf { it.isNotEmpty() } ?: sharedKey?.trim()?.takeIf { it.isNotEmpty() }
}
