package com.wusper.zeyao.ai

enum class AiProvider(
    val id: String,
    val displayName: String,
    val keyHint: String,
    val defaultModel: String,
) {
    DEEPSEEK(
        id = "deepseek",
        displayName = "DeepSeek",
        keyHint = "sk-…",
        defaultModel = "deepseek-flash",
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        keyHint = "sk-…",
        defaultModel = "gpt-4o",
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        keyHint = "AIza…",
        defaultModel = "gemini-2.0-flash",
    ),
    CLAUDE(
        id = "claude",
        displayName = "Anthropic Claude",
        keyHint = "sk-ant-…",
        defaultModel = "claude-sonnet-4-0",
    ),
    GROK(
        id = "grok",
        displayName = "xAI Grok",
        keyHint = "xai-…",
        defaultModel = "grok-3",
    );

    companion object {
        fun fromId(id: String): AiProvider =
            entries.find { it.id == id } ?: DEEPSEEK
    }
}

data class GenerationParams(
    val thinkingEnabled: Boolean = false,
    val reasoningEffortHigh: Boolean = true,
    val temperature: Double = 1.2,
    val maxTokens: Int = 1000,
    val stream: Boolean = true,
    val language: com.wusper.zeyao.data.AppLanguage = com.wusper.zeyao.data.AppLanguage.TRADITIONAL,
    val summaryLength: com.wusper.zeyao.data.SummaryLength = com.wusper.zeyao.data.SummaryLength.NORMAL,
    val searchNews: Boolean = false,
) {
    companion object {
        val DEFAULT = GenerationParams()
    }
}

class AiInitException(message: String, cause: Throwable? = null) : Exception(message, cause)
