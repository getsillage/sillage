package app.sillage.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sillage.features.auth.AuthFeatureStateHolder
import app.sillage.ui.designsystem.SillageSettingsActionRow

data class SillageAccountSettingsStrings(
    val changePasswordTitle: String,
    val changePasswordSupporting: String,
    val currentPasswordLabel: String,
    val newPasswordLabel: String,
    val confirmPasswordLabel: String,
    val savePassword: String,
    val signOut: String,
)

@Composable
fun SillageAccountSettingsContent(
    state: AuthFeatureStateHolder,
    mutationBlocked: Boolean,
    strings: SillageAccountSettingsStrings,
    signOutSupporting: String,
    signOutIcon: ImageVector,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSavePassword: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageAccountSettingsPresentation(
        state = state,
        mutationBlocked = mutationBlocked,
    )

    Column(modifier = modifier) {
        Text(
            strings.changePasswordTitle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            strings.changePasswordSupporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = presentation.currentPassword,
            onValueChange = onCurrentPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = presentation.controlsEnabled,
            label = { Text(strings.currentPasswordLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = presentation.newPassword,
            onValueChange = onNewPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = presentation.controlsEnabled,
            label = { Text(strings.newPasswordLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        OutlinedTextField(
            value = presentation.confirmPassword,
            onValueChange = onConfirmPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = presentation.controlsEnabled,
            label = { Text(strings.confirmPasswordLabel) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Button(
            onClick = onSavePassword,
            enabled = presentation.controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (presentation.passwordChanging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(strings.savePassword)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        SillageSettingsActionRow(
            icon = signOutIcon,
            title = strings.signOut,
            supporting = signOutSupporting,
            onClick = onSignOut,
            enabled = presentation.signOutEnabled,
        )
    }
}

internal data class SillageAccountSettingsPresentation(
    val currentPassword: String,
    val newPassword: String,
    val confirmPassword: String,
    val passwordChanging: Boolean,
    val controlsEnabled: Boolean,
    val signOutEnabled: Boolean,
)

internal fun sillageAccountSettingsPresentation(
    state: AuthFeatureStateHolder,
    mutationBlocked: Boolean,
) = SillageAccountSettingsPresentation(
    currentPassword = state.currentPassword,
    newPassword = state.newPassword,
    confirmPassword = state.confirmPassword,
    passwordChanging = state.passwordChanging,
    controlsEnabled = !state.passwordChanging && !mutationBlocked,
    signOutEnabled = !state.passwordChanging && !mutationBlocked,
)
