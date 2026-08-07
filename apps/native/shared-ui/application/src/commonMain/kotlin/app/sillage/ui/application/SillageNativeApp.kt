package app.sillage.ui.application

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.LifecycleStartEffect
import app.sillage.core.application.preferences.ClientPreferenceValues
import app.sillage.ui.appshell.AppDestination
import app.sillage.ui.designsystem.SillageDesignTheme
import app.sillage.ui.designsystem.SillageNavigationBar
import app.sillage.ui.designsystem.SillageNavigationItem
import app.sillage.ui.sync.SillageSyncConflictDialog
import app.sillage.ui.sync.SillageSyncConflictStrings
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
    val scope = rememberCoroutineScope()
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(controller, platform.authenticationPersistsAcrossLaunches) {
        if (platform.authenticationPersistsAcrossLaunches) {
            controller.resumeSavedAuthentication()
        }
    }

    val authenticatedAccountId = state.authentication.account?.id
    val networkStatus = platform.networkStatus
    LifecycleStartEffect(Triple(controller, authenticatedAccountId, networkStatus)) {
        val automaticSync = authenticatedAccountId?.let {
            scope.launch { controller.syncMemosAutomatically() }
        }
        val recoverySync = authenticatedAccountId?.let {
            networkStatus?.let { statuses ->
                scope.launch {
                    statuses.networkRecoveryEvents().collect {
                        controller.syncMemosAutomatically()
                    }
                }
            }
        }
        onStopOrDispose {
            automaticSync?.cancel()
            recoverySync?.cancel()
        }
    }

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
        SillageSyncConflictDialog(
            state = state.sync,
            strings = SillageSyncConflictStrings(
                title = strings.syncConflictTitle,
                supporting = strings.syncConflictSupporting,
                localLabel = strings.syncConflictLocalLabel,
                serverLabel = strings.syncConflictServerLabel,
                emptyLocal = strings.syncConflictEmptyLocal,
                emptyServer = strings.syncConflictEmptyServer,
                keepLocal = strings.syncConflictKeepLocal,
                takeServer = strings.syncConflictTakeServer,
                dismiss = strings.syncConflictDismiss,
            ),
            onKeepLocal = { resourceId ->
                scope.launch { controller.keepLocalSyncConflict(resourceId) }
            },
            onTakeServer = { resourceId ->
                scope.launch { controller.takeServerSyncConflict(resourceId) }
            },
            onDismiss = controller::dismissSyncConflict,
        )
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
            onAuthenticationCurrentPasswordChange = controller::updateAuthenticationCurrentPassword,
            onAuthenticationNewPasswordChange = controller::updateAuthenticationNewPassword,
            onAuthenticationConfirmPasswordChange = controller::updateAuthenticationConfirmPassword,
            onAuthenticate = { scope.launch { controller.authenticate() } },
            onChangePassword = { scope.launch { controller.changePassword() } },
            onSyncMemos = { scope.launch { controller.syncMemos() } },
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
        sillageNativePrimaryNavigationItems(state.clientContext.screen).forEach { item ->
            when (item.destination) {
                SillageNativePrimaryDestination.Records -> SillageRailItem(
                    selected = item.selected,
                    enabled = true,
                    icon = Icons.Outlined.Description,
                    label = strings.records,
                    onClick = onRecords,
                )

                SillageNativePrimaryDestination.Settings -> SillageRailItem(
                    selected = item.selected,
                    enabled = true,
                    icon = Icons.Outlined.Settings,
                    label = strings.settings,
                    onClick = onSettings,
                )
            }
        }
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
    SillageNavigationBar {
        sillageNativePrimaryNavigationItems(state.clientContext.screen).forEach { item ->
            when (item.destination) {
                SillageNativePrimaryDestination.Records -> SillageNavigationItem(
                    selected = item.selected,
                    onClick = onRecords,
                    enabled = true,
                    icon = Icons.Outlined.Description,
                    label = strings.records,
                )

                SillageNativePrimaryDestination.Settings -> SillageNavigationItem(
                    selected = item.selected,
                    onClick = onSettings,
                    enabled = true,
                    icon = Icons.Outlined.Settings,
                    label = strings.settings,
                )
            }
        }
    }
}

internal enum class SillageNativePrimaryDestination {
    Records,
    Settings,
}

internal data class SillageNativePrimaryNavigationItem(
    val destination: SillageNativePrimaryDestination,
    val selected: Boolean,
)

internal fun sillageNativePrimaryNavigationItems(
    screen: AppDestination,
): List<SillageNativePrimaryNavigationItem> = listOf(
    SillageNativePrimaryNavigationItem(
        destination = SillageNativePrimaryDestination.Records,
        selected = screen != AppDestination.AISettings,
    ),
    SillageNativePrimaryNavigationItem(
        destination = SillageNativePrimaryDestination.Settings,
        selected = screen == AppDestination.AISettings,
    ),
)

private fun SillageNativeStrings.message(feedback: SillageNativeFeedback): String = when (feedback) {
    SillageNativeFeedback.RecordSaved -> saved
    SillageNativeFeedback.RecordDeleted -> deleted
    SillageNativeFeedback.RecordRestored -> restored
    SillageNativeFeedback.RecordPurged -> purged
    SillageNativeFeedback.BackupExported -> backupExported
    SillageNativeFeedback.BackupRestored -> backupRestored
    SillageNativeFeedback.AccountInitialized -> accountInitialized
    SillageNativeFeedback.SignedIn -> signedIn
    SillageNativeFeedback.PasswordChanged -> passwordChanged
    SillageNativeFeedback.SignedOut -> signedOut
    SillageNativeFeedback.SignedOutLocally -> signedOutLocally
    SillageNativeFeedback.MemoSyncCompleted -> memoSyncCompleted
    SillageNativeFeedback.MemoSyncNoChanges -> memoSyncNoChanges
    SillageNativeFeedback.MemoSyncNeedsReview -> memoSyncNeedsReview
    SillageNativeFeedback.MemoSyncRejected -> memoSyncRejected
    SillageNativeFeedback.MemoSyncFailed -> memoSyncFailed
    SillageNativeFeedback.MemoSyncServerMismatch -> memoSyncServerMismatch
    SillageNativeFeedback.MemoSyncSessionExpired -> memoSyncSessionExpired
    SillageNativeFeedback.MemoSyncConflictResolved -> memoSyncConflictResolved
    SillageNativeFeedback.DataTransferFailed -> dataTransferFailed
    SillageNativeFeedback.StorageUnavailable -> storageUnavailable
}
