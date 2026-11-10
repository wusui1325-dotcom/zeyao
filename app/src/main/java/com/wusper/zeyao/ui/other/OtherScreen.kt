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
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.ui.components.PrimaryButton
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction

@Composable
fun OtherScreen(
    currentProviderId: String,
    onBack: () -> Unit,
    onReconfigureKey: () -> Unit,
    onTestUrl: (String) -> Unit,
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
            TopAction("返回", onBack)
            Spacer(Modifier.weight(1f))
            Text("其他", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SectionLabel("AI 服務")
            Text(
                "目前使用 ${provider.displayName}。金鑰只保存在這部裝置上，可在此重新設定服務商與 API Key。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("重新設定 API Key", onClick = onReconfigureKey)
            Spacer(Modifier.height(36.dp))
            SectionLabel("測試擇要")
            Text(
                "在尚未開啟攔截時，也可以貼上新聞網址預覽擇要頁。返回與進入會立即出現，摘要採流式輸出。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = testUrl,
                onValueChange = { testUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新聞網址") },
                placeholder = { Text("https://") },
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "開啟擇要頁",
                onClick = { onTestUrl(testUrl.trim()) },
                enabled = testUrl.isNotBlank(),
            )
            Spacer(Modifier.height(36.dp))
            SectionLabel("關於")
            Text(
                "擇要 0.1.0\nWusper 相關應用。本機攔截新聞頁，以你自己的 AI 金鑰產生流式摘要。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
