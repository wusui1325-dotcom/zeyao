package com.wusper.zeyao.intercept.helpers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.wusper.zeyao.intercept.ScreenshotBuffer
import com.wusper.zeyao.ai.SummaryCache

/**
 * Periodic cleanup worker for memory and cache (item 21).
 * Replaces long-lived coroutines for background maintenance.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ScreenshotBuffer.cleanupOld()
            SummaryCache.clear()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
