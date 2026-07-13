package app.sillage.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel

@Composable
internal fun ModeSelectionScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.mode_title),
        supporting = stringResource(R.string.mode_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
    ) {
        ModeOptionCard(
            icon = Icons.Rounded.OfflineBolt,
            title = stringResource(R.string.mode_offline),
            supporting = stringResource(R.string.mode_offline_supporting),
            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
            iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
        )
        ModeOptionCard(
            icon = Icons.Rounded.CloudSync,
            title = stringResource(R.string.mode_online),
            supporting = stringResource(R.string.mode_online_supporting),
            iconContainer = MaterialTheme.colorScheme.primaryContainer,
            iconContent = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = viewModel::chooseOnlineMode,
            enabled = !state.loading,
        )
    }
}

@Composable
private fun ModeOptionCard(
    icon: ImageVector,
    title: String,
    supporting: String,
    iconContainer: Color,
    iconContent: Color,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = iconContainer,
                contentColor = iconContent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    supporting,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ServerScreen(state: SillageUiState, viewModel: SillageViewModel) {
    BackHandler(enabled = !state.loading, onBack = viewModel::cancelServerConnection)
    AuthScaffold(
        title = stringResource(R.string.server_title),
        supporting = stringResource(R.string.server_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::cancelServerConnection, enabled = !state.loading) {
                Text(stringResource(if (state.serverReturnScreen != null) R.string.action_back else R.string.action_cancel))
            }
        },
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.server_address)) },
            placeholder = { Text(stringResource(R.string.server_address_placeholder)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { viewModel.saveServer() }),
            enabled = !state.loading,
        )
        Button(
            onClick = viewModel::saveServer,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            AuthButtonContent(
                loading = state.loading,
                icon = Icons.Rounded.CloudSync,
                text = stringResource(if (state.loading) R.string.server_connecting else R.string.server_save_connect),
            )
        }
        TextButton(
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Rounded.OfflineBolt,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.server_use_offline))
        }
    }
}

@Composable
internal fun InitializeScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.initialize_title),
        supporting = stringResource(R.string.initialize_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text(stringResource(R.string.server_label))
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.account_username)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.account_display_name)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = !state.loading,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            enabled = !state.loading,
            onDone = viewModel::initialize,
        )
        Button(
            onClick = viewModel::initialize,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            AuthButtonContent(
                loading = state.loading,
                text = stringResource(if (state.loading) R.string.account_creating else R.string.account_create_enter),
            )
        }
    }
}

@Composable
internal fun LoginScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = stringResource(R.string.login_title),
        supporting = stringResource(R.string.login_supporting),
        state = state,
        onLanguageToggle = viewModel::toggleLanguageMode,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text(stringResource(R.string.server_label))
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.account_username)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = !state.loading,
        )
        PasswordField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            enabled = !state.loading,
            onDone = viewModel::signIn,
        )
        Button(
            onClick = viewModel::signIn,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            AuthButtonContent(
                loading = state.loading,
                text = stringResource(if (state.loading) R.string.login_signing_in else R.string.login_action),
            )
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onDone: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    val visibilityLabel = stringResource(
        if (visible) R.string.account_hide_password else R.string.account_show_password,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(R.string.account_password)) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
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
                    imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = visibilityLabel,
                )
            }
        },
        enabled = enabled,
    )
}

@Composable
private fun AuthButtonContent(
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

@Composable
private fun AuthScaffold(
    title: String,
    supporting: String,
    state: SillageUiState,
    onLanguageToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = colorResource(R.color.ic_launcher_background),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.brand_tagline),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onLanguageToggle, enabled = !state.loading) {
                        Icon(
                            Icons.Rounded.Language,
                            contentDescription = stringResource(
                                if (state.languageMode == app.sillage.data.SessionStore.LANGUAGE_ZH_CN) {
                                    R.string.language_switch_to_english
                                } else {
                                    R.string.language_switch_to_chinese
                                },
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            supporting,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (trailing != null) {
                        Box(modifier = Modifier.padding(start = 8.dp)) {
                            trailing()
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.authError?.let { message ->
                        AuthErrorMessage(message)
                    }
                    content()
                }
            }
        }
    }
}

@Composable
private fun AuthErrorMessage(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { applyAuthErrorSemantics(message) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal fun SemanticsPropertyReceiver.applyAuthErrorSemantics(message: String) {
    liveRegion = LiveRegionMode.Assertive
    error(message)
}
