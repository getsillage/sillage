package app.sillage.ui.application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.designsystem.SillageDesignTheme
import app.sillage.ui.designsystem.SillageNavigationBar
import app.sillage.ui.designsystem.SillageNavigationItem
import kotlinx.coroutines.launch

private val WideNavigationBreakpoint = 760.dp

@Composable
fun SillageNativeApp(
    controller: SillageNativeController,
    platform: SillageNativePlatform,
    modifier: Modifier = Modifier,
) {
    val state = controller.state
    val strings = sillageNativeStrings(state.appearance.languageMode)
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(state.feedback, strings) {
        val feedback = state.feedback ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(strings.message(feedback))
        controller.dismissFeedback()
    }

    pendingNavigation?.let { action ->
        SillageNativeDiscardChangesDialog(
            languageMode = state.appearance.languageMode,
            themeMode = state.appearance.themeMode,
            onDismissRequest = { pendingNavigation = null },
            onDiscard = {
                pendingNavigation = null
                action()
            },
        )
    }

    fun guardedNavigation(action: () -> Unit) {
        if (controller.hasUnsavedEditorChanges) {
            pendingNavigation = action
        } else {
            action()
        }
    }

    SillageDesignTheme(
        darkTheme = state.appearance.themeMode == ClientPreferenceValues.THEME_DARK,
    ) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= WideNavigationBreakpoint
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SillageNativeRail(
                            state = state,
                            strings = strings,
                            onRecords = { guardedNavigation(controller::navigateToRecords) },
                            onSettings = { guardedNavigation(controller::navigateToSettings) },
                        )
                        VerticalDivider()
                        SillageNativeContent(
                            controller = controller,
                            platform = platform,
                            strings = strings,
                            wide = true,
                            onGuardedCloseEditor = { guardedNavigation(controller::closeEditor) },
                            onGuardedAction = ::guardedNavigation,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            SillageNativeBottomNavigation(
                                state = state,
                                strings = strings,
                                onRecords = { guardedNavigation(controller::navigateToRecords) },
                                onSettings = { guardedNavigation(controller::navigateToSettings) },
                            )
                        },
                    ) { padding ->
                        SillageNativeContent(
                            controller = controller,
                            platform = platform,
                            strings = strings,
                            wide = false,
                            onGuardedCloseEditor = { guardedNavigation(controller::closeEditor) },
                            onGuardedAction = ::guardedNavigation,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SillageNativeContent(
    controller: SillageNativeController,
    platform: SillageNativePlatform,
    strings: SillageNativeStrings,
    wide: Boolean,
    onGuardedCloseEditor: () -> Unit,
    onGuardedAction: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    when (controller.state.clientContext.screen) {
        AppDestination.AISettings -> SillageNativeSettings(
            state = controller.state,
            platform = platform,
            strings = strings,
            onDarkThemeChange = controller::setDarkTheme,
            onLanguageChange = controller::setLanguage,
            onServerBaseUrlChange = controller::updateServerBaseUrl,
            onCheckServer = { scope.launch { controller.checkServerConnection() } },
            onAuthenticationUsernameChange = controller::updateAuthenticationUsername,
            onAuthenticationDisplayNameChange = controller::updateAuthenticationDisplayName,
            onAuthenticationPasswordChange = controller::updateAuthenticationPassword,
            onAuthenticate = { scope.launch { controller.authenticate() } },
            onSignOut = { scope.launch { controller.signOut() } },
            onExportBackup = platform.exportBackup?.let { operation ->
                { scope.launch { controller.exportBackup(operation) } }
            },
            onRestoreBackup = platform.restoreBackup?.let { operation ->
                { scope.launch { controller.restoreBackup(operation) } }
            },
            modifier = modifier,
        )
        else -> SillageRecordsWorkspace(
            controller = controller,
            strings = strings,
            wide = wide,
            onGuardedCloseEditor = onGuardedCloseEditor,
            onGuardedAction = onGuardedAction,
            modifier = modifier,
        )
    }
}

@Composable
private fun SillageNativeRail(
    state: SillageNativeState,
    strings: SillageNativeStrings,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationRail(
        header = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .size(40.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("S", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        val recordsSelected = state.clientContext.screen != AppDestination.AISettings
        SillageRailItem(
            selected = recordsSelected,
            enabled = true,
            icon = Icons.Outlined.Description,
            label = strings.records,
            onClick = onRecords,
        )
        SillageRailItem(
            selected = false,
            enabled = false,
            icon = Icons.Outlined.AutoAwesome,
            label = strings.ask,
            onClick = {},
        )
        SillageRailItem(
            selected = !recordsSelected,
            enabled = true,
            icon = Icons.Outlined.Settings,
            label = strings.settings,
            onClick = onSettings,
        )
    }
}

@Composable
private fun SillageRailItem(
    selected: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}

@Composable
private fun SillageNativeBottomNavigation(
    state: SillageNativeState,
    strings: SillageNativeStrings,
    onRecords: () -> Unit,
    onSettings: () -> Unit,
) {
    val recordsSelected = state.clientContext.screen != AppDestination.AISettings
    SillageNavigationBar {
        SillageNavigationItem(
            selected = recordsSelected,
            onClick = onRecords,
            enabled = true,
            icon = Icons.Outlined.Description,
            label = strings.records,
        )
        SillageNavigationItem(
            selected = false,
            onClick = {},
            enabled = false,
            icon = Icons.Outlined.AutoAwesome,
            label = strings.ask,
        )
        SillageNavigationItem(
            selected = !recordsSelected,
            onClick = onSettings,
            enabled = true,
            icon = Icons.Outlined.Settings,
            label = strings.settings,
        )
    }
}

private fun SillageNativeStrings.message(feedback: SillageNativeFeedback): String = when (feedback) {
    SillageNativeFeedback.RecordSaved -> saved
    SillageNativeFeedback.RecordDeleted -> deleted
    SillageNativeFeedback.RecordRestored -> restored
    SillageNativeFeedback.RecordPurged -> purged
    SillageNativeFeedback.BackupExported -> backupExported
    SillageNativeFeedback.BackupRestored -> backupRestored
    SillageNativeFeedback.AccountInitialized -> accountInitialized
    SillageNativeFeedback.SignedIn -> signedIn
    SillageNativeFeedback.SignedOut -> signedOut
    SillageNativeFeedback.SignedOutLocally -> signedOutLocally
    SillageNativeFeedback.DataTransferFailed -> dataTransferFailed
    SillageNativeFeedback.StorageUnavailable -> storageUnavailable
}
