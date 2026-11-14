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
import androidx.compose.ui.res.stringResource
import com.wusper.zeyao.R
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
    var pickingCooldown by remember { mutableStateOf(false) }
    var confirmHttps by remember { mutableStateOf(false) }
    var confirmConsume by remember { mutableStateOf(false) }
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
            TopAction(stringResource(R.string.back), onBack)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
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
            SectionLabel(stringResource(R.string.section_intercept))
            SettingSwitch(
                title = "所有網頁攔截",
                subtitle = if (settings.interceptAllPages) {
                    if (settings.askBeforeSummary) "已開。攔截後會詢問是否擇要。" else "已開。攔截後一律直接擇要。"
                } else {
                    "開啟後攔截所有網頁，不做新聞驗證，直接擇要。"
                },
                checked = settings.interceptAllPages,
                onChecked = { enabled -> onUpdate { it.copy(interceptAllPages = enabled) } },
            )
            SettingSwitch(
                title = "攔截後一律詢問",
                subtitle = "開啟：彈窗問是否擇要。關閉：一律直接擇要。",
                checked = settings.askBeforeSummary,
                onChecked = { enabled -> onUpdate { it.copy(askBeforeSummary = enabled) } },
            )
            SettingSwitch(
                title = stringResource(R.string.global_intercept),
                subtitle = if (vpnReady) stringResource(R.string.global_intercept_on) else stringResource(R.string.global_intercept_off),
                checked = settings.interceptEnabled,
                onChecked = onToggleIntercept,
            )
            SettingSwitch(
                title = stringResource(R.string.persistent),
                subtitle = stringResource(R.string.persistent_hint),
                checked = settings.persistentEnabled,
                onChecked = onTogglePersistent,
            )
            SettingSwitch(
                title = stringResource(R.string.schedule_enabled),
                subtitle = stringResource(R.string.schedule_hint) + "（以裝置時區計算）",
                checked = settings.scheduleEnabled,
                onChecked = { enabled -> onUpdate { s -> s.copy(scheduleEnabled = enabled) } },
            )
            if (settings.scheduleEnabled) {
                DayRow(selected = settings.scheduleDays, onChange = { days -> onUpdate { it.copy(scheduleDays = days) } })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeChip(stringResource(R.string.schedule_start, formatMinutes(settings.scheduleStartMinutes))) { pickingStart = true }
                    TimeChip(stringResource(R.string.schedule_end, formatMinutes(settings.scheduleEndMinutes))) { pickingEnd = true }
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_apps))
            val count = settings.targetPackages.size
            Text(
                if (count == 0) stringResource(R.string.apps_all)
                else stringResource(R.string.apps_selected, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pickingApps = true }
                    .padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_permissions))
            PermissionLine(
                title = stringResource(R.string.perm_vpn),
                ok = vpnReady,
                okText = stringResource(R.string.perm_vpn_ok),
                badText = stringResource(R.string.perm_vpn_bad),
                onClick = onOpenVpnSettings,
            )
            PermissionLine(
                title = stringResource(R.string.perm_a11y),
                ok = accessibilityEnabled,
                okText = stringResource(R.string.perm_a11y_ok),
                badText = stringResource(R.string.perm_a11y_bad),
                onClick = onOpenAccessibilitySettings,
            )


            SectionLabel(stringResource(R.string.section_cooldown))
            val cooldownLabel = cooldownLabel(settings.siteCooldownSeconds)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pickingCooldown = true }
                    .padding(vertical = 10.dp),
            ) {
                Text(stringResource(R.string.cooldown_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.cooldown_current, cooldownLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_click))
            SettingSwitch(
                title = stringResource(R.string.click_intercept),
                subtitle = stringResource(R.string.click_intercept_hint),
                checked = settings.clickInterceptEnabled,
                onChecked = onToggleClickIntercept,
            )
            SettingSwitch(
                title = stringResource(R.string.click_consume),
                subtitle = stringResource(R.string.click_consume_hint),
                checked = settings.clickInterceptConsume,
                onChecked = { enabled ->
                    if (enabled) confirmConsume = true else onToggleClickConsume(false)
                },
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_https))
            SettingSwitch(
                title = stringResource(R.string.https_decrypt),
                subtitle = if (caInstalled) stringResource(R.string.https_decrypt_on) else stringResource(R.string.https_decrypt_off),
                checked = settings.httpsDecryptEnabled,
                onChecked = { enabled ->
                    if (enabled) confirmHttps = true else onToggleHttpsDecrypt(false)
                },
            )
            PermissionLine(
                title = stringResource(R.string.ca_cert),
                ok = caInstalled,
                okText = stringResource(R.string.ca_ok),
                badText = stringResource(R.string.ca_bad),
                onClick = onInstallCa,
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_browser))
            PermissionLine(
                title = stringResource(R.string.set_default_browser),
                ok = isDefaultBrowser,
                okText = stringResource(R.string.default_browser_ok),
                badText = stringResource(R.string.default_browser_bad),
                onClick = onRequestDefaultBrowser,
            )
            val handoffName = browsers.firstOrNull { it.first == settings.handoffBrowserPackage }?.second
                ?: if (settings.handoffBrowserPackage.isBlank()) stringResource(R.string.handoff_unset) else settings.handoffBrowserPackage
            Text(
                stringResource(R.string.handoff_hint, handoffName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { pickingBrowser = true }
                    .padding(vertical = 10.dp),
            )

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_color))
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
            SectionLabel(stringResource(R.string.section_style))
            ThemeStyle.entries.forEach { style ->
                ChoiceRow(
                    title = when (style) {
                        ThemeStyle.FOLLOW_SYSTEM -> stringResource(R.string.theme_system)
                        ThemeStyle.DARK -> stringResource(R.string.theme_dark)
                        ThemeStyle.LIGHT -> stringResource(R.string.theme_light)
                        ThemeStyle.HIGH_CONTRAST -> "高對比"
                    },
                    selected = settings.themeStyle == style,
                    onClick = { onUpdate { it.copy(themeStyle = style) } },
                )
            }
            Spacer(Modifier.height(4.dp))
            TypographyStyle.entries.forEach { style ->
                ChoiceRow(
                    title = when (style) {
                        TypographyStyle.EDITORIAL -> stringResource(R.string.typo_editorial)
                        TypographyStyle.COMPACT -> stringResource(R.string.typo_compact)
                    },
                    selected = settings.typographyStyle == style,
                    onClick = { onUpdate { it.copy(typographyStyle = style) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_enter_pos))
            EnterButtonPosition.entries.forEach { pos ->
                ChoiceRow(
                    title = when (pos) {
                        EnterButtonPosition.LEFT -> stringResource(R.string.btn_left)
                        EnterButtonPosition.RIGHT -> stringResource(R.string.btn_right)
                        EnterButtonPosition.BOTTOM_CENTER -> stringResource(R.string.btn_bottom)
                        EnterButtonPosition.ENTER_LEFT -> stringResource(R.string.btn_enter_left)
                        EnterButtonPosition.ENTER_RIGHT -> stringResource(R.string.btn_enter_right)
                    },
                    selected = settings.enterButtonPosition == pos,
                    onClick = { onUpdate { it.copy(enterButtonPosition = pos) } },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_summary))
            ChoiceRow(
                title = stringResource(R.string.pref_length),
                subtitle = when (settings.summaryLength) {
                    com.wusper.zeyao.data.SummaryLength.SHORT -> stringResource(R.string.shorter)
                    com.wusper.zeyao.data.SummaryLength.NORMAL -> "一般"
                    com.wusper.zeyao.data.SummaryLength.LONG -> stringResource(R.string.longer)
                },
                selected = false,
                onClick = {
                    val next = when (settings.summaryLength) {
                        com.wusper.zeyao.data.SummaryLength.SHORT -> com.wusper.zeyao.data.SummaryLength.NORMAL
                        com.wusper.zeyao.data.SummaryLength.NORMAL -> com.wusper.zeyao.data.SummaryLength.LONG
                        com.wusper.zeyao.data.SummaryLength.LONG -> com.wusper.zeyao.data.SummaryLength.SHORT
                    }
                    onUpdate { it.copy(summaryLength = next) }
                },
            )
            SettingSwitch(
                title = "反詐、防騙",
                subtitle = "在提示詞加入安全提醒，並要求摘要開頭標出欺詐可能性與潛在威脅風險。",
                checked = settings.antiFraudEnabled,
                onChecked = { enabled -> onUpdate { it.copy(antiFraudEnabled = enabled) } },
            )
            SettingSwitch(
                title = "真實性及時效性確認",
                subtitle = "擇要前先抽出至少 10 個關鍵事實，對每個同時搜索，把結果塞回上下文。",
                checked = settings.authenticityCheckEnabled,
                onChecked = { enabled -> onUpdate { it.copy(authenticityCheckEnabled = enabled) } },
            )
            SettingSwitch(
                title = "備用搜索",
                subtitle = "官方 DeepSeek Search 無結果或失敗時，改用 DuckDuckGo。",
                checked = settings.backupSearchEnabled,
                onChecked = { enabled -> onUpdate { it.copy(backupSearchEnabled = enabled) } },
            )
            SettingSwitch(
                title = stringResource(R.string.pref_search),
                subtitle = stringResource(R.string.pref_search_hint),
                checked = settings.searchNewsEnabled,
                onChecked = { enabled -> onUpdate { it.copy(searchNewsEnabled = enabled) } },
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel("實驗性")
            var showImageWarn by remember { mutableStateOf(false) }
            SettingSwitch(
                title = "圖片辨識",
                subtitle = "預設關閉。開啟後每秒截圖存到 app 私有目錄（最多 3 張，超出刪最舊）。只有 VPN 偵測到網頁時，才把上一張送給最便宜模型抽標題或連結，再搜一次塞進擇要。",
                checked = settings.imageRecognitionEnabled,
                onChecked = { enabled ->
                    if (enabled) showImageWarn = true
                    else {
                        onUpdate { it.copy(imageRecognitionEnabled = false) }
                        com.wusper.zeyao.intercept.ScreenshotBuffer.enabled = false
                    }
                },
            )
            if (showImageWarn) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showImageWarn = false },
                    title = { Text("開啟圖片辨識？") },
                    text = { Text("這是實驗功能。開啟後會每秒截圖並存在這部裝置的 app 私有目錄（最多 3 張）。只有在攔截新聞時，才會把上一張截圖上傳到你的 AI API（最便宜模型）以抽出標題或連結。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            onUpdate { it.copy(imageRecognitionEnabled = true) }
                            com.wusper.zeyao.intercept.ScreenshotBuffer.enabled = true
                            showImageWarn = false
                        }) { Text("確認開啟") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showImageWarn = false }) { Text("取消") }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            SectionLabel("狀態")
            Text(
                "已攔截次數：${settings.interceptCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val days = remember { com.wusper.zeyao.mitm.LocalCa.daysUntilExpiry() }
            if (days in 0..30) {
                Text(
                    "擇要 CA 將在 $days 天後過期，建議重新安裝。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel("生成參數")
            Text(
                "溫度：${String.format("%.1f", settings.temperature)}　最大字數：${settings.maxTokens}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "備用模型：${if (settings.fallbackModels.isBlank()) "內建" else settings.fallbackModels}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SettingSwitch(
                title = "僅解密已知新聞域名",
                subtitle = "開啟時 HTTPS 解密只對新聞網站生效，降低風險。",
                checked = settings.httpsDecryptNewsOnly,
                onChecked = { enabled -> onUpdate { it.copy(httpsDecryptNewsOnly = enabled) } },
            )
            SettingSwitch(
                title = stringResource(R.string.continue_on_exit),
                subtitle = stringResource(R.string.continue_on_exit_hint),
                checked = settings.continueOnExit,
                onChecked = { enabled -> onUpdate { s -> s.copy(continueOnExit = enabled) } },
            )
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.github_link),
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
            Spacer(Modifier.height(24.dp))
            Text(
                "版本 3.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickingStart) {
        TimePickDialog(
            title = stringResource(R.string.time_start),
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
            title = stringResource(R.string.time_end),
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
    if (pickingCooldown) {
        CooldownPickerDialog(
            selected = settings.siteCooldownSeconds,
            onDismiss = { pickingCooldown = false },
            onConfirm = { seconds ->
                onUpdate { it.copy(siteCooldownSeconds = seconds) }
                pickingCooldown = false
            },
        )
    }
    if (confirmHttps) {
        AlertDialog(
            onDismissRequest = { confirmHttps = false },
            title = { Text("開啟 HTTPS 解密？") },
            text = { Text("這會使用本機 CA 解密流量。僅建議在信任的網路使用，並只對新聞域名生效。") },
            confirmButton = {
                TextButton(onClick = {
                    onToggleHttpsDecrypt(true)
                    confirmHttps = false
                }) { Text("確認開啟") }
            },
            dismissButton = {
                TextButton(onClick = { confirmHttps = false }) { Text("取消") }
            },
        )
    }
    if (confirmConsume) {
        AlertDialog(
            onDismissRequest = { confirmConsume = false },
            title = { Text("開啟點擊攔截消耗？") },
            text = { Text("開啟後會嘗試阻擋原始點擊。可能影響部分 App 操作，確認後再開啟。") },
            confirmButton = {
                TextButton(onClick = {
                    onToggleClickConsume(true)
                    confirmConsume = false
                }) { Text("確認開啟") }
            },
            dismissButton = {
                TextButton(onClick = { confirmConsume = false }) { Text("取消") }
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
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.section_apps)) },
        text = {
            Column {
                Text(
                    "不選任何 App 表示全部。只勾選時，僅攔截這些 App 打開的新聞頁。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = { current = emptySet() }) { Text(stringResource(R.string.apps_clear)) }
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
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
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
        title = { Text(stringResource(R.string.actual_browser)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun cooldownLabel(seconds: Int): String = when (seconds) {
    10 -> "10 秒"
    30 -> "30 秒"
    60 -> "1 分鐘"
    120 -> "2 分鐘"
    180 -> "3 分鐘"
    300 -> "5 分鐘"
    600 -> "10 分鐘"
    else -> "${seconds} 秒"
}

@Composable
private fun CooldownPickerDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val options = listOf(10, 30, 60, 120, 180, 300, 600)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cooldown_dialog_title)) },
        text = {
            Column(
                Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { seconds ->
                    ChoiceRow(
                        title = cooldownLabel(seconds),
                        subtitle = if (seconds == 600) "預設" else null,
                        selected = seconds == selected,
                        onClick = { onConfirm(seconds) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60).coerceIn(0, 23)
    val m = (minutes % 60).coerceIn(0, 59)
    return String.format(Locale.TRADITIONAL_CHINESE, "%02d:%02d", h, m)
}
