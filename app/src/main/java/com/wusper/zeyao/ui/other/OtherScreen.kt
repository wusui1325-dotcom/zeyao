package com.wusper.zeyao.ui.other

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.BuildConfig
import com.wusper.zeyao.R
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.data.AppLanguage
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.FlatCard
import com.wusper.zeyao.ui.components.IconBadge
import com.wusper.zeyao.ui.components.InputShape
import com.wusper.zeyao.ui.components.PrimaryButton
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction
import com.wusper.zeyao.ui.components.ZeyaoIcons

@Composable
fun OtherScreen(
    currentProviderId: String,
    currentLanguage: AppLanguage,
    onBack: () -> Unit,
    onOpenModelParams: () -> Unit,
    onReconfigureKey: () -> Unit,
    onTestUrl: (String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    var testUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val provider = AiProvider.fromId(currentProviderId)
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(stringResource(R.string.back), onBack, icon = ZeyaoIcons.ArrowLeft)
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(ZeyaoIcons.Grid, contentDescription = null, tint = colors.onBackground, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.other), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel(stringResource(R.string.section_ai), icon = ZeyaoIcons.Sparkle)
            FlatCard(padding = PaddingValues(16.dp)) {
                Text(
                    stringResource(R.string.other_ai_hint, provider.displayName),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                FlatCard(
                    padding = PaddingValues(14.dp),
                    onClick = onOpenModelParams,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = ZeyaoIcons.Sliders)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.model_and_params), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.model_and_params_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(ZeyaoIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.reconfigure_key),
                    icon = ZeyaoIcons.Key,
                    onClick = onReconfigureKey,
                )
            }

            SectionLabel(stringResource(R.string.section_test), icon = ZeyaoIcons.Play)
            FlatCard(padding = PaddingValues(16.dp)) {
                Text(
                    stringResource(R.string.other_test_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = testUrl,
                    onValueChange = { testUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = InputShape,
                    leadingIcon = { Icon(ZeyaoIcons.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.news_url)) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clip = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                            if (clip.isNotBlank()) testUrl = clip
                        }) {
                            Icon(ZeyaoIcons.Clipboard, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.paste))
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = stringResource(R.string.open_intercept),
                    icon = ZeyaoIcons.ArrowUpRight,
                    onClick = { onTestUrl(testUrl.trim()) },
                    enabled = testUrl.isNotBlank(),
                )
            }

            SectionLabel(stringResource(R.string.section_language), icon = ZeyaoIcons.Globe)
            Text(
                stringResource(R.string.other_lang_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    ChoiceRow(
                        title = lang.nativeName,
                        subtitle = null,
                        icon = ZeyaoIcons.Globe,
                        selected = lang == currentLanguage,
                        onClick = { onLanguageChange(lang) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.section_about), icon = ZeyaoIcons.Info)
            FlatCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    IconBadge(icon = ZeyaoIcons.Info)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.about_text, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
