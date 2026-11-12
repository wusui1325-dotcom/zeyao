package com.wusper.zeyao

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.SummaryRecord
import com.wusper.zeyao.intercept.InterceptController
import com.wusper.zeyao.intercept.VpnController
import com.wusper.zeyao.mitm.LocalCa
import com.wusper.zeyao.ui.main.MainScreen
import com.wusper.zeyao.ui.main.RecordDetailScreen
import com.wusper.zeyao.ui.onboarding.OnboardingRoute
import com.wusper.zeyao.ui.other.OtherScreen
import com.wusper.zeyao.ui.settings.LaunchableApp
import com.wusper.zeyao.ui.settings.SettingsScreen
import com.wusper.zeyao.ui.theme.ZeyaoTheme
import com.wusper.zeyao.util.SystemChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingStartVpn = false

    private val vpnPrepare = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            VpnController.start(this)
            pendingStartVpn = false
        } else {
            pendingStartVpn = false
        }
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Notification is optional; intercept still works. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = ZeyaoApp.instance
            val settings by app.settings.settings.collectAsState(initial = AppSettings())
            val records by app.records.records.collectAsState()
            val scope = rememberCoroutineScope()
            var route by remember { mutableStateOf(Route.MAIN) }
            var openedRecord by remember { mutableStateOf<SummaryRecord?>(null) }
            var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
            var browsers by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
            var vpnReady by remember { mutableStateOf(SystemChecks.isVpnPrepared(this@MainActivity)) }
            var accessibilityOn by remember { mutableStateOf(SystemChecks.isAccessibilityEnabled(this@MainActivity)) }
            var isDefaultBrowser by remember { mutableStateOf(BrowserHandoff.isZeyaoDefaultBrowser(this@MainActivity)) }
            var caInstalled by remember { mutableStateOf(false) }
            var resumeTick by remember { mutableStateOf(0) }

            LaunchedEffect(route) {
                if (route == Route.SETTINGS) {
                    apps = withContext(Dispatchers.IO) { SystemChecks.listLaunchableApps(this@MainActivity) }
                    browsers = withContext(Dispatchers.IO) { BrowserHandoff.listBrowsers(this@MainActivity) }
                    isDefaultBrowser = BrowserHandoff.isZeyaoDefaultBrowser(this@MainActivity)
                    caInstalled = withContext(Dispatchers.IO) {
                        LocalCa.ensure(this@MainActivity)
                        val ok = LocalCa.isInstalled(this@MainActivity)
                        InterceptController.refreshCaInstalled()
                        ok
                    }
                }
            }

            DisposableEffect(Unit) {
                val listener = object : android.app.Application.ActivityLifecycleCallbacks {
                    override fun onActivityResumed(activity: android.app.Activity) {
                        if (activity === this@MainActivity) {
                            vpnReady = SystemChecks.isVpnPrepared(this@MainActivity)
                            accessibilityOn = SystemChecks.isAccessibilityEnabled(this@MainActivity)
                            isDefaultBrowser = BrowserHandoff.isZeyaoDefaultBrowser(this@MainActivity)
                            caInstalled = runCatching { LocalCa.isInstalled(this@MainActivity) }.getOrDefault(false)
                            InterceptController.refreshCaInstalled()
                            resumeTick++
                        }
                    }
                    override fun onActivityCreated(a: android.app.Activity, b: Bundle?) = Unit
                    override fun onActivityStarted(a: android.app.Activity) = Unit
                    override fun onActivityPaused(a: android.app.Activity) = Unit
                    override fun onActivityStopped(a: android.app.Activity) = Unit
                    override fun onActivitySaveInstanceState(a: android.app.Activity, b: Bundle) = Unit
                    override fun onActivityDestroyed(a: android.app.Activity) = Unit
                }
                application.registerActivityLifecycleCallbacks(listener)
                onDispose { application.unregisterActivityLifecycleCallbacks(listener) }
            }

            LaunchedEffect(resumeTick) {
                vpnReady = SystemChecks.isVpnPrepared(this@MainActivity)
                accessibilityOn = SystemChecks.isAccessibilityEnabled(this@MainActivity)
            }

            ZeyaoTheme(
                themeStyle = settings.themeStyle,
                typographyStyle = settings.typographyStyle,
                accent = Color(settings.accentColor),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    when {
                        !settings.onboarded || route == Route.RECONFIGURE -> {
                            OnboardingRoute(
                                initialProviderId = settings.providerId,
                                reconfigure = route == Route.RECONFIGURE || settings.onboarded,
                                onFinished = { route = Route.MAIN },
                            )
                        }
                        route == Route.SETTINGS -> {
                            SettingsScreen(
                                settings = settings,
                                apps = apps,
                                vpnReady = vpnReady,
                                accessibilityEnabled = accessibilityOn,
                                onBack = { route = Route.MAIN },
                                onUpdate = { transform -> scope.launch { app.settings.update(transform) } },
                                onToggleIntercept = { enabled ->
                                    scope.launch { handleInterceptToggle(enabled) }
                                },
                                onTogglePersistent = { enabled ->
                                    scope.launch { handlePersistentToggle(enabled) }
                                },
                                onOpenAccessibilitySettings = {
                                    startActivity(SystemChecks.accessibilitySettingsIntent())
                                },
                                onOpenVpnSettings = { prepareOrStartVpn() },
                                browsers = browsers,
                                isDefaultBrowser = isDefaultBrowser,
                                onRequestDefaultBrowser = {
                                    startActivity(BrowserHandoff.requestDefaultBrowserIntent(this@MainActivity))
                                },
                                caInstalled = caInstalled,
                                onToggleHttpsDecrypt = { enabled ->
                                    scope.launch { handleHttpsDecryptToggle(enabled) }
                                },
                                onInstallCa = { installZeyaoCa() },
                                onToggleClickIntercept = { enabled ->
                                    scope.launch { handleClickInterceptToggle(enabled) }
                                },
                                onToggleClickConsume = { enabled ->
                                    scope.launch { app.settings.setClickInterceptConsume(enabled) }
                                },
                            )
                        }
                        route == Route.OTHER -> {
                            OtherScreen(
                                currentProviderId = settings.providerId,
                                onBack = { route = Route.MAIN },
                                onReconfigureKey = { route = Route.RECONFIGURE },
                                onTestUrl = { raw ->
                                    val url = if (raw.startsWith("http")) raw else "https://$raw"
                                    InterceptController.startOrRefine(url, title = "", packageName = packageName)
                                },
                            )
                        }
                        route == Route.RECORD && openedRecord != null -> {
                            val rec = openedRecord!!
                            RecordDetailScreen(record = rec, onBack = {
                                route = Route.MAIN
                                openedRecord = null
                            })
                        }
                        else -> {
                            MainScreen(
                                settings = settings,
                                records = records,
                                interceptOn = settings.interceptEnabled,
                                vpnReady = vpnReady,
                                accessibilityEnabled = accessibilityOn,
                                onToggleIntercept = { enabled ->
                                    scope.launch { handleInterceptToggle(enabled) }
                                },
                                onOpenSettings = { route = Route.SETTINGS },
                                onOpenOther = { route = Route.OTHER },
                                onOpenRecord = { record ->
                                    openedRecord = record
                                    route = Route.RECORD
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingStartVpn && SystemChecks.isVpnPrepared(this)) {
            VpnController.start(this)
            pendingStartVpn = false
        }
    }

    private suspend fun handleInterceptToggle(enabled: Boolean) {
        val app = ZeyaoApp.instance
        if (enabled) {
            requestNotificationIfNeeded()
            app.settings.setInterceptEnabled(true)
            prepareOrStartVpn()
        } else {
            app.settings.setInterceptEnabled(false)
            VpnController.stop(this)
        }
    }

    private suspend fun handlePersistentToggle(enabled: Boolean) {
        val app = ZeyaoApp.instance
        app.settings.setPersistentEnabled(enabled)
        if (enabled) {
            requestNotificationIfNeeded()
            val current = app.settings.current()
            if (current.interceptEnabled) {
                prepareOrStartVpn()
            }
            if (!SystemChecks.isIgnoringBatteryOptimizations(this)) {
                runCatching { startActivity(SystemChecks.batteryOptimizationIntent(this)) }
            }
        }
    }

    private fun requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun prepareOrStartVpn(): Boolean {
        val prepare = VpnController.prepareIntent(this)
        return if (prepare != null) {
            pendingStartVpn = true
            vpnPrepare.launch(prepare)
            false
        } else {
            VpnController.start(this)
            true
        }
    }

    private suspend fun handleHttpsDecryptToggle(enabled: Boolean) {
        val app = ZeyaoApp.instance
        withContext(Dispatchers.IO) { LocalCa.ensure(this@MainActivity) }
        if (enabled && !LocalCa.isInstalled(this)) {
            installZeyaoCa()
        }
        app.settings.setHttpsDecryptEnabled(enabled)
        InterceptController.refreshCaInstalled()
    }

    private suspend fun handleClickInterceptToggle(enabled: Boolean) {
        val app = ZeyaoApp.instance
        app.settings.setClickInterceptEnabled(enabled)
        if (enabled) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "請先允許懸浮窗權限", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
                startActivity(intent)
            }
            com.wusper.zeyao.intercept.ClickInterceptOverlay.start(this)
        } else {
            com.wusper.zeyao.intercept.ClickInterceptOverlay.stop(this)
        }
    }

    private fun installZeyaoCa() {
        LocalCa.ensure(this)
        val exported = runCatching { LocalCa.exportToDownloads(this) }.getOrElse { e ->
            Toast.makeText(this, e.message ?: "無法寫入下載資料夾", Toast.LENGTH_LONG).show()
            return
        }
        if (Build.VERSION.SDK_INT < 30) {
            runCatching { startActivity(LocalCa.installIntent(this)) }
            Toast.makeText(this, "憑證已存到${exported.displayPath}", Toast.LENGTH_LONG).show()
            return
        }
        val message = "檔案在「" + exported.displayPath + "」。\n\n請到「設定 → 安全性 → 加密與憑證 → 安裝憑證 → CA 憑證」，選這個檔。"
        AlertDialog.Builder(this)
            .setTitle("已存到下載資料夾")
            .setMessage(message)
            .setPositiveButton("前往安裝") { _, _ ->
                runCatching { startActivity(LocalCa.installIntent(this)) }
            }
            .setNegativeButton("先不用", null)
            .show()
    }

    private enum class Route { MAIN, SETTINGS, OTHER, RECORD, RECONFIGURE }
}
