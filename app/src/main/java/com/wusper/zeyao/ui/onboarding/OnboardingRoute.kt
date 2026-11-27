package com.wusper.zeyao.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.ai.AiClientFactory
import com.wusper.zeyao.ai.AiInitException
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.ai.GenerationParams
import com.wusper.zeyao.data.AppLanguage
import com.wusper.zeyao.data.AppLocale
import com.wusper.zeyao.ui.components.BrandMark
import com.wusper.zeyao.ui.components.ChoiceRow
import com.wusper.zeyao.ui.components.IconBadge
import com.wusper.zeyao.ui.components.InputShape
import com.wusper.zeyao.ui.components.PrimaryButton
import com.wusper.zeyao.ui.components.QuietButton
import com.wusper.zeyao.ui.components.ZeyaoIcons
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
            if (reconfigure) OnboardingStep.PROVIDER else OnboardingStep.LANGUAGE,
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
                app.secrets.setApiKey(provider.id, apiKey)
                app.settings.update {
                    it.copy(
                        onboarded = true,
                        providerId = provider.id,
                        interceptEnabled = true,
                    )
                }
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 18.dp),
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
                        AppLocale.persist(app, lang)
                        java.util.Locale.setDefault(AppLocale.localeOf(lang))
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
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.lang_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lang_hint),
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
                    icon = ZeyaoIcons.Globe,
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
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (reconfigure) stringResource(R.string.onboard_reconfigure_title) else stringResource(R.string.onboard_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.onboard_provider_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
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
                    icon = ZeyaoIcons.Sparkle,
                    selected = selected == provider,
                    onClick = { onSelect(provider) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = stringResource(R.string.continue_action),
            icon = ZeyaoIcons.ArrowRight,
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
        Spacer(Modifier.height(26.dp))
        Text(stringResource(R.string.onboard_key_title, provider.displayName), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.onboard_key_hint, provider.displayName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = key,
            onValueChange = onKeyChange,
            modifier = Modifier.fillMaxWidth(),
            shape = InputShape,
            leadingIcon = { Icon(ZeyaoIcons.Key, contentDescription = null, modifier = Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.api_key)) },
            placeholder = { Text(provider.keyHint) },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (showKey) ZeyaoIcons.EyeOff else ZeyaoIcons.Eye,
                        contentDescription = stringResource(if (showKey) R.string.hide_key else R.string.show_key),
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton(
            text = stringResource(R.string.continue_action),
            icon = ZeyaoIcons.ArrowRight,
            onClick = onContinue,
            enabled = key.isNotBlank(),
        )
        Spacer(Modifier.height(10.dp))
        QuietButton(
            text = stringResource(R.string.back),
            icon = ZeyaoIcons.ArrowLeft,
            onClick = onBack,
        )
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
        Spacer(Modifier.height(32.dp))
        if (error == null) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text(message, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "正在寫入預設參數：高強度、關閉深度思考、溫度 1.2、上限 1000 tokens。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            IconBadge(icon = ZeyaoIcons.Alert, size = 48.dp, iconSize = 22.dp)
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.init_incomplete), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(28.dp))
            PrimaryButton(text = stringResource(R.string.retry), icon = ZeyaoIcons.Refresh, onClick = onRetry)
            Spacer(Modifier.height(10.dp))
            QuietButton(text = stringResource(R.string.back), icon = ZeyaoIcons.ArrowLeft, onClick = onBack)
        }
        if (busy) {
            // Keep the page as a full-page loader; buttons hidden while in flight.
        }
    }
}
