package app.sillage.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.sillage.features.auth.AuthFeatureStateHolder

data class SillageInitializeFormStrings(
    val usernameLabel: String,
    val displayNameLabel: String,
    val password: SillagePasswordFieldStrings,
    val submit: String,
    val submitting: String,
)

@Composable
fun SillageInitializeForm(
    state: AuthFeatureStateHolder,
    loading: Boolean,
    strings: SillageInitializeFormStrings,
    showPasswordIcon: ImageVector,
    hidePasswordIcon: ImageVector,
    onUsernameChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageInitializeFormPresentation(
        state = state,
        loading = loading,
        strings = strings,
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = presentation.username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.usernameLabel) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = presentation.controlsEnabled,
        )
        OutlinedTextField(
            value = presentation.displayName,
            onValueChange = onDisplayNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.displayNameLabel) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = presentation.controlsEnabled,
        )
        SillagePasswordField(
            value = presentation.password,
            onValueChange = onPasswordChange,
            enabled = presentation.controlsEnabled,
            onDone = onSubmit,
            strings = strings.password,
            showPasswordIcon = showPasswordIcon,
            hidePasswordIcon = hidePasswordIcon,
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
                text = presentation.actionText,
            )
        }
    }
}

internal data class SillageInitializeFormPresentation(
    val username: String,
    val displayName: String,
    val password: String,
    val controlsEnabled: Boolean,
    val actionText: String,
)

internal fun sillageInitializeFormPresentation(
    state: AuthFeatureStateHolder,
    loading: Boolean,
    strings: SillageInitializeFormStrings,
) = SillageInitializeFormPresentation(
    username = state.username,
    displayName = state.displayName,
    password = state.password,
    controlsEnabled = !loading,
    actionText = if (loading) strings.submitting else strings.submit,
)
