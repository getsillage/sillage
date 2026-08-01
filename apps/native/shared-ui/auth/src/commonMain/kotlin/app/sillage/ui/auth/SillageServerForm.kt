package app.sillage.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class SillageServerFormStrings(
    val addressLabel: String,
    val addressPlaceholder: String,
    val submit: String,
    val submitting: String,
    val useOffline: String,
)

@Composable
fun SillageServerForm(
    baseUrl: String,
    loading: Boolean,
    strings: SillageServerFormStrings,
    connectIcon: ImageVector,
    offlineIcon: ImageVector,
    onBaseUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onUseOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageServerFormPresentation(
        baseUrl = baseUrl,
        loading = loading,
        strings = strings,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = presentation.baseUrl,
            onValueChange = onBaseUrlChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.addressLabel) },
            placeholder = { Text(strings.addressPlaceholder) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            enabled = presentation.controlsEnabled,
        )
        Button(
            onClick = onSubmit,
            enabled = presentation.controlsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            SillageAuthButtonContent(
                loading = loading,
                icon = connectIcon,
                text = presentation.actionText,
            )
        }
        TextButton(
            onClick = onUseOffline,
            enabled = presentation.controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                offlineIcon,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(strings.useOffline)
        }
    }
}

internal data class SillageServerFormPresentation(
    val baseUrl: String,
    val controlsEnabled: Boolean,
    val actionText: String,
)

internal fun sillageServerFormPresentation(
    baseUrl: String,
    loading: Boolean,
    strings: SillageServerFormStrings,
) = SillageServerFormPresentation(
    baseUrl = baseUrl,
    controlsEnabled = !loading,
    actionText = if (loading) strings.submitting else strings.submit,
)
