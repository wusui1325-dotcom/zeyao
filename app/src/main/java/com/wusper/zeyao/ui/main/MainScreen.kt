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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.BrowserHandoff
import com.wusper.zeyao.data.RecordStatus
import com.wusper.zeyao.data.SummaryRecord
import com.wusper.zeyao.ui.components.EmptyHint
import com.wusper.zeyao.ui.components.FlatCard
import com.wusper.zeyao.ui.components.IconBadge
import com.wusper.zeyao.ui.components.PillAction
import com.wusper.zeyao.ui.components.PillShape
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction
import com.wusper.zeyao.ui.components.ZeyaoIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

private enum class RecordFilter { ALL, PINNED, FAILED }

@OptIn(ExperimentalLayoutApi::class)
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
    val pinnedCount = remember(records) { records.count { it.pinned } }
    val failedCount = remember(records) {
        records.count { it.status == RecordStatus.FAILED || it.status == RecordStatus.CANCELLED }
    }
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

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Streamlined Top Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopAction(
                    text = stringResource(R.string.other),
                    icon = ZeyaoIcons.Grid,
                    onClick = onOpenOther,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = ZeyaoIcons.Sparkle,
                        contentDescription = null,
                        tint = colors.onBackground,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.weight(1f))
                TopAction(
                    text = stringResource(R.string.settings),
                    icon = ZeyaoIcons.Settings,
                    onClick = onOpenSettings,
                )
            }

            Column(Modifier.padding(horizontal = 18.dp)) {
                Spacer(Modifier.height(4.dp))
                InterceptCard(
                    interceptOn = interceptOn,
                    vpnReady = vpnReady,
                    accessibilityEnabled = accessibilityEnabled,
                    scheduleEnabled = settings.scheduleEnabled,
                    onToggle = onToggleIntercept,
                )
                Spacer(Modifier.height(10.dp))
                AllPagesCard(
                    enabled = settings.interceptAllPages,
                    askBefore = settings.askBeforeSummary,
                    onToggle = onToggleAllPages,
                )
                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionLabel(
                        text = stringResource(R.string.recent_records),
                        icon = ZeyaoIcons.Clock,
                        modifier = Modifier.weight(1f),
                    )
                    PillAction(
                        text = stringResource(R.string.export_records),
                        icon = ZeyaoIcons.Share,
                        onClick = {
                            val exportTitle = context.getString(R.string.export_records)
                            val text = filtered.joinToString("\n\n") { r ->
                                "${r.title}\n${r.url}\n${r.summary.take(200)}"
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text.ifBlank { "—" })
                            }
                            runCatching { context.startActivity(Intent.createChooser(send, exportTitle)) }
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))
                val clearCd = stringResource(R.string.clear_search)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.search_records), style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    shape = PillShape,
                    leadingIcon = {
                        Icon(
                            imageVector = ZeyaoIcons.Search,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            Box(
                                Modifier
                                    .semantics { contentDescription = clearCd }
                                    .clip(PillShape)
                                    .clickable { query = "" }
                                    .padding(8.dp),
                            ) {
                                Icon(
                                    imageVector = ZeyaoIcons.Close,
                                    contentDescription = null,
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.65f),
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val allLabel = stringResource(R.string.filter_all)
                    val pinnedPillLabel = stringResource(R.string.filter_pinned)
                    val failedPillLabel = stringResource(R.string.filter_failed)
                    PillAction(
                        text = if (records.isNotEmpty()) "$allLabel ${records.size}" else allLabel,
                        icon = ZeyaoIcons.ListIcon,
                        selected = filter == RecordFilter.ALL,
                        onClick = { filter = RecordFilter.ALL },
                    )
                    PillAction(
                        text = if (pinnedCount > 0) "$pinnedPillLabel $pinnedCount" else pinnedPillLabel,
                        icon = ZeyaoIcons.Pin,
                        selected = filter == RecordFilter.PINNED,
                        onClick = { filter = RecordFilter.PINNED },
                    )
                    PillAction(
                        text = if (failedCount > 0) "$failedPillLabel $failedCount" else failedPillLabel,
                        icon = ZeyaoIcons.Alert,
                        selected = filter == RecordFilter.FAILED,
                        onClick = { filter = RecordFilter.FAILED },
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            if (filtered.isEmpty()) {
                EmptyHint(
                    text = stringResource(if (query.isBlank() && filter == RecordFilter.ALL) R.string.no_records else R.string.no_matching_records),
                    icon = if (query.isBlank() && filter == RecordFilter.ALL) ZeyaoIcons.Document else ZeyaoIcons.Search,
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filtered, key = { it.id }) { record ->
                        RecordRow(
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
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
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
    FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = ZeyaoIcons.Globe, active = enabled)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.intercept_all), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
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
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) showAsk = true else onToggle(false, false)
                },
            )
        }
    }
    if (showAsk) {
        AlertDialog(
            onDismissRequest = { showAsk = false },
            title = { Text(stringResource(R.string.ask_enable_title)) },
            text = { Text(stringResource(R.string.ask_enable_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onToggle(true, true)
                    showAsk = false
                }) {
                    Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ask_enable_ask))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    onToggle(true, false)
                    showAsk = false
                }) {
                    Icon(ZeyaoIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ask_enable_direct))
                }
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
    FlatCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (interceptOn) ZeyaoIcons.ShieldCheck else ZeyaoIcons.Shield,
                active = interceptOn,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.intercept_news), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    interceptStatusLine(interceptOn, vpnReady, accessibilityEnabled, scheduleEnabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
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
    onDelete: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val pinCd = stringResource(if (record.pinned) R.string.cd_unpin else R.string.cd_pin)
    val deleteCd = stringResource(R.string.cd_delete)
    FlatCard(
        padding = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.title.ifBlank { record.host.ifBlank { record.url } },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = ZeyaoIcons.Globe,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        record.host.ifBlank { "—" },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(
                        formatTime(record.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                    Text("·", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(
                        recordStatusLabel(record.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.status == RecordStatus.FAILED) colors.error else colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = ZeyaoIcons.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        if (record.summary.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                record.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillAction(
                text = stringResource(if (record.pinned) R.string.unpin else R.string.pin),
                icon = ZeyaoIcons.Pin,
                selected = record.pinned,
                contentDesc = pinCd,
                onClick = { onPin(!record.pinned) },
            )
            if (onDelete != null) {
                PillAction(
                    text = stringResource(R.string.delete),
                    icon = ZeyaoIcons.Trash,
                    destructive = true,
                    contentDesc = deleteCd,
                    onClick = onDelete,
                )
            }
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
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale,
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(
                text = stringResource(R.string.back),
                icon = ZeyaoIcons.ArrowLeft,
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(
                record.title.ifBlank { record.host },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            val statusLabel = recordStatusLabel(record.status)
            val pinnedLabel = stringResource(R.string.pinned)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(ZeyaoIcons.Globe, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(
                    buildString {
                        append(record.host)
                        append(" · ")
                        append(formatTime(record.createdAt))
                        append(" · ")
                        append(statusLabel)
                        if (record.pinned) {
                            append(" · ")
                            append(pinnedLabel)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(record.url, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillAction(stringResource(R.string.share), ZeyaoIcons.Share, { shareRecord(context, record) })
                PillAction(stringResource(R.string.copy), ZeyaoIcons.Copy, { copyRecord(context, record) })
                PillAction(stringResource(R.string.open_original), ZeyaoIcons.ArrowUpRight, {
                    if (record.url.isNotBlank()) {
                        BrowserHandoff.open(context, record.url, handoffPackage)
                    }
                })
                PillAction(
                    text = stringResource(if (record.pinned) R.string.unpin else R.string.pin),
                    icon = ZeyaoIcons.Pin,
                    selected = record.pinned,
                    onClick = { onPin(!record.pinned) },
                )
                PillAction(stringResource(R.string.regenerate_summary), ZeyaoIcons.Refresh, onRegenerate)
                PillAction(stringResource(R.string.font_smaller), ZeyaoIcons.TextSmaller, onFontSmaller)
                PillAction(stringResource(R.string.font_larger), ZeyaoIcons.TextLarger, onFontLarger)
                PillAction(stringResource(R.string.delete), ZeyaoIcons.Trash, { confirmDelete = true }, destructive = true)
            }
            Spacer(Modifier.height(16.dp))
            FlatCard(padding = PaddingValues(18.dp)) {
                SelectionContainer {
                    Text(
                        record.summary.ifBlank {
                            stringResource(
                                if (record.status == RecordStatus.GENERATING) R.string.still_generating else R.string.no_summary,
                            )
                        },
                        style = bodyStyle,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_record)) },
            text = { Text(stringResource(R.string.delete_record_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Icon(ZeyaoIcons.Trash, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
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

private val recordTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

internal fun formatTime(epochMs: Long): String {
    return recordTimeFormatter
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
}

@Composable
internal fun recordStatusLabel(status: RecordStatus): String = when (status) {
    RecordStatus.COMPLETED -> stringResource(R.string.status_completed)
    RecordStatus.GENERATING -> stringResource(R.string.status_generating)
    RecordStatus.FAILED -> stringResource(R.string.status_failed)
    RecordStatus.CANCELLED -> stringResource(R.string.status_cancelled)
}
