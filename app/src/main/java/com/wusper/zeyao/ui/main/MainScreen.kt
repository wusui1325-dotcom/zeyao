package com.wusper.zeyao.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.res.stringResource
import com.wusper.zeyao.R
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.data.SummaryRecord
import com.wusper.zeyao.ui.components.EmptyHint
import com.wusper.zeyao.ui.components.TopAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MainScreen(
    settings: AppSettings,
    records: List<SummaryRecord>,
    interceptOn: Boolean,
    vpnReady: Boolean,
    accessibilityEnabled: Boolean,
    onToggleIntercept: (Boolean) -> Unit,
    onToggleAllPages: (Boolean, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOther: () -> Unit,
    onOpenRecord: (SummaryRecord) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val filtered = remember(records, query) {
        if (query.isBlank()) records
        else records.filter {
            it.title.contains(query, ignoreCase = true) ||
            it.url.contains(query, ignoreCase = true) ||
            it.summary.contains(query, ignoreCase = true)
        }
    }
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(stringResource(R.string.other), onOpenOther)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
            )
            Spacer(Modifier.weight(1f))
            TopAction(stringResource(R.string.settings), onOpenSettings)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            InterceptCard(
                interceptOn = interceptOn,
                vpnReady = vpnReady,
                accessibilityEnabled = accessibilityEnabled,
                scheduleEnabled = settings.scheduleEnabled,
                onToggle = onToggleIntercept,
            )
            Spacer(Modifier.height(12.dp))
            AllPagesCard(
                enabled = settings.interceptAllPages,
                askBefore = settings.askBeforeSummary,
                onToggle = onToggleAllPages,
            )
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.recent_records), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_records)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.export_records),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier
                    .clickable {
                        val exportTitle = context.getString(R.string.export_records)
                        val text = filtered.joinToString("\n\n") { r ->
                            "${r.title}\n${r.url}\n${r.summary.take(200)}"
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text.ifBlank { "—" })
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, exportTitle)) }
                    }
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (filtered.isEmpty()) {
            EmptyHint(stringResource(if (query.isBlank()) R.string.no_records else R.string.no_matching_records))
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filtered, key = { it.id }) { record ->
                    RecordRow(record, onClick = { onOpenRecord(record) })
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AllPagesCard(
    enabled: Boolean,
    askBefore: Boolean,
    onToggle: (Boolean, Boolean) -> Unit,
) {
    var showAsk by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.7f), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.intercept_all), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled) {
                        if (askBefore) stringResource(R.string.intercept_all_on_ask)
                        else stringResource(R.string.intercept_all_on_direct)
                    } else {
                        stringResource(R.string.intercept_all_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) showAsk = true else onToggle(false, false)
                },
            )
        }
    }
    if (showAsk) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAsk = false },
            title = { Text(stringResource(R.string.ask_enable_title)) },
            text = { Text(stringResource(R.string.ask_enable_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onToggle(true, true)
                    showAsk = false
                }) { Text(stringResource(R.string.ask_enable_ask)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onToggle(true, false)
                    showAsk = false
                }) { Text(stringResource(R.string.ask_enable_direct)) }
            },
        )
    }
}

@Composable
private fun InterceptCard(
    interceptOn: Boolean,
    vpnReady: Boolean,
    accessibilityEnabled: Boolean,
    scheduleEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.7f), shape)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.intercept_news), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    interceptStatusLine(interceptOn, vpnReady, accessibilityEnabled, scheduleEnabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = interceptOn, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun interceptStatusLine(
    interceptOn: Boolean,
    vpnReady: Boolean,
    accessibilityEnabled: Boolean,
    scheduleEnabled: Boolean,
): String {
    if (!interceptOn) return stringResource(R.string.intercept_off)
    val parts = mutableListOf<String>()
    parts += if (vpnReady) stringResource(R.string.status_vpn_on) else stringResource(R.string.status_vpn_off)
    parts += if (accessibilityEnabled) stringResource(R.string.status_a11y_on) else stringResource(R.string.status_a11y_off)
    if (scheduleEnabled) parts += stringResource(R.string.status_schedule_on)
    return parts.joinToString(" · ")
}

@Composable
private fun RecordRow(record: SummaryRecord, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(
            record.title.ifBlank { record.host.ifBlank { record.url } },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.host, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(formatTime(record.createdAt), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(recordStatusLabel(record.status), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        }
        if (record.summary.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                record.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun RecordDetailScreen(record: SummaryRecord, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(stringResource(R.string.back), onBack)
        }
        Text(record.title.ifBlank { record.host }, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("${record.host}  ·  ${formatTime(record.createdAt)}  ·  ${recordStatusLabel(record.status)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(record.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            record.summary.ifBlank {
                stringResource(
                    if (record.status == RecordStatus.GENERATING) R.string.still_generating else R.string.no_summary,
                )
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun formatTime(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMs))
}

@Composable
internal fun recordStatusLabel(status: RecordStatus): String = when (status) {
    RecordStatus.COMPLETED -> stringResource(R.string.status_completed)
    RecordStatus.GENERATING -> stringResource(R.string.status_generating)
    RecordStatus.FAILED -> stringResource(R.string.status_failed)
    RecordStatus.CANCELLED -> stringResource(R.string.status_cancelled)
}
