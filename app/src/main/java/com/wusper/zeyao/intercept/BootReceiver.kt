package com.wusper.zeyao.intercept

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wusper.zeyao.ZeyaoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as? ZeyaoApp
                val settings = app?.settings?.current() ?: return@launch
                if (settings.persistentEnabled && settings.interceptEnabled) {
                    VpnController.startIfPermitted(context.applicationContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
