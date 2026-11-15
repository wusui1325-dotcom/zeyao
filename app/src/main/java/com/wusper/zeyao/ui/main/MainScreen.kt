package com.wusper.zeyao.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.data.SummaryRecord
import com.wusper.zeyao.ui.components.EmptyHint
import com.wusper.zeyao.ui.components.TopAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private enum class RecordFilter { ALL, PINNED, FAILED }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    var filter by remember { mutableStateOf(RecordFilter.ALL) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val deletedLabel = stringResource(R.string.record_deleted)
    val undoLabel = stringResource(R.string.undo)
    val filtered = remember(records, query, filter) {
        val q = query.trim()
        records.asSequence()
            .filter { record ->
                when (filter) {
                    RecordFilter.ALL -> true
                    RecordFilter.PINNED -> record.pinned
                    RecordFilter.FAILED -> record.status == RecordStatus.FAILED || record.status == RecordStatus.CANCELLED
                }
            }
            .filter { record ->
                if (q.isBlank()) true
                else record.title.contains(q, true) ||
                    record.url.contains(q, true) ||
                    record.host.contains(q, true) ||
                    record.summary.contains(q, true)
            }
            .toList()
    }
    val colors = MaterialTheme.colorScheme

    fun deleteRecord(record: SummaryRecord) {
        scope.launch {
            val removed = ZeyaoApp.instance.records.delete(record.id)
            if (removed == null) return@launch
            val result = snackbar.showSnackbar(
                message = deletedLabel,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                ZeyaoApp.instance.records.upsert(removed)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = filter == RecordFilter.ALL,
                        onClick = { filter = RecordFilter.ALL },
                        label = { Text(stringResource(R.string.filter_all)) },
                    )
                    FilterChip(
                        selected = filter == RecordFilter.PINNED,
                        onClick = { filter = RecordFilter.PINNED },
                        label = { Text(stringResource(R.string.filter_pinned)) },
                    )
                    FilterChip(
                        selected = filter == RecordFilter.FAILED,
                        onClick = { filter = RecordFilter.FAILED },
                        label = { Text(stringResource(R.string.filter_failed)) },
                    )
                }
                Spacer(Modifier.height(4.dp))
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
                EmptyHint(stringResource(if (query.isBlank() && filter == RecordFilter.ALL) R.string.no_records else R.string.no_matching_records))
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { record ->
                        SwipeRecordRow(
                            record = record,
                            onClick = { onOpenRecord(record) },
                            onPin = { pinned ->
                                scope.launch { ZeyaoApp.instance.records.setPinned(record.id, pinned) }
                            },
                            onDelete = { deleteRecord(record) },
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRecordRow(
    record: SummaryRecord,
    onClick: () -> Unit,
    onPin: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val deleteCd = stringResource(R.string.cd_delete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.18f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.semantics { contentDescription = deleteCd },
                )
            }
        },
        content = {
            RecordRow(record, onClick = onClick, onPin = onPin)
        },
    )
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
private fun RecordRow(
    record: SummaryRecord,
    onClick: () -> Unit,
    onPin: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val pinCd = stringResource(if (record.pinned) R.string.cd_unpin else R.string.cd_pin)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.outline.copy(alpha = 0.45f), shape)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    record.title.ifBlank { record.host.ifBlank { record.url } },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = { onPin(!record.pinned) },
                modifier = Modifier.semantics { contentDescription = pinCd },
            ) {
                Icon(
                    imageVector = if (record.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = pinCd,
                    tint = if (record.pinned) colors.primary else colors.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(record.host, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(formatTime(record.createdAt), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(recordStatusLabel(record.status), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            if (record.pinned) {
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.pinned), style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordDetailScreen(
    record: SummaryRecord,
    fontScale: Float,
    handoffPackage: String,
    onBack: () -> Unit,
    onPin: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale,
    )
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
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(record.title.ifBlank { record.host }, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            val statusLabel = recordStatusLabel(record.status)
            val pinnedLabel = stringResource(R.string.pinned)
            Text(
                buildString {
                    append(record.host)
                    append("  ·  ")
                    append(formatTime(record.createdAt))
                    append("  ·  ")
                    append(statusLabel)
                    if (record.pinned) {
                        append("  ·  ")
                        append(pinnedLabel)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(record.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                TextButton(onClick = {
                    shareRecord(context, record)
                }) { Text(stringResource(R.string.share)) }
                TextButton(onClick = {
                    copyRecord(context, record)
                }) { Text(stringResource(R.string.copy)) }
                TextButton(onClick = {
                    if (record.url.isNotBlank()) {
                        BrowserHandoff.open(context, record.url, handoffPackage)
                    }
                }) { Text(stringResource(R.string.open_original)) }
                TextButton(onClick = { onPin(!record.pinned) }) {
                    Text(stringResource(if (record.pinned) R.string.unpin else R.string.pin))
                }
                TextButton(onClick = onRegenerate) { Text(stringResource(R.string.regenerate_summary)) }
                TextButton(onClick = onFontSmaller) { Text(stringResource(R.string.font_smaller)) }
                TextButton(onClick = onFontLarger) { Text(stringResource(R.string.font_larger)) }
                TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.delete)) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                record.summary.ifBlank {
                    stringResource(
                        if (record.status == RecordStatus.GENERATING) R.string.still_generating else R.string.no_summary,
                    )
                },
                style = bodyStyle,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_record)) },
            text = { Text(stringResource(R.string.delete_record_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

internal fun shareRecord(context: android.content.Context, record: SummaryRecord) {
    val text = buildString {
        append(record.title.ifBlank { record.host })
        append('\n')
        append(record.url)
        if (record.summary.isNotBlank()) {
            append("\n\n")
            append(record.summary)
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, record.title.ifBlank { context.getString(R.string.share_summary) })
    }
    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.share_summary))) }
}

internal fun copyRecord(context: android.content.Context, record: SummaryRecord) {
    val text = record.summary.ifBlank { record.url }
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
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
