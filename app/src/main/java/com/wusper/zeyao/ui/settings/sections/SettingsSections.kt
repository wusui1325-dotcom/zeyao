package com.wusper.zeyao.ui.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.ui.components.FlatCard
import com.wusper.zeyao.ui.components.IconBadge
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.ZeyaoIcons
import com.wusper.zeyao.ui.settings.SettingSwitch

@Composable
fun InterceptSection(
    settings: AppSettings,
    vpnReady: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleIntercept: (Boolean) -> Unit,
    onTogglePersistent: (Boolean) -> Unit,
) {
    SectionLabel(stringResource(R.string.section_intercept), icon = ZeyaoIcons.Shield)
    FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        SettingSwitch(
            title = stringResource(R.string.global_intercept),
            subtitle = if (settings.interceptEnabled) {
                stringResource(R.string.global_intercept_on)
            } else {
                stringResource(R.string.global_intercept_off)
            },
            icon = ZeyaoIcons.ShieldCheck,
            checked = settings.interceptEnabled,
            onChecked = onToggleIntercept,
        )
        if (!vpnReady && settings.interceptEnabled) {
            Row(
                Modifier.padding(start = 48.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(ZeyaoIcons.Alert, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                Text(
                    stringResource(R.string.status_vpn_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        SettingSwitch(
            title = stringResource(R.string.persistent),
            subtitle = stringResource(R.string.persistent_hint),
            icon = ZeyaoIcons.Bolt,
            checked = settings.persistentEnabled,
            onChecked = onTogglePersistent,
        )
        SettingSwitch(
            title = stringResource(R.string.schedule_enabled),
            subtitle = stringResource(R.string.schedule_hint),
            icon = ZeyaoIcons.Clock,
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
            icon = ZeyaoIcons.Globe,
            checked = settings.interceptAllPages,
            onChecked = { enabled -> onUpdate { it.copy(interceptAllPages = enabled) } },
        )
        if (settings.interceptAllPages) {
            SettingSwitch(
                title = stringResource(R.string.ask_before),
                subtitle = stringResource(R.string.ask_before_hint),
                icon = ZeyaoIcons.Info,
                checked = settings.askBeforeSummary,
                onChecked = { enabled -> onUpdate { it.copy(askBeforeSummary = enabled) } },
            )
        }
    }
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
    val colors = MaterialTheme.colorScheme
    SectionLabel(stringResource(R.string.section_https), icon = ZeyaoIcons.Lock)
    FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        SettingSwitch(
            title = stringResource(R.string.https_decrypt),
            subtitle = if (settings.httpsDecryptEnabled && caInstalled) {
                stringResource(R.string.https_decrypt_on)
            } else {
                stringResource(R.string.https_decrypt_off)
            },
            icon = ZeyaoIcons.Lock,
            checked = settings.httpsDecryptEnabled,
            onChecked = { enabled ->
                if (enabled) onRequestEnableHttps() else onToggleHttpsDecrypt(false)
            },
        )
        SettingSwitch(
            title = stringResource(R.string.https_news_only),
            subtitle = stringResource(R.string.https_news_only_hint),
            icon = ZeyaoIcons.Document,
            checked = settings.httpsDecryptNewsOnly,
            onChecked = { enabled -> onUpdate { it.copy(httpsDecryptNewsOnly = enabled) } },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onInstallCa)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = ZeyaoIcons.Award, active = caInstalled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ca_cert), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (caInstalled) stringResource(R.string.ca_ok) else stringResource(R.string.ca_bad),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (caInstalled) colors.onSurfaceVariant else colors.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(ZeyaoIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.pinned_hosts_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
fun StatsSection(settings: AppSettings) {
    val colors = MaterialTheme.colorScheme
    SectionLabel(stringResource(R.string.section_status), icon = ZeyaoIcons.BarChart)
    FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = ZeyaoIcons.BarChart)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.intercept_count, settings.interceptCount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                val days = com.wusper.zeyao.mitm.LocalCa.daysUntilExpiry()
                if (days in 0..30) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.ca_expiry_soon, days),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
            }
        }
    }
}
