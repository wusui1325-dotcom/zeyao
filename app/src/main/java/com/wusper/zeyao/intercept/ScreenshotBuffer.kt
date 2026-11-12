package com.wusper.zeyao.intercept

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * Experimental: take a screenshot every second while image recognition is on.
 * Keeps at most 3 bitmaps. The intercept flow uses the previous one.
 */
object ScreenshotBuffer {
    private const val MAX = 3
    private const val INTERVAL_MS = 1000L

    private val handler = Handler(Looper.getMainLooper())
    private val frames = ArrayDeque<Bitmap>(MAX)
    private var running = false

    @Volatile
    var enabled: Boolean = false
        set(value) {
            field = value
            if (value) start() else stop()
        }

    fun latest(): Bitmap? = synchronized(frames) { frames.peekLast() }

    fun previous(): Bitmap? = synchronized(frames) {
        if (frames.size >= 2) frames.elementAt(frames.size - 2) else frames.peekLast()
    }

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
        synchronized(frames) {
            frames.forEach { runCatching { it.recycle() } }
            frames.clear()
        }
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
                        if (bmp != null) add(bmp)
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        }
    }

    private fun add(bmp: Bitmap) {
        synchronized(frames) {
            frames.addLast(bmp)
            while (frames.size > MAX) {
                runCatching { frames.removeFirst().recycle() }
            }
        }
    }
}
