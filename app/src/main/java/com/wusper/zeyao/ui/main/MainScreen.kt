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
            TopAction("其他", onOpenOther)
            Spacer(Modifier.weight(1f))
            Text(
                "擇要",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Serif,
            )
            Spacer(Modifier.weight(1f))
            TopAction("設置", onOpenSettings)
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
                placeholder = { Text("搜尋紀錄") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.3f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "匯出紀錄",
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
                modifier = Modifier
                    .clickable {
                        val text = filtered.joinToString("\n\n") { r ->
                            "${r.title}\n${r.url}\n${r.summary.take(200)}"
                        }
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text.ifBlank { "（無紀錄）" })
                        }
                        runCatching { context.startActivity(Intent.createChooser(send, "匯出紀錄")) }
                    }
                    .padding(vertical = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (filtered.isEmpty()) {
            EmptyHint(if (query.isBlank()) "還沒有擇要紀錄。開啟攔截後，打開新聞頁就會出現在這裡。" else "沒有符合的紀錄")
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
                Text("所有網頁攔截", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (enabled) {
                        if (askBefore) "攔截後會詢問是否擇要" else "攔截後一律直接擇要"
                    } else {
                        "開啟後攔截所有網頁，不做新聞驗證，直接擇要"
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
            title = { Text("攔截後一律詢問？") },
            text = { Text("開啟：攔截到後彈窗問是否擇要。\n關閉：一律直接進入擇要。") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onToggle(true, true)
                    showAsk = false
                }) { Text("開啟詢問") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onToggle(true, false)
                    showAsk = false
                }) { Text("直接擇要") }
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
                    statusLine(interceptOn, vpnReady, accessibilityEnabled, scheduleEnabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = interceptOn, onCheckedChange = onToggle)
        }
    }
}

private fun statusLine(
    interceptOn: Boolean,
    vpnReady: Boolean,
    accessibilityEnabled: Boolean,
    scheduleEnabled: Boolean,
): String {
    if (!interceptOn) return "攔截已關閉"
    val parts = mutableListOf<String>()
    parts += if (vpnReady) "本機 VPN 已開" else "尚未授權本機 VPN"
    parts += if (accessibilityEnabled) "無障礙已開" else "尚未開啟無障礙（無法取得完整網址）"
    if (scheduleEnabled) parts += "已啟用特定時段"
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
            Text(statusLabel(record.status), style = MaterialTheme.typography.labelMedium, color = colors.primary)
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
            TopAction("返回", onBack)
        }
        Text(record.title.ifBlank { record.host }, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("${record.host}  ·  ${formatTime(record.createdAt)}  ·  ${statusLabel(record.status)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(record.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            record.summary.ifBlank {
                if (record.status == RecordStatus.GENERATING) "仍在生成中…" else "沒有摘要內容。"
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

internal fun formatTime(epochMs: Long): String {
    val zone = ZoneId.systemDefault()
    return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMs))
}

internal fun statusLabel(status: RecordStatus): String = when (status) {
    RecordStatus.COMPLETED -> "完成"
    RecordStatus.GENERATING -> "生成中"
    RecordStatus.FAILED -> "失敗"
    RecordStatus.CANCELLED -> "已中止"
}
