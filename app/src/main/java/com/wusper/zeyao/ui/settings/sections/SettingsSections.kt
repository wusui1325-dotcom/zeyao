package com.wusper.zeyao.ui.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.settings.SettingSwitch
import java.util.Locale

@Composable
fun InterceptSection(
    settings: AppSettings,
    vpnReady: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleIntercept: (Boolean) -> Unit,
    onTogglePersistent: (Boolean) -> Unit,
) {
    SectionLabel(stringResource(R.string.section_intercept))
    SettingSwitch(
        title = stringResource(R.string.global_intercept),
        subtitle = if (settings.interceptEnabled) {
            stringResource(R.string.global_intercept_on)
        } else {
            stringResource(R.string.global_intercept_off)
        },
        checked = settings.interceptEnabled,
        onChecked = onToggleIntercept,
    )
    if (!vpnReady && settings.interceptEnabled) {
        Text(
            stringResource(R.string.status_vpn_off),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    SettingSwitch(
        title = stringResource(R.string.persistent),
        subtitle = stringResource(R.string.persistent_hint),
        checked = settings.persistentEnabled,
        onChecked = onTogglePersistent,
    )
    SettingSwitch(
        title = stringResource(R.string.schedule_enabled),
        subtitle = stringResource(R.string.schedule_hint),
        checked = settings.scheduleEnabled,
        onChecked = { enabled -> onUpdate { it.copy(scheduleEnabled = enabled) } },
    )
    SettingSwitch(
        title = stringResource(R.string.intercept_all),
        subtitle = if (settings.interceptAllPages) {
            if (settings.askBeforeSummary) {
                stringResource(R.string.intercept_all_on_ask)
            } else {
                stringResource(R.string.intercept_all_on_direct)
            }
        } else {
            stringResource(R.string.intercept_all_off)
        },
        checked = settings.interceptAllPages,
        onChecked = { enabled -> onUpdate { it.copy(interceptAllPages = enabled) } },
    )
    if (settings.interceptAllPages) {
        SettingSwitch(
            title = stringResource(R.string.ask_before),
            subtitle = stringResource(R.string.ask_before_hint),
            checked = settings.askBeforeSummary,
            onChecked = { enabled -> onUpdate { it.copy(askBeforeSummary = enabled) } },
        )
    }
}

@Composable
fun GenerationParamsSection(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    SectionLabel(stringResource(R.string.section_generation_params))
    var temperature by remember(settings.temperature) { mutableFloatStateOf(settings.temperature.toFloat()) }
    Text(
        stringResource(R.string.temperature_label, String.format(Locale.US, "%.1f", temperature)),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = temperature,
        onValueChange = { temperature = it },
        onValueChangeFinished = { onUpdate { it.copy(temperature = temperature.toDouble()) } },
        valueRange = 0f..2f,
        steps = 19,
    )
    var maxTokens by remember(settings.maxTokens) { mutableFloatStateOf(settings.maxTokens.toFloat()) }
    Text(
        stringResource(R.string.max_tokens_label, maxTokens.toInt()),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = maxTokens,
        onValueChange = { maxTokens = it },
        onValueChangeFinished = { onUpdate { it.copy(maxTokens = maxTokens.toInt()) } },
        valueRange = 200f..2000f,
        steps = 18,
    )
    var fallback by remember(settings.fallbackModels) { mutableStateOf(settings.fallbackModels) }
    OutlinedTextField(
        value = fallback,
        onValueChange = { fallback = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.fallback_models_label)) },
        placeholder = { Text(stringResource(R.string.fallback_models_builtin)) },
        supportingText = { Text(stringResource(R.string.fallback_models_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = { onUpdate { it.copy(fallbackModels = fallback.trim()) } },
        ),
    )
}

@Composable
fun HttpsSection(
    settings: AppSettings,
    caInstalled: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleHttpsDecrypt: (Boolean) -> Unit,
    onRequestEnableHttps: () -> Unit,
    onInstallCa: () -> Unit,
) {
    SectionLabel(stringResource(R.string.section_https))
    SettingSwitch(
        title = stringResource(R.string.https_decrypt),
        subtitle = if (settings.httpsDecryptEnabled && caInstalled) {
            stringResource(R.string.https_decrypt_on)
        } else {
            stringResource(R.string.https_decrypt_off)
        },
        checked = settings.httpsDecryptEnabled,
        onChecked = { enabled ->
            if (enabled) onRequestEnableHttps() else onToggleHttpsDecrypt(false)
        },
    )
    SettingSwitch(
        title = stringResource(R.string.https_news_only),
        subtitle = stringResource(R.string.https_news_only_hint),
        checked = settings.httpsDecryptNewsOnly,
        onChecked = { enabled -> onUpdate { it.copy(httpsDecryptNewsOnly = enabled) } },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onInstallCa)
            .padding(vertical = 8.dp),
    ) {
        Text(stringResource(R.string.ca_cert), style = MaterialTheme.typography.titleMedium)
        Text(
            if (caInstalled) stringResource(R.string.ca_ok) else stringResource(R.string.ca_bad),
            style = MaterialTheme.typography.bodySmall,
            color = if (caInstalled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
    }
    Text(
        stringResource(R.string.pinned_hosts_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun StatsSection(settings: AppSettings) {
    SectionLabel(stringResource(R.string.section_status))
    Text(
        stringResource(R.string.intercept_count, settings.interceptCount),
        style = MaterialTheme.typography.bodyMedium,
    )
    val days = com.wusper.zeyao.mitm.LocalCa.daysUntilExpiry()
    if (days in 0..30) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.ca_expiry_soon, days),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
