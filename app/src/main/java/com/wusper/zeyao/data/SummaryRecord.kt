package com.wusper.zeyao.data

enum class RecordStatus {
    COMPLETED,
    GENERATING,
    FAILED,
    CANCELLED,
}

data class SummaryRecord(
    val id: String,
    val url: String,
    val title: String,
    val host: String,
    val summary: String,
    val createdAt: Long,
    val status: RecordStatus,
)
