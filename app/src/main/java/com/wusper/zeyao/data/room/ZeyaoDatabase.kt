package com.wusper.zeyao.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SummaryRecordEntity::class], version = 1, exportSchema = false)
abstract class ZeyaoDatabase : RoomDatabase() {
    abstract fun summaryRecordDao(): SummaryRecordDao
}
