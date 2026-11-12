package com.wusper.zeyao.ui.intercept

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wusper.zeyao.R
import com.wusper.zeyao.data.EnterButtonPosition
import com.wusper.zeyao.intercept.InterceptSession

@Composable
fun InterceptScreen(
    session: InterceptSession,
    buttonPosition: EnterButtonPosition,
    searchEnabled: Boolean,
    onBack: () -> Unit,
    onEnter: () -> Unit,
    onShorter: () -> Unit,
    onLonger: () -> Unit,
    onRetry: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
) {
    var showSources by remember { mutableStateOf(false) }
    val host = session.host.ifBlank {
        runCatching { Uri.parse(session.url).host.orEmpty() }.getOrDefault("")
    }
    val title = session.title.ifBlank { host.ifBlank { session.url } }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = host.ifBlank { session.url },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                val body = when {
                    session.error != null && session.summary.isBlank() -> session.error
                    session.summary.isBlank() && session.streaming -> stringResource(R.string.extracting)
                    else -> session.summary
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (session.error != null && session.summary.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = session.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        // Action row: shorter / longer / retry / search
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallAction("→更短←", onShorter)
            SmallAction("更←→長", onLonger)
            SmallAction(stringResource(R.string.retry), onRetry)
            if (session.sources.isNotEmpty()) {
                SmallAction("來源 ${session.sources.size}", { showSources = true })
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.search_news),
                    style = MaterialTheme.typography.labelMedium,
                )
                androidx.compose.material3.Switch(
                    checked = searchEnabled,
                    onCheckedChange = onToggleSearch,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        ButtonBar(position = buttonPosition, onBack = onBack, onEnter = onEnter)
        Spacer(Modifier.height(8.dp))
    }
    if (showSources) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text("來源 ${session.sources.size}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    session.sources.forEachIndexed { i, s ->
                        Text("${i + 1}. [${s.query}] ${s.title}", style = MaterialTheme.typography.bodyMedium)
                        if (s.url.isNotBlank()) {
                            Text(s.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (s.snippet.isNotBlank()) {
                            Text(s.snippet.take(180), style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showSources = false }) { Text("關閉") }
            },
        )
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ButtonBar(
    position: EnterButtonPosition,
    onBack: () -> Unit,
    onEnter: () -> Unit,
) {
    when (position) {
        EnterButtonPosition.BOTTOM_CENTER -> {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EnterButton(onEnter, Modifier.fillMaxWidth())
                BackButton(onBack, Modifier.fillMaxWidth())
            }
        }
        EnterButtonPosition.LEFT -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                PairButtons(position, onBack, onEnter, compact = true)
            }
        }
        EnterButtonPosition.RIGHT -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PairButtons(position, onBack, onEnter, compact = true)
            }
        }
        EnterButtonPosition.ENTER_LEFT, EnterButtonPosition.ENTER_RIGHT -> {
            Row(Modifier.fillMaxWidth()) {
                PairButtons(position, onBack, onEnter, compact = false)
            }
        }
    }
}

@Composable
private fun RowScope.PairButtons(
    position: EnterButtonPosition,
    onBack: () -> Unit,
    onEnter: () -> Unit,
    compact: Boolean,
) {
    val enterFirst = position == EnterButtonPosition.ENTER_LEFT
    val modifier = if (compact) Modifier.width(148.dp) else Modifier.weight(1f)
    if (enterFirst) {
        EnterButton(onEnter, modifier)
        Spacer(Modifier.width(12.dp))
        BackButton(onBack, modifier)
    } else {
        BackButton(onBack, modifier)
        Spacer(Modifier.width(12.dp))
        EnterButton(onEnter, modifier)
    }
}

@Composable
private fun EnterButton(onClick: () -> Unit, modifier: Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(stringResource(R.string.enter), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(52.dp)) {
        Text(stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
    }
}
