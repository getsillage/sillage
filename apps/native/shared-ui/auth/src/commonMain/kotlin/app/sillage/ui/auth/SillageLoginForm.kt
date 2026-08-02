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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.sillage.features.auth.AuthFeatureStateHolder

data class SillagePasswordFieldStrings(
    val label: String,
    val showPassword: String,
    val hidePassword: String,
)

data class SillageLoginFormStrings(
    val usernameLabel: String,
    val password: SillagePasswordFieldStrings,
    val submit: String,
    val submitting: String,
)

@Composable
fun SillageLoginForm(
    state: AuthFeatureStateHolder,
    loading: Boolean,
    strings: SillageLoginFormStrings,
    showPasswordIcon: ImageVector,
    hidePasswordIcon: ImageVector,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = sillageLoginFormPresentation(
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

@Composable
fun SillagePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
    strings: SillagePasswordFieldStrings,
    showPasswordIcon: ImageVector,
    hidePasswordIcon: ImageVector,
    modifier: Modifier = Modifier,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val visibilityLabel = if (visible) strings.hidePassword else strings.showPassword

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(strings.label) },
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible },
                enabled = enabled,
            ) {
                Icon(
                    imageVector = if (visible) hidePasswordIcon else showPasswordIcon,
                    contentDescription = visibilityLabel,
                )
            }
        },
        enabled = enabled,
    )
}

@Composable
fun SillageAuthButtonContent(
    loading: Boolean,
    text: String,
    icon: ImageVector? = null,
) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(ButtonDefaults.IconSize),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else if (icon != null) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
    }
    if (loading || icon != null) {
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    }
    Text(text)
}

internal data class SillageLoginFormPresentation(
    val username: String,
    val password: String,
    val controlsEnabled: Boolean,
    val actionText: String,
)

internal fun sillageLoginFormPresentation(
    state: AuthFeatureStateHolder,
    loading: Boolean,
    strings: SillageLoginFormStrings,
) = SillageLoginFormPresentation(
    username = state.username,
    password = state.password,
    controlsEnabled = !loading,
    actionText = if (loading) strings.submitting else strings.submit,
)
