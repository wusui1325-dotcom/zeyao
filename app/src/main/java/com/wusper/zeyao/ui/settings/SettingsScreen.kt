package com.wusper.zeyao.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.BuildConfig
import com.wusper.zeyao.R
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.EnterButtonPosition
import com.wusper.zeyao.data.ThemeStyle
import com.wusper.zeyao.data.TypographyStyle
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.FlatCard
import com.wusper.zeyao.ui.components.IconBadge
import com.wusper.zeyao.ui.components.PillAction
import com.wusper.zeyao.ui.components.PillShape
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction
import com.wusper.zeyao.ui.components.ZeyaoIcons
import com.wusper.zeyao.ui.lock.BiometricGate
import com.wusper.zeyao.ui.settings.sections.HttpsSection
import com.wusper.zeyao.ui.settings.sections.InterceptSection
import com.wusper.zeyao.ui.settings.sections.StatsSection
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
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(stringResource(R.string.back), onBack, icon = ZeyaoIcons.ArrowLeft)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(ZeyaoIcons.Settings, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InterceptSection(
                settings = settings,
                vpnReady = vpnReady,
                onUpdate = onUpdate,
                onToggleIntercept = onToggleIntercept,
                onTogglePersistent = onTogglePersistent,
            )
            if (settings.scheduleEnabled) {
                FlatCard(padding = PaddingValues(16.dp)) {
                    DayRow(selected = settings.scheduleDays, onChange = { days -> onUpdate { it.copy(scheduleDays = days) } })
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeChip(stringResource(R.string.schedule_start, formatMinutes(settings.scheduleStartMinutes))) { pickingStart = true }
                        TimeChip(stringResource(R.string.schedule_end, formatMinutes(settings.scheduleEndMinutes))) { pickingEnd = true }
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_apps), icon = ZeyaoIcons.Grid)
            val count = settings.targetPackages.size
            FlatCard(padding = PaddingValues(16.dp), onClick = { pickingApps = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = ZeyaoIcons.Grid, active = count > 0)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (count == 0) stringResource(R.string.apps_all)
                        else stringResource(R.string.apps_selected, count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(ZeyaoIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            SectionLabel(stringResource(R.string.section_permissions), icon = ZeyaoIcons.ShieldCheck)
            FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                PermissionLine(
                    title = stringResource(R.string.perm_vpn),
                    icon = ZeyaoIcons.Shield,
                    ok = vpnReady,
                    okText = stringResource(R.string.perm_vpn_ok),
                    badText = stringResource(R.string.perm_vpn_bad),
                    onClick = onOpenVpnSettings,
                )
                PermissionLine(
                    title = stringResource(R.string.perm_a11y),
                    icon = ZeyaoIcons.Eye,
                    ok = accessibilityEnabled,
                    okText = stringResource(R.string.perm_a11y_ok),
                    badText = stringResource(R.string.perm_a11y_bad),
                    onClick = onOpenAccessibilitySettings,
                )
            }

            SectionLabel(stringResource(R.string.section_cooldown), icon = ZeyaoIcons.Clock)
            val cooldownLabel = cooldownLabelText(settings.siteCooldownSeconds)
            FlatCard(padding = PaddingValues(16.dp), onClick = { pickingCooldown = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = ZeyaoIcons.Clock)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.cooldown_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.cooldown_current, cooldownLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(ZeyaoIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            SectionLabel(stringResource(R.string.section_skipped), icon = ZeyaoIcons.Block)
            FlatCard(padding = PaddingValues(16.dp)) {
                if (settings.skippedHosts.isEmpty()) {
                    Text(
                        stringResource(R.string.no_skipped_sites),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        settings.skippedHosts.sorted().forEach { host ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(ZeyaoIcons.Globe, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(host, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                PillAction(
                                    text = stringResource(R.string.remove_skipped_site),
                                    icon = ZeyaoIcons.Close,
                                    onClick = { onUpdate { it.copy(skippedHosts = it.skippedHosts - host) } },
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel(stringResource(R.string.section_click), icon = ZeyaoIcons.Pointer)
            FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                SettingSwitch(
                    title = stringResource(R.string.click_intercept),
                    subtitle = stringResource(R.string.click_intercept_hint),
                    icon = ZeyaoIcons.Pointer,
                    checked = settings.clickInterceptEnabled,
                    onChecked = onToggleClickIntercept,
                )
                SettingSwitch(
                    title = stringResource(R.string.click_consume),
                    subtitle = stringResource(R.string.click_consume_hint),
                    icon = ZeyaoIcons.Block,
                    checked = settings.clickInterceptConsume,
                    onChecked = { enabled ->
                        if (enabled) confirmConsume = true else onToggleClickConsume(false)
                    },
                )
            }

            HttpsSection(
                settings = settings,
                caInstalled = caInstalled,
                onUpdate = onUpdate,
                onToggleHttpsDecrypt = onToggleHttpsDecrypt,
                onRequestEnableHttps = { confirmHttps = true },
                onInstallCa = onInstallCa,
            )

            SectionLabel(stringResource(R.string.section_browser), icon = ZeyaoIcons.Compass)
            FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                PermissionLine(
                    title = stringResource(R.string.set_default_browser),
                    icon = ZeyaoIcons.Compass,
                    ok = isDefaultBrowser,
                    okText = stringResource(R.string.default_browser_ok),
                    badText = stringResource(R.string.default_browser_bad),
                    onClick = onRequestDefaultBrowser,
                )
                val handoffName = browsers.firstOrNull { it.first == settings.handoffBrowserPackage }?.second
                    ?: if (settings.handoffBrowserPackage.isBlank()) stringResource(R.string.handoff_unset) else settings.handoffBrowserPackage
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { pickingBrowser = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(icon = ZeyaoIcons.ArrowUpRight)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.handoff_hint, handoffName),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(ZeyaoIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            SectionLabel(stringResource(R.string.section_style), icon = ZeyaoIcons.Contrast)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeStyle.entries.forEach { style ->
                    ChoiceRow(
                        title = when (style) {
                            ThemeStyle.FOLLOW_SYSTEM -> stringResource(R.string.theme_system)
                            ThemeStyle.DARK -> stringResource(R.string.theme_dark)
                            ThemeStyle.LIGHT -> stringResource(R.string.theme_light)
                            ThemeStyle.HIGH_CONTRAST -> stringResource(R.string.theme_high_contrast)
                        },
                        icon = ZeyaoIcons.Contrast,
                        selected = settings.themeStyle == style,
                        onClick = {
                            onUpdate {
                                it.copy(
                                    themeStyle = style,
                                    highContrastEnabled = style == ThemeStyle.HIGH_CONTRAST,
                                )
                            }
                        },
                    )
                }
                Spacer(Modifier.height(2.dp))
                TypographyStyle.entries.forEach { style ->
                    ChoiceRow(
                        title = when (style) {
                            TypographyStyle.EDITORIAL -> stringResource(R.string.typo_editorial)
                            TypographyStyle.COMPACT -> stringResource(R.string.typo_compact)
                        },
                        icon = ZeyaoIcons.Document,
                        selected = settings.typographyStyle == style,
                        onClick = { onUpdate { it.copy(typographyStyle = style) } },
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_enter_pos), icon = ZeyaoIcons.Layout)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EnterButtonPosition.entries.forEach { pos ->
                    ChoiceRow(
                        title = when (pos) {
                            EnterButtonPosition.LEFT -> stringResource(R.string.btn_left)
                            EnterButtonPosition.RIGHT -> stringResource(R.string.btn_right)
                            EnterButtonPosition.BOTTOM_CENTER -> stringResource(R.string.btn_bottom)
                            EnterButtonPosition.ENTER_LEFT -> stringResource(R.string.btn_enter_left)
                            EnterButtonPosition.ENTER_RIGHT -> stringResource(R.string.btn_enter_right)
                        },
                        icon = ZeyaoIcons.Layout,
                        selected = settings.enterButtonPosition == pos,
                        onClick = { onUpdate { it.copy(enterButtonPosition = pos) } },
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_summary), icon = ZeyaoIcons.Document)
            ChoiceRow(
                title = stringResource(R.string.pref_length),
                subtitle = when (settings.summaryLength) {
                    com.wusper.zeyao.data.SummaryLength.SHORT -> stringResource(R.string.shorter)
                    com.wusper.zeyao.data.SummaryLength.NORMAL -> stringResource(R.string.length_normal)
                    com.wusper.zeyao.data.SummaryLength.LONG -> stringResource(R.string.longer)
                },
                icon = ZeyaoIcons.Sliders,
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
            FlatCard(padding = PaddingValues(16.dp)) {
                var fontScale by remember(settings.summaryFontScale) { mutableStateOf(settings.summaryFontScale) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(ZeyaoIcons.TextLarger, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.font_size_current, String.format(Locale.US, "%.2f", fontScale)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    onValueChangeFinished = {
                        onUpdate { it.copy(summaryFontScale = fontScale.coerceIn(0.85f, 1.6f)) }
                    },
                    valueRange = 0.85f..1.6f,
                    steps = 4,
                )
                SettingSwitch(
                    title = stringResource(R.string.anti_fraud),
                    subtitle = stringResource(R.string.anti_fraud_hint),
                    icon = ZeyaoIcons.ShieldCheck,
                    checked = settings.antiFraudEnabled,
                    onChecked = { enabled -> onUpdate { it.copy(antiFraudEnabled = enabled) } },
                )
                SettingSwitch(
                    title = stringResource(R.string.authenticity),
                    subtitle = stringResource(R.string.authenticity_hint),
                    icon = ZeyaoIcons.Check,
                    checked = settings.authenticityCheckEnabled,
                    onChecked = { enabled -> onUpdate { it.copy(authenticityCheckEnabled = enabled) } },
                )
                SettingSwitch(
                    title = stringResource(R.string.pref_search),
                    subtitle = stringResource(R.string.pref_search_hint),
                    icon = ZeyaoIcons.Search,
                    checked = settings.searchNewsEnabled,
                    onChecked = { enabled -> onUpdate { it.copy(searchNewsEnabled = enabled) } },
                )
            }

            SectionLabel(stringResource(R.string.section_experimental), icon = ZeyaoIcons.Sparkle)
            var showImageWarn by remember { mutableStateOf(false) }
            FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                SettingSwitch(
                    title = stringResource(R.string.image_recognition),
                    subtitle = stringResource(R.string.image_recognition_hint),
                    icon = ZeyaoIcons.Image,
                    checked = settings.imageRecognitionEnabled,
                    onChecked = { enabled ->
                        if (enabled) showImageWarn = true
                        else {
                            onUpdate { it.copy(imageRecognitionEnabled = false) }
                            com.wusper.zeyao.intercept.ScreenshotBuffer.enabled = false
                        }
                    },
                )
            }
            if (showImageWarn) {
                AlertDialog(
                    onDismissRequest = { showImageWarn = false },
                    title = { Text(stringResource(R.string.image_recognition_warn_title)) },
                    text = { Text(stringResource(R.string.image_recognition_warn_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            onUpdate { it.copy(imageRecognitionEnabled = true) }
                            com.wusper.zeyao.intercept.ScreenshotBuffer.enabled = true
                            showImageWarn = false
                        }) {
                            Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.confirm_enable))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImageWarn = false }) {
                            Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }

            StatsSection(settings)

            SectionLabel(stringResource(R.string.section_privacy), icon = ZeyaoIcons.Lock)
            FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                SettingSwitch(
                    title = stringResource(R.string.biometric_lock),
                    subtitle = stringResource(R.string.biometric_lock_hint),
                    icon = ZeyaoIcons.Lock,
                    checked = settings.biometricUnlockEnabled,
                    onChecked = { enabled ->
                        if (enabled && !BiometricGate.canAuthenticate(context)) {
                            Toast.makeText(context, context.getString(R.string.biometric_unavailable), Toast.LENGTH_LONG).show()
                        } else {
                            onUpdate { it.copy(biometricUnlockEnabled = enabled) }
                        }
                    },
                )
                SettingSwitch(
                    title = stringResource(R.string.continue_on_exit),
                    subtitle = stringResource(R.string.continue_on_exit_hint),
                    icon = ZeyaoIcons.Play,
                    checked = settings.continueOnExit,
                    onChecked = { enabled -> onUpdate { s -> s.copy(continueOnExit = enabled) } },
                )
            }

            Spacer(Modifier.height(6.dp))
            FlatCard(
                padding = PaddingValues(16.dp),
                onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/wusui1325-dotcom/zeyao"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(icon = ZeyaoIcons.Code)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.github_link),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(ZeyaoIcons.ArrowUpRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Text(
                stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
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
            title = { Text(stringResource(R.string.confirm_https_title)) },
            text = { Text(stringResource(R.string.confirm_https_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onToggleHttpsDecrypt(true)
                    confirmHttps = false
                }) {
                    Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.confirm_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmHttps = false }) {
                    Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (confirmConsume) {
        AlertDialog(
            onDismissRequest = { confirmConsume = false },
            title = { Text(stringResource(R.string.confirm_consume_title)) },
            text = { Text(stringResource(R.string.confirm_consume_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onToggleClickConsume(true)
                    confirmConsume = false
                }) {
                    Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.confirm_enable))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmConsume = false }) {
                    Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cancel))
                }
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
internal fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon = icon, active = checked)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun PermissionLine(
    title: String,
    icon: ImageVector,
    ok: Boolean,
    okText: String,
    badText: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, active = ok)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                if (ok) okText else badText,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) colors.onSurfaceVariant else colors.onSurface,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (ok) ZeyaoIcons.Check else ZeyaoIcons.ChevronRight,
            contentDescription = null,
            tint = if (ok) colors.onSurface else colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DayRow(selected: Int, onChange: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.weekday_sun),
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat),
    )
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val bit = 1 shl index
            val on = selected and bit != 0
            val bg = if (on) colors.primary else colors.surfaceVariant
            val fg = if (on) colors.onPrimary else colors.onSurface
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .border(1.dp, if (on) colors.primary else colors.outline, CircleShape)
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
    PillAction(text = text, icon = ZeyaoIcons.Clock, onClick = onClick)
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
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cancel))
            }
        },
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
    var search by remember { mutableStateOf("") }
    val filteredApps = remember(apps, search) {
        val q = search.trim()
        if (q.isBlank()) apps
        else apps.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.section_apps)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.apps_picker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(ZeyaoIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    shape = PillShape,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    TextButton(onClick = { current = emptySet() }) {
                        Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.apps_clear))
                    }
                }
                LazyColumn(Modifier.height(320.dp)) {
                    items(filteredApps, key = { it.packageName }) { app ->
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
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cancel))
            }
        },
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                browsers.forEach { (pkg, label) ->
                    ChoiceRow(
                        title = label,
                        subtitle = pkg,
                        icon = ZeyaoIcons.Compass,
                        selected = pkg == selected,
                        onClick = { onConfirm(pkg) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun cooldownLabelText(seconds: Int): String = when (seconds) {
    10 -> stringResource(R.string.cd_10s)
    30 -> stringResource(R.string.cd_30s)
    60 -> stringResource(R.string.cd_1m)
    120 -> stringResource(R.string.cd_2m)
    180 -> stringResource(R.string.cd_3m)
    300 -> stringResource(R.string.cd_5m)
    600 -> stringResource(R.string.cd_10m)
    else -> "${seconds}s"
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { seconds ->
                    ChoiceRow(
                        title = cooldownLabelText(seconds),
                        subtitle = if (seconds == 600) stringResource(R.string.cooldown_default) else null,
                        icon = ZeyaoIcons.Clock,
                        selected = seconds == selected,
                        onClick = { onConfirm(seconds) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun formatMinutes(minutes: Int): String {
    val h = (minutes / 60).coerceIn(0, 23)
    val m = (minutes % 60).coerceIn(0, 59)
    return String.format(Locale.US, "%02d:%02d", h, m)
}
