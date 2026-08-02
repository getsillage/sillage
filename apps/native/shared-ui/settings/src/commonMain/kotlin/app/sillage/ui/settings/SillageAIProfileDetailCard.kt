package app.sillage.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.features.settings.editorKey
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageAIProfileDetailStrings(
    val title: String,
    val supporting: String,
    val collapse: String,
    val nameLabel: String,
    val providerLabel: String,
    val anthropicCompatible: String,
    val openAICompatible: String,
    val baseUrlLabel: String,
    val modelLabel: String,
    val modelsLoading: String,
    val getModels: String,
    val temperatureLabel: String,
    val maxTokensLabel: String,
    val apiKeyLabel: String,
    val keepApiKey: String,
    val apiKeyNotConfigured: String,
    val keyDecryptError: String,
    val testing: String,
    val testConnection: String,
    val confirmDelete: String,
    val delete: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SillageAIProfileDetailCard(
    state: SettingsFeatureStateHolder,
    profileIndex: Int,
    strings: SillageAIProfileDetailStrings,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    onNameChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onLoadModels: () -> Unit,
    onTemperatureChange: (String) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onDelete: () -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAIProfileDetailPresentation(
        state = state,
        profileIndex = profileIndex,
        strings = strings,
        editingBlocked = editingBlocked,
        mutationBlocked = mutationBlocked,
    )
    var confirmingDelete by remember(presentation.profileKey) { mutableStateOf(false) }
    var providerMenuExpanded by remember(presentation.profileKey) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        strings.title,
                        modifier = Modifier.semantics { applySillageHeadingSemantics() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        strings.supporting,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(onClick = onClose) {
                    Text(strings.collapse)
                }
            }
            OutlinedTextField(
                value = presentation.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.nameLabel) },
                enabled = presentation.controlsEnabled,
            )
            ExposedDropdownMenuBox(
                expanded = providerMenuExpanded,
                onExpandedChange = { expanded ->
                    providerMenuExpanded = presentation.controlsEnabled && expanded
                },
            ) {
                OutlinedTextField(
                    value = presentation.providerLabel,
                    onValueChange = {},
                    modifier = Modifier
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = presentation.controlsEnabled,
                        )
                        .fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                    label = { Text(strings.providerLabel) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerMenuExpanded)
                    },
                    enabled = presentation.controlsEnabled,
                )
                ExposedDropdownMenu(
                    expanded = providerMenuExpanded,
                    onDismissRequest = { providerMenuExpanded = false },
                ) {
                    SillageAIProviderOptions.forEach { provider ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    sillageAIProviderLabel(
                                        provider = provider,
                                        anthropicCompatible = strings.anthropicCompatible,
                                        openAICompatible = strings.openAICompatible,
                                    ),
                                )
                            },
                            enabled = presentation.controlsEnabled,
                            onClick = {
                                onProviderChange(provider)
                                providerMenuExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = presentation.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.baseUrlLabel) },
                enabled = presentation.controlsEnabled,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = presentation.model,
                    onValueChange = onModelChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.modelLabel) },
                    enabled = presentation.controlsEnabled,
                )
                TextButton(
                    onClick = onLoadModels,
                    enabled = presentation.controlsEnabled,
                ) {
                    Text(presentation.modelsAction)
                }
            }
            if (presentation.modelOptions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presentation.modelOptions.forEach { model ->
                        AssistChip(
                            onClick = { onModelChange(model) },
                            label = {
                                Text(
                                    model,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            enabled = presentation.controlsEnabled,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = presentation.temperatureInput,
                    onValueChange = onTemperatureChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.temperatureLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = presentation.controlsEnabled,
                )
                OutlinedTextField(
                    value = presentation.maxTokensInput,
                    onValueChange = onMaxTokensChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text(strings.maxTokensLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = presentation.controlsEnabled,
                )
            }
            OutlinedTextField(
                value = presentation.apiKeyInput,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(strings.apiKeyLabel) },
                placeholder = { Text(presentation.apiKeyPlaceholder) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                enabled = presentation.controlsEnabled,
            )
            if (presentation.keyUnavailable) {
                Text(
                    strings.keyDecryptError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onTestConnection,
                    enabled = presentation.controlsEnabled,
                ) {
                    Text(presentation.testAction)
                }
                TextButton(
                    onClick = {
                        if (confirmingDelete) {
                            confirmingDelete = false
                            if (onDelete()) {
                                onClose()
                            }
                        } else {
                            confirmingDelete = true
                        }
                    },
                    enabled = presentation.deleteEnabled,
                ) {
                    Text(if (confirmingDelete) strings.confirmDelete else strings.delete)
                }
            }
            presentation.testResult?.let { result ->
                Text(
                    result,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

internal data class SillageAIProfileDetailPresentation(
    val profileKey: String,
    val name: String,
    val providerLabel: String,
    val baseUrl: String,
    val model: String,
    val modelOptions: List<String>,
    val temperatureInput: String,
    val maxTokensInput: String,
    val apiKeyInput: String,
    val apiKeyPlaceholder: String,
    val keyUnavailable: Boolean,
    val testResult: String?,
    val modelsAction: String,
    val testAction: String,
    val controlsEnabled: Boolean,
    val deleteEnabled: Boolean,
)

internal fun sillageAIProfileDetailPresentation(
    state: SettingsFeatureStateHolder,
    profileIndex: Int,
    strings: SillageAIProfileDetailStrings,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
): SillageAIProfileDetailPresentation {
    val profile = state.profiles[profileIndex]
    val profileKey = profile.editorKey(profileIndex)
    val testing = state.testingProfileKey == profileKey
    val loadingModels = state.loadingModelsProfileKey == profileKey
    val controlsEnabled = !editingBlocked && !testing && !loadingModels
    return SillageAIProfileDetailPresentation(
        profileKey = profileKey,
        name = profile.name,
        providerLabel = sillageAIProviderLabel(
            provider = profile.provider,
            anthropicCompatible = strings.anthropicCompatible,
            openAICompatible = strings.openAICompatible,
        ),
        baseUrl = profile.baseUrl,
        model = profile.model,
        modelOptions = state.modelResults[profileKey].orEmpty(),
        temperatureInput = profile.temperatureInput,
        maxTokensInput = profile.maxTokensInput,
        apiKeyInput = profile.apiKeyInput,
        apiKeyPlaceholder = if (profile.hasApiKey) strings.keepApiKey else strings.apiKeyNotConfigured,
        keyUnavailable = profile.keyUnavailable,
        testResult = state.testResults[profileKey],
        modelsAction = if (loadingModels) strings.modelsLoading else strings.getModels,
        testAction = if (testing) strings.testing else strings.testConnection,
        controlsEnabled = controlsEnabled,
        deleteEnabled = controlsEnabled && !mutationBlocked,
    )
}
