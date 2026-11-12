package com.wusper.zeyao.intercept

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.wusper.zeyao.ZeyaoApp
import java.io.File

/**
 * Experimental: screenshot every second into the app's private files dir.
 * Keeps at most 3 files; the oldest is deleted when the limit is exceeded.
 * Nothing is uploaded until the intercept flow explicitly asks for the previous frame.
 */
object ScreenshotBuffer {
    private const val MAX = 3
    private const val INTERVAL_MS = 1000L
    private const val DIR = "screenshots"

    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            if (value) start() else stop()
        }

    fun previous(): Bitmap? {
        val files = listFiles()
        val file = if (files.size >= 2) files[files.size - 2] else files.lastOrNull()
        return file?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
    }

    private fun dir(): File {
        val d = File(ZeyaoApp.instance.filesDir, DIR)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun listFiles(): List<File> =
        dir().listFiles { f -> f.isFile && f.name.endsWith(".jpg") }
            ?.sortedBy { it.lastModified() }
            .orEmpty()

    private val tick = object : Runnable {
        override fun run() {
            if (!running || !enabled) return
            val service = InterceptController.accessibility
            if (service != null && Build.VERSION.SDK_INT >= 30) {
                capture(service)
            }
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    private fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(tick)
        listFiles().forEach { runCatching { it.delete() } }
    }

    private fun capture(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < 30) return
        runCatching {
            service.takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val bmp = result.hardwareBuffer.let { buffer ->
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        }
                        result.hardwareBuffer.close()
                        if (bmp != null) save(bmp)
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        }
    }

    private fun save(bmp: Bitmap) {
        runCatching {
            val file = File(dir(), "${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            bmp.recycle()
            val files = listFiles()
            if (files.size > MAX) {
                files.take(files.size - MAX).forEach { runCatching { it.delete() } }
            }
        }
    }
}
