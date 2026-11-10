package com.wusper.zeyao.ai

object AiClientFactory {
    fun create(provider: AiProvider): AiClient = when (provider) {
        AiProvider.DEEPSEEK -> DeepSeekClient()
        AiProvider.OPENAI -> OpenAiClient()
        AiProvider.GEMINI -> GeminiClient()
        AiProvider.CLAUDE -> ClaudeClient()
        AiProvider.GROK -> GrokClient()
    }

    fun create(providerId: String): AiClient = create(AiProvider.fromId(providerId))
}
