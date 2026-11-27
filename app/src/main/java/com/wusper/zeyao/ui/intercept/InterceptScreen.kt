package com.wusper.zeyao.ui.intercept

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.data.EnterButtonPosition
import com.wusper.zeyao.intercept.InterceptSession
import com.wusper.zeyao.ui.components.FlatCard
import com.wusper.zeyao.ui.components.PillAction
import com.wusper.zeyao.ui.components.PillShape
import com.wusper.zeyao.ui.components.ZeyaoIcons

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterceptScreen(
    session: InterceptSession,
    buttonPosition: EnterButtonPosition,
    searchEnabled: Boolean,
    fontScale: Float,
    onBack: () -> Unit,
    onEnter: () -> Unit,
    onShorter: () -> Unit,
    onLonger: () -> Unit,
    onRetry: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenSourceUrl: (String) -> Unit,
    onSkipSite: () -> Unit,
    onFontSmaller: () -> Unit,
    onFontLarger: () -> Unit,
) {
    var showSources by remember { mutableStateOf(false) }
    var confirmSkip by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val host = session.host.ifBlank {
        runCatching { Uri.parse(session.url).host.orEmpty() }.getOrDefault("")
    }
    val title = session.title.ifBlank { host.ifBlank { session.url } }
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
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // Minimalist Brand Pill Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .clip(PillShape)
                    .background(colors.primary)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = ZeyaoIcons.Sparkle,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    stringResource(R.string.brand_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (session.fromCache && session.summary.isNotBlank()) {
                Row(
                    Modifier
                        .clip(PillShape)
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = ZeyaoIcons.Bolt,
                        contentDescription = null,
                        tint = colors.onSurface,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(R.string.from_cache),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurface,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = ZeyaoIcons.Globe,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = host.ifBlank { session.url },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (session.streaming) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(PillShape),
                color = colors.primary,
                trackColor = colors.surfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Spacer(Modifier.height(6.dp))
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val scroll = rememberScrollState()
            FlatCard(
                modifier = Modifier.fillMaxSize(),
                padding = PaddingValues(18.dp),
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                    val body = when {
                        session.error != null && session.summary.isBlank() -> session.error
                        session.summary.isBlank() && session.streaming -> stringResource(R.string.extracting_wait)
                        else -> session.summary
                    }
                    SelectionContainer {
                        Text(
                            text = body,
                            style = bodyStyle,
                        )
                    }
                    if (session.error != null && session.summary.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = session.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.error,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PillAction(stringResource(R.string.shorter), ZeyaoIcons.Compress, onShorter)
            PillAction(stringResource(R.string.longer), ZeyaoIcons.Expand, onLonger)
            PillAction(stringResource(R.string.retry), ZeyaoIcons.Refresh, onRetry)
            if (session.summary.isNotBlank()) {
                PillAction(stringResource(R.string.copy), ZeyaoIcons.Copy, onCopy)
                PillAction(stringResource(R.string.share), ZeyaoIcons.Share, onShare)
            }
            PillAction(stringResource(R.string.font_smaller), ZeyaoIcons.TextSmaller, onFontSmaller)
            PillAction(stringResource(R.string.font_larger), ZeyaoIcons.TextLarger, onFontLarger)
            if (session.sources.isNotEmpty()) {
                PillAction(stringResource(R.string.sources_count, session.sources.size), ZeyaoIcons.Link, { showSources = true })
            }
            PillAction(stringResource(R.string.skip_this_site), ZeyaoIcons.Block, { confirmSkip = true })
            Row(
                Modifier
                    .clip(PillShape)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.outline.copy(alpha = 0.7f), PillShape)
                    .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = ZeyaoIcons.Search,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    stringResource(R.string.search_news),
                    style = MaterialTheme.typography.labelMedium,
                )
                Switch(
                    checked = searchEnabled,
                    onCheckedChange = onToggleSearch,
                )
            }
        }
        ButtonBar(position = buttonPosition, onBack = onBack, onEnter = onEnter)
        Spacer(Modifier.height(4.dp))
    }

    if (showSources) {
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text(stringResource(R.string.sources_count, session.sources.size)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    session.sources.forEachIndexed { i, s ->
                        Text("${i + 1}. [${s.query}] ${s.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (s.url.isNotBlank()) {
                            Row(
                                Modifier
                                    .clickable { onOpenSourceUrl(s.url) }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(ZeyaoIcons.ArrowUpRight, contentDescription = null, tint = colors.primary, modifier = Modifier.size(13.dp))
                                Text(
                                    s.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary,
                                    textDecoration = TextDecoration.Underline,
                                )
                            }
                        }
                        if (s.snippet.isNotBlank()) {
                            Text(s.snippet.take(180), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSources = false }) {
                    Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    if (confirmSkip) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            title = { Text(stringResource(R.string.skip_this_site)) },
            text = { Text(stringResource(R.string.skip_this_site_hint)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSkip = false
                    onSkipSite()
                }) {
                    Icon(ZeyaoIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) {
                    Icon(ZeyaoIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.cancel))
                }
            },
        )
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
        Spacer(Modifier.width(10.dp))
        BackButton(onBack, modifier)
    } else {
        BackButton(onBack, modifier)
        Spacer(Modifier.width(10.dp))
        EnterButton(onEnter, modifier)
    }
}

@Composable
private fun EnterButton(onClick: () -> Unit, modifier: Modifier) {
    val enterCd = stringResource(R.string.cd_enter_article)
    Button(
        onClick = onClick,
        shape = PillShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = enterCd },
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(ZeyaoIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.enter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BackButton(onClick: () -> Unit, modifier: Modifier) {
    val backCd = stringResource(R.string.cd_back_page)
    OutlinedButton(
        onClick = onClick,
        shape = PillShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
        modifier = modifier
            .height(52.dp)
            .semantics { contentDescription = backCd },
    ) {
        Icon(ZeyaoIcons.ArrowLeft, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.back), style = MaterialTheme.typography.titleMedium)
    }
}
