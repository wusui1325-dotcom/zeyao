package com.wusper.zeyao.ui.other

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wusper.zeyao.R
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.data.AppLanguage
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.PrimaryButton
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction

@Composable
fun OtherScreen(
    currentProviderId: String,
    currentLanguage: AppLanguage,
    onBack: () -> Unit,
    onReconfigureKey: () -> Unit,
    onTestUrl: (String) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
) {
    var testUrl by remember { mutableStateOf("") }
    val provider = AiProvider.fromId(currentProviderId)
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
            TopAction(stringResource(R.string.back), onBack)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.other), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionLabel(stringResource(R.string.section_ai))
            Text(
                stringResource(R.string.other_ai_hint, provider.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton(stringResource(R.string.reconfigure_key), onClick = onReconfigureKey)
            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.section_test))
            Text(
                stringResource(R.string.other_test_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.news_url)) },
                placeholder = { Text("https://") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.open_intercept),
                onClick = { onTestUrl(testUrl.trim()) },
                enabled = testUrl.isNotBlank(),
            )
            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.section_language))
            Text(
                stringResource(R.string.other_lang_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                AppLanguage.entries.forEach { lang ->
                    ChoiceRow(
                        title = lang.nativeName,
                        subtitle = null,
                        selected = lang == currentLanguage,
                        onClick = { onLanguageChange(lang) },
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            SectionLabel(stringResource(R.string.section_about))
            Text(
                stringResource(R.string.about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
