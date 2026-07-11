package app.sillage.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
        )
        ModeOptionCard(
            icon = Icons.Rounded.CloudSync,
            title = "在线模式",
            supporting = "连接自托管服务，同步附件和 AI 能力。",
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
    onClick: () -> Unit,
    enabled: Boolean,
) {
    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
        }
    }
}

@Composable
internal fun ServerScreen(state: SillageUiState, viewModel: SillageViewModel) {
    BackHandler(onBack = viewModel::cancelServerConnection)
    AuthScaffold(
        title = "连接 Sillage",
        supporting = "填写后端服务地址。模拟器访问本机服务可使用 http://10.0.2.2:5231。",
        state = state,
        trailing = {
            TextButton(onClick = viewModel::cancelServerConnection) {
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
        )
        Button(
            onClick = viewModel::saveServer,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.CloudSync, contentDescription = null)
            Text(if (state.loading) "连接中" else "保存并连接")
        }
        TextButton(
            onClick = viewModel::useOfflineMode,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OfflineBolt, contentDescription = null)
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
            TextButton(onClick = viewModel::openServerSettings) {
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
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = viewModel::updateDisplayName,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("显示名") },
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = viewModel::initialize,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "创建中" else "创建并进入")
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
            TextButton(onClick = viewModel::openServerSettings) {
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
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("密码") },
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = viewModel::signIn,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.loading) "登录中" else "登录")
        }
    }
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
            .imePadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item {
            ElevatedCard(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Sillage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    supporting,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        trailing?.invoke()
                    }
                    MessageBlock(state.error, state.notice)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        content()
                    }
                }
            }
        }
    }
}
