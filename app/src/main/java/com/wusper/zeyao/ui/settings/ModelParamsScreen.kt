package com.wusper.zeyao.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wusper.zeyao.R
import com.wusper.zeyao.ZeyaoApp
import com.wusper.zeyao.ai.AiProvider
import com.wusper.zeyao.data.AppSettings
import com.wusper.zeyao.data.ModelRole
import com.wusper.zeyao.data.ModelRoleSettings
import com.wusper.zeyao.ui.components.SectionLabel
import com.wusper.zeyao.ui.components.TopAction
import java.util.Locale

@Composable
fun ModelParamsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    val app = ZeyaoApp.instance
    val provider = AiProvider.fromId(settings.providerId)
    val focus = LocalFocusManager.current
    var sharedKey by remember(settings.providerId) {
        mutableStateOf(app.secrets.getApiKey(settings.providerId).orEmpty())
    }
    var searchModel by remember(settings.searchModel) { mutableStateOf(settings.searchModel) }
    var summarizeModel by remember(settings.summarizeModel, settings.fallbackModels) {
        mutableStateOf(ModelRoleSettings.customModelText(ModelRole.SUMMARIZE, settings))
    }
    var verifyModel by remember(settings.verifyModel) { mutableStateOf(settings.verifyModel) }
    var searchKey by remember(settings.providerId) {
        mutableStateOf(app.secrets.getRoleApiKey(settings.providerId, ModelRole.SEARCH.storageId).orEmpty())
    }
    var summarizeKey by remember(settings.providerId) {
        mutableStateOf(app.secrets.getRoleApiKey(settings.providerId, ModelRole.SUMMARIZE.storageId).orEmpty())
    }
    var verifyKey by remember(settings.providerId) {
        mutableStateOf(app.secrets.getRoleApiKey(settings.providerId, ModelRole.VERIFY.storageId).orEmpty())
    }

    fun persistPending() {
        focus.clearFocus()
        app.secrets.setApiKey(settings.providerId, sharedKey)
        app.secrets.setRoleApiKey(settings.providerId, ModelRole.SEARCH.storageId, searchKey)
        app.secrets.setRoleApiKey(settings.providerId, ModelRole.SUMMARIZE.storageId, summarizeKey)
        app.secrets.setRoleApiKey(settings.providerId, ModelRole.VERIFY.storageId, verifyKey)
        onUpdate {
            it.copy(
                searchModel = searchModel.trim(),
                summarizeModel = summarizeModel.trim(),
                verifyModel = verifyModel.trim(),
            )
        }
    }

    DisposableEffect(settings.providerId) {
        onDispose { persistPending() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopAction(stringResource(R.string.back), onClick = {
                persistPending()
                onBack()
            })
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.model_and_params), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.model_params_provider_hint, provider.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.section_shared_key))
            Text(
                stringResource(R.string.shared_key_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            KeyField(
                value = sharedKey,
                onValueChange = { sharedKey = it },
                label = stringResource(R.string.api_key),
                supporting = stringResource(R.string.shared_key_hint),
                onSave = { app.secrets.setApiKey(settings.providerId, sharedKey) },
            )

            RoleSection(
                title = stringResource(R.string.role_search),
                hint = stringResource(R.string.role_search_hint),
                intensityHint = stringResource(R.string.intensity_hint_search),
                model = searchModel,
                onModelChange = { searchModel = it },
                onModelSave = { onUpdate { it.copy(searchModel = searchModel.trim()) } },
                modelPlaceholder = ModelRoleSettings.defaultModels(ModelRole.SEARCH, provider).first(),
                temperature = settings.searchTemperature.toFloat(),
                onTemperatureSave = { value ->
                    onUpdate { it.copy(searchTemperature = ModelRoleSettings.sanitizeTemperature(value.toDouble(), 0.3)) }
                },
                intensity = ModelRoleSettings.displayIntensity(ModelRole.SEARCH, settings).toFloat(),
                onIntensitySave = { value ->
                    onUpdate { it.copy(searchIntensity = ModelRoleSettings.sanitizeIntensity(value, 3)) }
                },
                apiKey = searchKey,
                onApiKeyChange = { searchKey = it },
                onApiKeySave = {
                    app.secrets.setRoleApiKey(settings.providerId, ModelRole.SEARCH.storageId, searchKey)
                },
            )

            RoleSection(
                title = stringResource(R.string.role_summarize),
                hint = stringResource(R.string.role_summarize_hint),
                intensityHint = stringResource(R.string.intensity_hint_summarize),
                model = summarizeModel,
                onModelChange = { summarizeModel = it },
                onModelSave = { onUpdate { it.copy(summarizeModel = summarizeModel.trim()) } },
                modelPlaceholder = ModelRoleSettings.defaultModels(ModelRole.SUMMARIZE, provider).first(),
                temperature = settings.temperature.toFloat(),
                onTemperatureSave = { value ->
                    onUpdate { it.copy(temperature = ModelRoleSettings.sanitizeTemperature(value.toDouble(), 1.0)) }
                },
                intensity = ModelRoleSettings.displayIntensity(ModelRole.SUMMARIZE, settings).toFloat(),
                onIntensitySave = { value ->
                    val intensity = ModelRoleSettings.sanitizeIntensity(value, 3)
                    onUpdate {
                        it.copy(
                            summarizeIntensity = intensity,
                            maxTokens = ModelRoleSettings.summarizeTokens(intensity),
                        )
                    }
                },
                apiKey = summarizeKey,
                onApiKeyChange = { summarizeKey = it },
                onApiKeySave = {
                    app.secrets.setRoleApiKey(settings.providerId, ModelRole.SUMMARIZE.storageId, summarizeKey)
                },
            )

            RoleSection(
                title = stringResource(R.string.role_verify),
                hint = stringResource(R.string.role_verify_hint),
                intensityHint = stringResource(R.string.intensity_hint_verify),
                model = verifyModel,
                onModelChange = { verifyModel = it },
                onModelSave = { onUpdate { it.copy(verifyModel = verifyModel.trim()) } },
                modelPlaceholder = ModelRoleSettings.defaultModels(ModelRole.VERIFY, provider).first(),
                temperature = settings.verifyTemperature.toFloat(),
                onTemperatureSave = { value ->
                    onUpdate { it.copy(verifyTemperature = ModelRoleSettings.sanitizeTemperature(value.toDouble(), 0.0)) }
                },
                intensity = ModelRoleSettings.displayIntensity(ModelRole.VERIFY, settings).toFloat(),
                onIntensitySave = { value ->
                    onUpdate { it.copy(verifyIntensity = ModelRoleSettings.sanitizeIntensity(value, 1)) }
                },
                apiKey = verifyKey,
                onApiKeyChange = { verifyKey = it },
                onApiKeySave = {
                    app.secrets.setRoleApiKey(settings.providerId, ModelRole.VERIFY.storageId, verifyKey)
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleSection(
    title: String,
    hint: String,
    intensityHint: String,
    model: String,
    onModelChange: (String) -> Unit,
    onModelSave: () -> Unit,
    modelPlaceholder: String,
    temperature: Float,
    onTemperatureSave: (Float) -> Unit,
    intensity: Float,
    onIntensitySave: (Int) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onApiKeySave: () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    SectionLabel(title)
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = model,
        onValueChange = onModelChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.model_id_label)) },
        placeholder = { Text(stringResource(R.string.model_id_placeholder, modelPlaceholder)) },
        supportingText = { Text(stringResource(R.string.model_id_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onModelSave() }),
    )
    var temp by remember(temperature) { mutableFloatStateOf(temperature) }
    Text(
        stringResource(R.string.temperature_label, String.format(Locale.US, "%.1f", temp)),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = temp,
        onValueChange = { temp = it },
        onValueChangeFinished = { onTemperatureSave(temp) },
        valueRange = 0f..2f,
        steps = 19,
    )
    var intensityValue by remember(intensity) { mutableFloatStateOf(intensity) }
    Text(
        stringResource(R.string.intensity_label, intensityValue.toInt()),
        style = MaterialTheme.typography.bodyMedium,
    )
    Slider(
        value = intensityValue,
        onValueChange = { intensityValue = it },
        onValueChangeFinished = { onIntensitySave(intensityValue.toInt()) },
        valueRange = 1f..5f,
        steps = 3,
    )
    Text(intensityHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    KeyField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        label = stringResource(R.string.role_api_key_label),
        supporting = stringResource(R.string.role_api_key_hint),
        onSave = onApiKeySave,
    )
}

@Composable
private fun KeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    onSave: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supporting) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSave() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = stringResource(if (visible) R.string.hide_key else R.string.show_key),
                )
            }
        },
    )
}
