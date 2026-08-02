package app.sillage.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.features.settings.editorKey
import app.sillage.ui.designsystem.SillageSettingsEmptyCard

data class SillageAIProfilesEditorStrings(
    val header: SillageAIProfilesHeaderStrings,
    val empty: String,
    val summary: SillageAIProfileSummaryStrings,
    val detail: SillageAIProfileDetailStrings,
)

@Stable
class SillageAIProfilesEditorState internal constructor() {
    private var selectedProfileIndex by mutableStateOf<Int?>(null)

    internal fun selectedIndex(profileCount: Int): Int? =
        selectedProfileIndex?.takeIf { it in 0 until profileCount }

    internal fun select(index: Int) {
        selectedProfileIndex = index
    }

    internal fun clearSelection() {
        selectedProfileIndex = null
    }
}

@Composable
fun rememberSillageAIProfilesEditorState(): SillageAIProfilesEditorState =
    remember { SillageAIProfilesEditorState() }

fun LazyListScope.sillageAIProfilesEditorItems(
    state: SettingsFeatureStateHolder,
    editorState: SillageAIProfilesEditorState,
    strings: SillageAIProfilesEditorStrings,
    addIcon: ImageVector,
    saveIcon: ImageVector,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    onSetDefault: (Int) -> Unit,
    onNameChange: (Int, String) -> Unit,
    onProviderChange: (Int, String) -> Unit,
    onBaseUrlChange: (Int, String) -> Unit,
    onModelChange: (Int, String) -> Unit,
    onLoadModels: (Int) -> Unit,
    onTemperatureChange: (Int, String) -> Unit,
    onMaxTokensChange: (Int, String) -> Unit,
    onApiKeyChange: (Int, String) -> Unit,
    onTestConnection: (Int) -> Unit,
    onDelete: (Int) -> Boolean,
) {
    item(key = "ai-profiles-header") {
        SillageAIProfilesHeaderCard(
            state = state,
            strings = strings.header,
            addIcon = addIcon,
            saveIcon = saveIcon,
            editingBlocked = editingBlocked,
            mutationBlocked = mutationBlocked,
            onAdd = {
                editorState.select(state.profiles.size)
                onAdd()
            },
            onSave = onSave,
        )
    }
    if (state.profiles.isEmpty()) {
        item(key = "ai-profiles-empty") {
            SillageSettingsEmptyCard(strings.empty)
        }
    } else {
        items(
            count = state.profiles.size,
            key = { index -> state.profiles[index].editorKey(index) },
        ) { index ->
            val selected = editorState.selectedIndex(state.profiles.size) == index
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SillageAIProfileSummaryCard(
                    state = state,
                    profileIndex = index,
                    strings = strings.summary,
                    selected = selected,
                    editingBlocked = editingBlocked,
                    mutationBlocked = mutationBlocked,
                    onConfigure = { editorState.select(index) },
                    onSetDefault = { onSetDefault(index) },
                )
                if (selected) {
                    SillageAIProfileDetailCard(
                        state = state,
                        profileIndex = index,
                        strings = strings.detail,
                        editingBlocked = editingBlocked,
                        mutationBlocked = mutationBlocked,
                        onNameChange = { onNameChange(index, it) },
                        onProviderChange = { onProviderChange(index, it) },
                        onBaseUrlChange = { onBaseUrlChange(index, it) },
                        onModelChange = { onModelChange(index, it) },
                        onLoadModels = { onLoadModels(index) },
                        onTemperatureChange = { onTemperatureChange(index, it) },
                        onMaxTokensChange = { onMaxTokensChange(index, it) },
                        onApiKeyChange = { onApiKeyChange(index, it) },
                        onTestConnection = { onTestConnection(index) },
                        onDelete = { onDelete(index) },
                        onClose = editorState::clearSelection,
                    )
                }
            }
        }
    }
}
