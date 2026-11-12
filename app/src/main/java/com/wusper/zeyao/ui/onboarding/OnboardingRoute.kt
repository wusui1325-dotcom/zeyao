package com.wusper.zeyao.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.ai.AiClientFactory
import com.wusper.zeyao.ai.AiInitException
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.ai.GenerationParams
import com.wusper.zeyao.data.AppLanguage
import com.wusper.zeyao.ui.components.BrandMark
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.PrimaryButton
import com.wusper.zeyao.ui.components.QuietButton
import kotlinx.coroutines.launch

enum class OnboardingStep { LANGUAGE, PROVIDER, KEY, LOADING }

@Composable
fun OnboardingRoute(
    initialProviderId: String? = null,
    reconfigure: Boolean = false,
    onFinished: () -> Unit,
) {
    val app = ZeyaoApp.instance
    val scope = rememberCoroutineScope()
    var step by remember {
        mutableStateOf(
            if (reconfigure) OnboardingStep.PROVIDER else OnboardingStep.LANGUAGE
        )
    }
    var selected by remember { mutableStateOf<AiProvider?>(null) }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("正在初始化…") }
    var error by remember { mutableStateOf<String?>(null) }
    var initializing by remember { mutableStateOf(false) }

    fun runInit(provider: AiProvider, apiKey: String) {
        initializing = true
        error = null
        loadingMessage = "正在初始化 ${provider.displayName}…"
        step = OnboardingStep.LOADING
        scope.launch {
            try {
                val client = AiClientFactory.create(provider)
                client.initialize(apiKey)
                // Persist key only in encrypted prefs; persist provider + default params via settings.
                app.secrets.setApiKey(provider.id, apiKey)
                app.settings.update {
                    it.copy(
                        onboarded = true,
                        providerId = provider.id,
                        interceptEnabled = true,
                    )
                }
                // Default generation parameters are the app-wide GenerationParams.DEFAULT
                // (thinking off, reasoning high, temperature 1.2, max tokens 1000) and
                // are applied by each vendor client on every request.
                @Suppress("UNUSED_VARIABLE")
                val defaults = GenerationParams.DEFAULT
                onFinished()
            } catch (e: Exception) {
                initializing = false
                error = when (e) {
                    is AiInitException -> e.message ?: "初始化失敗，請檢查金鑰後重試。"
                    else -> "初始化失敗，請檢查網絡與金鑰後重試。"
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboard",
        ) { current ->
            when (current) {
                OnboardingStep.LANGUAGE -> LanguageStep(
                    onSelect = { lang ->
                        scope.launch {
                            app.settings.update { it.copy(language = lang) }
                        }
                        val locale = java.util.Locale.forLanguageTag(lang.tag)
                        java.util.Locale.setDefault(locale)
                        step = OnboardingStep.PROVIDER
                    },
                )
                OnboardingStep.PROVIDER -> ProviderStep(
                    selected = selected,
                    onSelect = { selected = it },
                    reconfigure = reconfigure,
                    onContinue = { selected?.let { step = OnboardingStep.KEY } },
                )
                OnboardingStep.KEY -> KeyStep(
                    provider = selected ?: AiProvider.DEEPSEEK,
                    key = key,
                    showKey = showKey,
                    onKeyChange = { key = it },
                    onToggle = { showKey = !showKey },
                    onBack = { step = OnboardingStep.PROVIDER },
                    onContinue = {
                        val provider = selected ?: return@KeyStep
                        if (key.isBlank()) return@KeyStep
                        runInit(provider, key.trim())
                    },
                )
                OnboardingStep.LOADING -> LoadingStep(
                    message = loadingMessage,
                    error = error,
                    busy = initializing,
                    onBack = {
                        error = null
                        initializing = false
                        step = OnboardingStep.KEY
                    },
                    onRetry = {
                        val provider = selected ?: return@LoadingStep
                        if (key.isBlank()) return@LoadingStep
                        runInit(provider, key.trim())
                    },
                )
            }
        }
    }
}

@Composable
private fun LanguageStep(onSelect: (AppLanguage) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BrandMark()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "選擇語言 / Choose language",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "之後可在「其他」中更改。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppLanguage.entries.forEach { lang ->
                ChoiceRow(
                    title = lang.nativeName,
                    subtitle = null,
                    selected = false,
                    onClick = { onSelect(lang) },
                )
            }
        }
    }
}

@Composable
private fun ProviderStep(
    selected: AiProvider?,
    onSelect: (AiProvider) -> Unit,
    reconfigure: Boolean,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BrandMark()
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (reconfigure) "重新選擇 AI 服務商" else "打開新聞前，先讀重點。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "請選擇摘要使用的 AI 服務商。金鑰只會保存在這部裝置上。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AiProvider.entries.forEach { provider ->
                ChoiceRow(
                    title = provider.displayName,
                    subtitle = if (provider == AiProvider.DEEPSEEK) "建議首選，回應較快" else null,
                    selected = selected == provider,
                    onClick = { onSelect(provider) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "繼續",
            onClick = onContinue,
            enabled = selected != null,
        )
    }
}

@Composable
private fun KeyStep(
    provider: AiProvider,
    key: String,
    showKey: Boolean,
    onKeyChange: (String) -> Unit,
    onToggle: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        BrandMark(small = true)
        Spacer(Modifier.height(28.dp))
        Text("輸入 ${provider.displayName} API Key", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "金鑰以加密方式存在本機，不會寫入原始碼、日誌或雲端。擇要只會用它向 ${provider.displayName} 請求摘要。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API Key") },
            placeholder = { Text(provider.keyHint) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showKey) "隱藏金鑰" else "顯示金鑰",
                    )
                }
            },
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(text = "繼續", onClick = onContinue, enabled = key.isNotBlank())
        Spacer(Modifier.height(8.dp))
        QuietButton(text = "返回", onClick = onBack)
    }
}

@Composable
private fun LoadingStep(
    message: String,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark(small = true)
        Spacer(Modifier.height(36.dp))
        if (error == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "正在寫入預設參數：高強度、關閉深度思考、溫度 1.2、上限 1000 tokens。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("初始化未完成", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(32.dp))
            PrimaryButton(text = "重試", onClick = onRetry)
            Spacer(Modifier.height(8.dp))
            QuietButton(text = "返回修改金鑰", onClick = onBack)
        }
        if (busy) {
            // Keep the page as a full-page loader; buttons hidden while in flight.
        }
    }
}
