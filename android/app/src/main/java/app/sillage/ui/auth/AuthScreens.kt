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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sillage.R
import app.sillage.ui.SillageUiState
import app.sillage.ui.SillageViewModel
import app.sillage.ui.common.MessageBlock

@Composable
internal fun ModeSelectionScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "选择使用方式",
        supporting = "之后可以在设置里切换。",
        state = state,
    ) {
        ModeOptionCard(
            icon = Icons.Rounded.OfflineBolt,
            title = "离线模式",
            supporting = "记录只保存在当前设备。",
            iconContainer = MaterialTheme.colorScheme.secondaryContainer,
            iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
        )
        ModeOptionCard(
            icon = Icons.Rounded.CloudSync,
            title = "在线模式",
            supporting = "连接自托管服务，同步附件和 AI 能力。",
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
        title = "连接 Sillage",
        supporting = "填写后端服务地址。模拟器访问本机服务可使用 http://10.0.2.2:5231。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::cancelServerConnection, enabled = !state.loading) {
                Text(if (state.serverReturnScreen != null) "返回" else "取消")
            }
        },
    ) {
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::updateBaseUrl,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("服务器地址") },
            placeholder = { Text("https://example.com 或 192.168.1.10:5231") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
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
                text = if (state.loading) "连接中" else "保存并连接",
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
            Text("离线使用")
        }
    }
}

@Composable
internal fun InitializeScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "创建唯一账号",
        supporting = "这是你的私密记录空间，初始化后不允许创建第二个账号。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text("服务器")
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("显示名") },
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.loading,
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
                text = if (state.loading) "创建中" else "创建并进入",
            )
        }
    }
}

@Composable
internal fun LoginScreen(state: SillageUiState, viewModel: SillageViewModel) {
    AuthScaffold(
        title = "登录 Sillage",
        supporting = "登录后可查看和编辑你的记录。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::openServerSettings, enabled = !state.loading) {
                Text("服务器")
            }
        },
    ) {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("账号") },
            enabled = !state.loading,
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.loading,
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
                text = if (state.loading) "登录中" else "登录",
            )
        }
    }
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
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Sillage", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "私密记录与 AI 反思",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
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
                MessageBlock(state.error, state.notice)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    content()
                }
            }
        }
    }
}
