package app.sillage.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.sillage.features.settings.SettingsFeatureStateHolder
import app.sillage.ui.designsystem.applySillageHeadingSemantics

data class SillageAIProfilesHeaderStrings(
    val title: String,
    val supporting: String,
    val newProfile: String,
    val saving: String,
    val save: String,
)

@Composable
fun SillageAIProfilesHeaderCard(
    state: SettingsFeatureStateHolder,
    strings: SillageAIProfilesHeaderStrings,
    addIcon: ImageVector,
    saveIcon: ImageVector,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAIProfilesHeaderPresentation(
        state = state,
        strings = strings,
        editingBlocked = editingBlocked,
        mutationBlocked = mutationBlocked,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            presentation.title,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics { applySillageHeadingSemantics() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    presentation.supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAdd,
                        enabled = presentation.addEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(
                            addIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(presentation.newProfileAction)
                    }
                    Button(
                        onClick = onSave,
                        enabled = presentation.saveEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        if (presentation.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                saveIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(presentation.saveAction)
                    }
                }
            }
        }
    }
}

internal data class SillageAIProfilesHeaderPresentation(
    val title: String,
    val supporting: String,
    val newProfileAction: String,
    val saveAction: String,
    val saving: Boolean,
    val addEnabled: Boolean,
    val saveEnabled: Boolean,
)

internal fun sillageAIProfilesHeaderPresentation(
    state: SettingsFeatureStateHolder,
    strings: SillageAIProfilesHeaderStrings,
    editingBlocked: Boolean,
    mutationBlocked: Boolean,
) = SillageAIProfilesHeaderPresentation(
    title = strings.title,
    supporting = strings.supporting,
    newProfileAction = strings.newProfile,
    saveAction = if (state.profilesSaving) strings.saving else strings.save,
    saving = state.profilesSaving,
    addEnabled = !editingBlocked && !state.profilesSaving,
    saveEnabled = !mutationBlocked && !state.profilesSaving,
)
