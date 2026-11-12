package com.wusper.zeyao.intercept

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.wusper.zeyao.ZeyaoApp

/**
 * Small always-on-top button. Tap once to arm the next link click
 * for the accessibility service.
 */
class ClickInterceptOverlay : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var armed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (overlayView == null) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return
        val tv = TextView(this).apply {
            text = "擇"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCCB33A2B.toInt())
            setPadding(28, 16, 28, 16)
            setOnClickListener {
                armed = !armed
                text = if (armed) "擇●" else "擇"
                InterceptController.clickArmed = armed
                // Auto-disarm after a short window so it doesn't stay forever.
                if (armed) {
                    postDelayed({
                        if (armed) {
                            armed = false
                            text = "擇"
                            InterceptController.clickArmed = false
                        }
                    }, 8_000)
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 32
            y = 180
        }
        try {
            windowManager?.addView(tv, params)
            overlayView = tv
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        overlayView = null
        InterceptController.clickArmed = false
    }

    companion object {
        private const val ACTION_STOP = "com.wusper.zeyao.CLICK_OVERLAY_STOP"

        fun start(context: Context) {
            val intent = Intent(context, ClickInterceptOverlay::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClickInterceptOverlay::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
