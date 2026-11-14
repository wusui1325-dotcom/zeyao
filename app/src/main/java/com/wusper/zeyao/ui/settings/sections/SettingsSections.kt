package com.wusper.zeyao.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.settings.SettingSwitch

/**
 * Extracted settings sections to reduce SettingsScreen size (item 19).
 */
@Composable
fun InterceptSection(
    settings: AppSettings,
    vpnReady: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleIntercept: (Boolean) -> Unit,
    onTogglePersistent: (Boolean) -> Unit,
) {
    SectionLabel("攔截")
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
        title = "全域攔截",
        subtitle = if (vpnReady) "已開啟" else "已關閉",
        checked = settings.interceptEnabled,
        onChecked = onToggleIntercept,
    )
    SettingSwitch(
        title = "常駐",
        subtitle = "保持前景服務",
        checked = settings.persistentEnabled,
        onChecked = onTogglePersistent,
    )
    SettingSwitch(
        title = "特定時段",
        subtitle = "以裝置時區計算",
        checked = settings.scheduleEnabled,
        onChecked = { enabled -> onUpdate { it.copy(scheduleEnabled = enabled) } },
    )
}

@Composable
fun GenerationParamsSection(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionLabel("生成參數")
    Text("溫度：${String.format("%.1f", settings.temperature)}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = settings.temperature.toFloat(),
        onValueChange = { v -> onUpdate { it.copy(temperature = v.toDouble()) } },
        valueRange = 0f..2f,
        steps = 19,
    )
    Text("最大字數：${settings.maxTokens}", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = settings.maxTokens.toFloat(),
        onValueChange = { v -> onUpdate { it.copy(maxTokens = v.toInt()) } },
        valueRange = 200f..2000f,
        steps = 18,
    )
    Text(
        "備用模型：${if (settings.fallbackModels.isBlank()) "內建" else settings.fallbackModels}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun HttpsSection(
    settings: AppSettings,
    caInstalled: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleHttpsDecrypt: (Boolean) -> Unit,
    onInstallCa: () -> Unit,
) {
    SectionLabel("HTTPS 解密")
    SettingSwitch(
        title = "解密 HTTPS",
        subtitle = if (caInstalled) "已安裝憑證" else "需先安裝憑證",
        checked = settings.httpsDecryptEnabled,
        onChecked = { enabled ->
            if (enabled) {
                // Confirmation handled in caller
                onToggleHttpsDecrypt(true)
            } else onToggleHttpsDecrypt(false)
        },
    )
    SettingSwitch(
        title = "僅解密已知新聞域名",
        subtitle = "開啟時只對新聞網站生效，降低風險。",
        checked = settings.httpsDecryptNewsOnly,
        onChecked = { enabled -> onUpdate { it.copy(httpsDecryptNewsOnly = enabled) } },
    )
    Text(
        "已釘扎：accounts.google.com、play.google.com、login.microsoftonline.com 等系統登入不會解密。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun StatsSection(settings: AppSettings) {
    SectionLabel("統計")
    Text(
        "已攔截次數：${settings.interceptCount}",
        style = MaterialTheme.typography.bodyMedium,
    )
    val days = com.wusper.zeyao.mitm.LocalCa.daysUntilExpiry()
    if (days in 0..30) {
        Text(
            "擇要 CA 將在 $days 天後過期，建議重新安裝。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

