package com.wusper.zeyao.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.ui.components.SectionLabel

/**
 * Extracted settings sections to reduce SettingsScreen size (item 19).
 */
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
fun SecuritySection(
    settings: AppSettings,
    caInstalled: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleHttpsDecrypt: (Boolean) -> Unit,
    onInstallCa: () -> Unit,
) {
    SectionLabel("安全與 HTTPS")
    Text(
        if (caInstalled) "擇要 CA 已安裝" else "擇要 CA 未安裝",
        style = MaterialTheme.typography.bodyMedium,
        color = if (caInstalled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    // Additional switches would be called from main screen or expanded here
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
