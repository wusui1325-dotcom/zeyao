package com.wusper.zeyao.intercept

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.wusper.zeyao.MainActivity
import com.wusper.zeyao.R
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.data.AppSettings
import java.net.DatagramSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ZeyaoVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tun: ParcelFileDescriptor? = null
    private var forwarder: PacketForwarder? = null
    private var observeJob: Job? = null
    @Volatile private var lastTargets: Set<String>? = null
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnController.ACTION_STOP) {
            tearDown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        running = true
        establishIfNeeded()
        observeSettings()
        val persistent = try {
            (application as ZeyaoApp).settings
            true
        } catch (_: Exception) {
            true
        }
        return START_STICKY
    }

    override fun onRevoke() {
        tearDown()
        super.onRevoke()
    }

    override fun onDestroy() {
        tearDown()
        scope.cancel()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun protectSocket(socket: Socket): Boolean = protect(socket)
    fun protectDatagram(socket: DatagramSocket): Boolean = protect(socket)

    private fun observeSettings() {
        val app = application as? ZeyaoApp ?: return
        observeJob?.cancel()
        observeJob = scope.launch {
            app.settings.settings.collect { settings ->
                if (!running) return@collect
                if (!settings.interceptEnabled && !settings.persistentEnabled) {
                    tearDown()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }
                val targets = settings.targetPackages
                if (lastTargets != null && lastTargets != targets) {
                    establishTunnel(settings, force = true)
                }
            }
        }
    }

    private fun establishIfNeeded() {
        scope.launch {
            val app = application as? ZeyaoApp ?: return@launch
            val settings = app.settings.current()
            establishTunnel(settings, force = false)
        }
    }

    @Synchronized
    private fun establishTunnel(settings: AppSettings, force: Boolean) {
        if (tun != null && !force) return
        closeTunnel()
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress(TUN_ADDR, 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .setBlocking(true)
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setMetered(false)
        }
        val targets = settings.targetPackages
        try {
            if (targets.isEmpty()) {
                builder.addDisallowedApplication(packageName)
            } else {
                var added = 0
                for (pkg in targets) {
                    try {
                        builder.addAllowedApplication(pkg)
                        added++
                    } catch (_: Exception) {
                    }
                }
                if (added == 0) {
                    builder.addDisallowedApplication(packageName)
                }
            }
        } catch (_: Exception) {
        }
        val pfd = try {
            builder.establish()
        } catch (_: Exception) {
            null
        }
        if (pfd == null) return
        tun = pfd
        lastTargets = targets
        val fwd = PacketForwarder(this, pfd)
        forwarder = fwd
        fwd.start()
    }

    @Synchronized
    private fun closeTunnel() {
        forwarder?.stop()
        forwarder = null
        try {
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
    }

    private fun tearDown() {
        running = false
        observeJob?.cancel()
        closeTunnel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "擇要攔截",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "擇要本機 VPN 正在執行，流量不會送往遠端伺服器。"
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_zeyao)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "zeyao_vpn"
        const val NOTIFICATION_ID = 17
        const val TUN_ADDR = "10.8.0.2"
        const val MTU = 1400

        @Volatile
        var instance: ZeyaoVpnService? = null
            private set
    }
}
