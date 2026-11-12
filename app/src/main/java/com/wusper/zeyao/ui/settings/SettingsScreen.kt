package com.wusper.zeyao.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.EnterButtonPosition
import com.wusper.zeyao.data.ThemeStyle
import com.wusper.zeyao.data.TypographyStyle
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction
import java.util.Locale

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    vpnReady: Boolean,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleIntercept: (Boolean) -> Unit,
    onTogglePersistent: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    browsers: List<Pair<String, String>>,
    isDefaultBrowser: Boolean,
    onRequestDefaultBrowser: () -> Unit,
    caInstalled: Boolean,
    onToggleHttpsDecrypt: (Boolean) -> Unit,
    onInstallCa: () -> Unit,
    onToggleClickIntercept: (Boolean) -> Unit,
    onToggleClickConsume: (Boolean) -> Unit,
) {
    var pickingApps by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var pickingBrowser by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction("返回", onBack)
            Spacer(Modifier.weight(1f))
            Text("設置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("攔截")
            SettingSwitch(
                title = "全域",
                subtitle = if (vpnReady) "攔截新聞網址，於正文打開前先出示擇要頁。" else "開啟時會請求本機 VPN 權限。",
                checked = settings.interceptEnabled,
                onChecked = onToggleIntercept,
            )
            SettingSwitch(
                title = "常駐",
                subtitle = "以前景服務保持攔截，並在重啟後嘗試恢復。系統可能會再問你略過電池優化。",
                checked = settings.persistentEnabled,
                onChecked = onTogglePersistent,
            )
            SettingSwitch(
                title = "特定時段",
                subtitle = "只在選定的星期與時段攔截。跨日（例如 22:00–07:00）也支援。",
                checked = settings.scheduleEnabled,
                onChecked = { enabled -> onUpdate { s -> s.copy(scheduleEnabled = enabled) } },
            )
            if (settings.scheduleEnabled) {
                DayRow(selected = settings.scheduleDays, onChange = { days -> onUpdate { it.copy(scheduleDays = days) } })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeChip("開始 ${formatMinutes(settings.scheduleStartMinutes)}") { pickingStart = true }
                    TimeChip("結束 ${formatMinutes(settings.scheduleEndMinutes)}") { pickingEnd = true }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("特定 App")
            val count = settings.targetPackages.size
            Text(
                if (count == 0) "目前：全部 App。點此選擇只攔截特定 App。"
                else "目前：已選 $count 個 App。清空後視為全部。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pickingApps = true }
                    .padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("權限")
            PermissionLine(
                title = "本機 VPN",
                ok = vpnReady,
                okText = "已授權，流量只在本機轉發",
                badText = "未授權，點此開啟",
                onClick = onOpenVpnSettings,
            )
            PermissionLine(
                title = "無障礙服務",
                ok = accessibilityEnabled,
                okText = "已開啟，只讀取瀏覽器網址列",
                badText = "未開啟，點此前往系統設定",
                onClick = onOpenAccessibilitySettings,
            )


            Spacer(Modifier.height(8.dp))
            SectionLabel("點擊攔截（微信等）")
            SettingSwitch(
                title = "點擊攔截",
                subtitle = "預設關閉。開啟後會出現小型懸浮窗。點懸浮窗一次即「武裝」下一次點擊。微信與 Google 發現頁會優先抽出連結；微信若節點沒有網址，可先長按複製連結再點一次。需要無障礙與懸浮窗權限。",
                checked = settings.clickInterceptEnabled,
                onChecked = onToggleClickIntercept,
            )
            SettingSwitch(
                title = "消耗原點擊",
                subtitle = "預設關閉。開啟後，當擇要出現時會嘗試立刻返回，減少原頁面同時載入。部分 App 仍可能繼續跳轉。",
                checked = settings.clickInterceptConsume,
                onChecked = onToggleClickConsume,
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("HTTPS 解密")
            SettingSwitch(
                title = "解密 HTTPS",
                subtitle = if (caInstalled) {
                    "已安裝擇要憑證。開啟後，本機 VPN 會在這部裝置上解開 HTTPS，才能讀到完整文章路徑與頁面正文。系統可能顯示連線警告；部分只信任系統憑證的 App 或網站會連不上。此開關預設關閉。"
                } else {
                    "須先安裝擇要憑證才會生效。點「擇要憑證」會把檔案存到下載資料夾；再到「設定 → 安全性 → 加密與憑證 → 安裝憑證 → CA 憑證」選「擇要CA.crt」。部分網站或 App 可能因此無法連線。"
                },
                checked = settings.httpsDecryptEnabled,
                onChecked = onToggleHttpsDecrypt,
            )
            PermissionLine(
                title = "擇要憑證",
                ok = caInstalled,
                okText = "已安裝為使用者憑證。點此可再把「擇要CA.crt」存到下載資料夾。",
                badText = "未安裝。點此會把「擇要CA.crt」存到下載資料夾，再顯示安裝步驟。",
                onClick = onInstallCa,
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("預設瀏覽器")
            PermissionLine(
                title = "設為預設瀏覽器",
                ok = isDefaultBrowser,
                okText = "擇要目前是預設瀏覽器。不是新聞的連結會轉到下面指定的瀏覽器。",
                badText = "未設定。點此向系統申請，打開連結時才能在進頁面前攔截。",
                onClick = onRequestDefaultBrowser,
            )
            val handoffName = browsers.firstOrNull { it.first == settings.handoffBrowserPackage }?.second
                ?: if (settings.handoffBrowserPackage.isBlank()) "尚未指定，將使用裝置上的常用瀏覽器" else settings.handoffBrowserPackage
            Text(
                "實際瀏覽器：$handoffName。點此選擇「進入」或非新聞連結要跳轉的瀏覽器。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pickingBrowser = true }
                    .padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel("顏色")
            val accents = listOf(
                0xFFB33A2BL, 0xFF1F4E3DL, 0xFF1C3A5FL, 0xFF7A2E4AL, 0xFF8A5A18L, 0xFF2E2A28L,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                accents.forEach { value ->
                    val color = Color(value)
                    val selected = settings.accentColor == value
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { onUpdate { it.copy(accentColor = value) } },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("風格")
            ThemeStyle.entries.forEach { style ->
                ChoiceRow(
                    title = when (style) {
                        ThemeStyle.FOLLOW_SYSTEM -> "跟隨系統"
                        ThemeStyle.DARK -> "深色"
                        ThemeStyle.LIGHT -> "淺色"
                    },
                    selected = settings.themeStyle == style,
                    onClick = { onUpdate { it.copy(themeStyle = style) } },
                )
            }
            Spacer(Modifier.height(4.dp))
            TypographyStyle.entries.forEach { style ->
                ChoiceRow(
                    title = when (style) {
                        TypographyStyle.EDITORIAL -> "編輯體（較疏朗）"
                        TypographyStyle.COMPACT -> "緊湊"
                    },
                    selected = settings.typographyStyle == style,
                    onClick = { onUpdate { it.copy(typographyStyle = style) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("進入按鈕位置")
            EnterButtonPosition.entries.forEach { pos ->
                ChoiceRow(
                    title = when (pos) {
                        EnterButtonPosition.LEFT -> "兩個按鈕靠左"
                        EnterButtonPosition.RIGHT -> "兩個按鈕靠右"
                        EnterButtonPosition.BOTTOM_CENTER -> "底部置中"
                        EnterButtonPosition.ENTER_LEFT -> "進入在左、返回在右"
                        EnterButtonPosition.ENTER_RIGHT -> "進入在右、返回在左"
                    },
                    selected = settings.enterButtonPosition == pos,
                    onClick = { onUpdate { it.copy(enterButtonPosition = pos) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("生成")
            SettingSwitch(
                title = "擇要過程中退出後繼續生成",
                subtitle = "開啟時，按返回或進入後仍會在背景跑完並寫入最近紀錄；關閉則立即停止。",
                checked = settings.continueOnExit,
                onChecked = { enabled -> onUpdate { s -> s.copy(continueOnExit = enabled) } },
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "擇要 GitHub",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/wusui1325-dotcom/zeyao"),
                        )
                        runCatching { context.startActivity(intent) }
                    }
                    .padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickingStart) {
        TimePickDialog(
            title = "開始時間",
            minutes = settings.scheduleStartMinutes,
            onDismiss = { pickingStart = false },
            onConfirm = {
                onUpdate { s -> s.copy(scheduleStartMinutes = it) }
                pickingStart = false
            },
        )
    }
    if (pickingEnd) {
        TimePickDialog(
            title = "結束時間",
            minutes = settings.scheduleEndMinutes,
            onDismiss = { pickingEnd = false },
            onConfirm = {
                onUpdate { s -> s.copy(scheduleEndMinutes = it) }
                pickingEnd = false
            },
        )
    }
    if (pickingBrowser) {
        BrowserPickerDialog(
            browsers = browsers,
            selected = settings.handoffBrowserPackage,
            onDismiss = { pickingBrowser = false },
            onConfirm = { pkg ->
                onUpdate { it.copy(handoffBrowserPackage = pkg) }
                pickingBrowser = false
            },
        )
    }
    if (pickingApps) {
        AppPickerDialog(
            apps = apps,
            selected = settings.targetPackages,
            onDismiss = { pickingApps = false },
            onConfirm = { set ->
                onUpdate { it.copy(targetPackages = set) }
                pickingApps = false
            },
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionLine(title: String, ok: Boolean, okText: String, badText: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            if (ok) okText else badText,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun DayRow(selected: Int, onChange: (Int) -> Unit) {
    val labels = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val bit = 1 shl index
            val on = selected and bit != 0
            val bg = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable {
                        val next = if (on) selected and bit.inv() else selected or bit
                        onChange(if (next == 0) selected else next)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(title: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = (minutes / 60).coerceIn(0, 23),
        initialMinute = (minutes % 60).coerceIn(0, 59),
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AppPickerDialog(
    apps: List<LaunchableApp>,
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("特定 App") },
        text = {
            Column {
                Text(
                    "不選任何 App 表示全部。只勾選時，僅攔截這些 App 打開的新聞頁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { current = emptySet() }) { Text("全部（清空）") }
                }
                Column(
                    Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    apps.forEach { app ->
                        val checked = app.packageName in current
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = if (checked) current - app.packageName else current + app.packageName
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { on ->
                                current = if (on) current + app.packageName else current - app.packageName
                            })
                            Column(Modifier.weight(1f)) {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("完成") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun BrowserPickerDialog(
    browsers: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("實際瀏覽器") },
        text = {
            Column(
                Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                browsers.forEach { (pkg, label) ->
                    ChoiceRow(
                        title = label,
                        subtitle = pkg,
                        selected = pkg == selected,
                        onClick = { onConfirm(pkg) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60).coerceIn(0, 23)
    val m = (minutes % 60).coerceIn(0, 59)
    return String.format(Locale.TRADITIONAL_CHINESE, "%02d:%02d", h, m)
}
