package com.wusper.zeyao.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryRecordDao {
    @Query("SELECT * FROM summary_records ORDER BY createdAt DESC LIMIT 100")
    fun recent(): Flow<List<SummaryRecordEntity>>

    @Insert
    suspend fun insert(record: SummaryRecordEntity)

    @Query("DELETE FROM summary_records WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)
}
