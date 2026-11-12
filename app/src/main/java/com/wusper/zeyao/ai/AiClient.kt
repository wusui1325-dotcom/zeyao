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
    fun build(
        title: String,
        url: String,
        articleText: String,
        language: com.wusper.zeyao.data.AppLanguage = com.wusper.zeyao.data.AppLanguage.TRADITIONAL,
        length: com.wusper.zeyao.data.SummaryLength = com.wusper.zeyao.data.SummaryLength.NORMAL,
        searchNews: Boolean = false,
    ): String {
        val body = articleText.trim().take(12_000)
        val langInstruction = when (language) {
            com.wusper.zeyao.data.AppLanguage.SIMPLIFIED -> "请用简体中文"
            com.wusper.zeyao.data.AppLanguage.TRADITIONAL -> "請用繁體中文"
            com.wusper.zeyao.data.AppLanguage.ENGLISH -> "Please write in English"
        }
        val lengthInstruction = when (length) {
            com.wusper.zeyao.data.SummaryLength.SHORT -> "寫得更短，約 2–3 句，只留最關鍵的事實。"
            com.wusper.zeyao.data.SummaryLength.NORMAL -> "寫一段可在數秒內讀完的簡要。"
            com.wusper.zeyao.data.SummaryLength.LONG -> "寫得稍長一些，約 5–7 句，保留更多關鍵細節與背景。"
        }
        val searchInstruction = if (searchNews) {
            "在撰寫摘要前，請先根據標題與網址，在你的知識中核對此新聞的重點、常見標題與關鍵詞，以確保事實盡量準確。若你有網絡搜索能力，請先進行多輪搜索再摘要。若資訊不足或無法核對，請如實說明。"
        } else {
            ""
        }
        return buildString {
            appendLine("你是新聞擇要助手。$langInstruction 為以下文章寫摘要。")
            appendLine(lengthInstruction)
            if (searchInstruction.isNotEmpty()) appendLine(searchInstruction)
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
