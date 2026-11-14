package com.wusper.zeyao.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for summary records (item 39 - data layer migration start).
 */
@Entity(tableName = "summary_records")
data class SummaryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val summary: String,
    val isNews: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)
