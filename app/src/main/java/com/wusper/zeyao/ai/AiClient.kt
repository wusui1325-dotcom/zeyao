package com.wusper.zeyao.ai

import kotlinx.coroutines.flow.Flow

interface AiClient {
    val provider: AiProvider

    /**
     * Lightweight auth / model check. Throws [AiInitException] on failure.
     * Must not be used to proceed into the main screen unless it succeeds.
     */
    suspend fun initialize(apiKey: String): String

    /**
     * Stream a news brief. Each emission is a newly arrived text delta (not cumulative).
     */
    fun streamSummary(
        apiKey: String,
        title: String,
        url: String,
        articleText: String,
        params: GenerationParams = GenerationParams.DEFAULT,
    ): Flow<String>
}

object SummaryPrompt {
    fun build(title: String, url: String, articleText: String): String {
        val body = articleText.trim().take(12_000)
        return buildString {
            appendLine("你是新聞擇要助手。請用繁體中文為以下文章寫一段可在數秒內讀完的簡要。")
            appendLine("只保留關鍵事實（何人、何事、何時、何地、結果與影響）。")
            appendLine("不要評論、不要另起標題、不要輸出條列清單、不要重複原文。")
            appendLine("若原文資訊不足，請如實寫出已知部分，不要臆測。")
            appendLine()
            append("標題："); appendLine(title.ifBlank { "（無標題）" })
            append("網址："); appendLine(url)
            appendLine("正文：")
            append(body.ifBlank { "（未能擷取正文，請僅依標題與網址判斷；若無法判斷請說明資訊不足。）" })
        }
    }
}
