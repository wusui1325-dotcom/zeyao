package com.wusper.zeyao.intercept

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat

object VpnController {
    const val ACTION_STOP = "com.wusper.zeyao.action.STOP_VPN"

    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    fun start(context: Context) {
        val intent = Intent(context, ZeyaoVpnService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        val intent = Intent(context, ZeyaoVpnService::class.java).setAction(ACTION_STOP)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (_: Exception) {
            context.startService(intent)
        }
    }

    fun startIfPermitted(context: Context): Boolean {
        if (prepareIntent(context) != null) return false
        start(context)
        return true
    }

    fun requestPrepare(activity: Activity, requestCode: Int): Boolean {
        val prepare = prepareIntent(activity) ?: run {
            start(activity)
            return false
        }
        activity.startActivityForResult(prepare, requestCode)
        return true
    }
}
