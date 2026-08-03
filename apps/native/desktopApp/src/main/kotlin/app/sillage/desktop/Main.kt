package app.sillage.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.sillage.core.localdata.LocalClientRepository
import app.sillage.core.network.RemoteInstanceAuthenticationRepositoryFactory
import app.sillage.core.network.RemoteInstanceBootstrapRepository
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativeDiscardChangesDialog
import app.sillage.ui.application.SillageNativePlatform
import app.sillage.ui.application.sillageNativeHostStrings
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.FilenameFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.Locale
import javax.swing.JOptionPane

internal const val DesktopVersion = "0.3.1"

fun main() {
    val snapshotPath = DesktopDataPaths.defaultSnapshotPath()
    val instanceLock = try {
        DesktopClientInstanceLock.tryAcquire(snapshotPath)
    } catch (error: Exception) {
        showDesktopStartupError(error)
        return
    }
    if (instanceLock == null) {
        showDesktopStartupError(null)
        return
    }

    instanceLock.use {
        runDesktopApplication(snapshotPath)
    }
}

private fun runDesktopApplication(snapshotPath: Path) = application {
    val storage = remember(snapshotPath) { DesktopClientSnapshotStorage(snapshotPath) }
    val repository = remember(storage) {
        LocalClientRepository(storage, DesktopRuntimeValues())
    }
            val httpTransport = remember { DesktopSillageHttpTransport() }
            val bootstrapRepository = remember(httpTransport) {
                RemoteInstanceBootstrapRepository(httpTransport)
            }
            val authenticationCredentialStore = remember {
                desktopAuthenticationCredentialStore()
            }
            val authenticationRepositoryFactory = remember(
                httpTransport,
                authenticationCredentialStore,
            ) {
                RemoteInstanceAuthenticationRepositoryFactory(
                    transport = httpTransport,
                    credentialStore = authenticationCredentialStore,
                )
            }
            val controller = remember(
                repository,
                bootstrapRepository,
                authenticationRepositoryFactory,
            ) {
                SillageNativeController(
            recordsRepository = repository,
            recordWriteRepository = repository,
            recordLifecycleRepository = repository,
                    preferencesRepository = repository,
                    bootstrapRepository = bootstrapRepository,
                    authenticationRepositoryFactory = authenticationRepositoryFactory,
                    todayProvider = { LocalDate.now().toString() },
                    memoSyncWorkspaceFactory = repository,
                    memoSyncGatewayFactory = authenticationRepositoryFactory,
                )
    }
    val primaryShortcutUsesMeta = remember { isMacOs() }
    val hostStrings = sillageNativeHostStrings(controller.state.appearance.languageMode)
    val thirdPartyNotices = remember { loadDesktopThirdPartyNotices() }
    var pendingHostAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun runGuarded(action: () -> Unit) {
        if (controller.hasUnsavedEditorChanges) {
            pendingHostAction = action
        } else {
            action()
        }
    }

    fun primaryShortcut(key: Key) = KeyShortcut(
        key = key,
        ctrl = !primaryShortcutUsesMeta,
        meta = primaryShortcutUsesMeta,
    )

    Window(
        onCloseRequest = { runGuarded(::exitApplication) },
        title = "Sillage",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
        val platform = remember(
            snapshotPath,
            repository,
            hostStrings,
            thirdPartyNotices,
            window,
            authenticationCredentialStore,
        ) {
            SillageNativePlatform(
                name = desktopPlatformName(),
                dataLocation = snapshotPath.toAbsolutePath().normalize().toString(),
                version = DesktopVersion,
                thirdPartyNotices = thirdPartyNotices,
                authenticationPersistsAcrossLaunches =
                    authenticationCredentialStore.persistsAcrossLaunches,
                openDataLocation = { openDataFolder(snapshotPath) },
                exportBackup = {
                    exportDesktopBackup(
                        owner = window,
                        repository = repository,
                        title = hostStrings.exportBackupDialogTitle,
                    )
                },
                restoreBackup = {
                    restoreDesktopBackup(
                        owner = window,
                        repository = repository,
                        title = hostStrings.restoreBackupDialogTitle,
                    )
                },
            )
        }

        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(820, 600)
        }

        MenuBar {
            Menu(hostStrings.fileMenu) {
                Item(
                    text = hostStrings.newRecord,
                    onClick = { runGuarded(controller::startNewRecord) },
                    shortcut = primaryShortcut(Key.N),
                    enabled = !controller.state.busy,
                )
                Item(
                    text = hostStrings.openDataLocation,
                    onClick = { platform.openDataLocation?.invoke() },
                    enabled = !controller.state.busy && platform.openDataLocation != null,
                )
                Separator()
                Item(
                    text = hostStrings.quit,
                    onClick = { runGuarded(::exitApplication) },
                    shortcut = primaryShortcut(Key.Q),
                )
            }
            Menu(hostStrings.navigateMenu) {
                Item(
                    text = hostStrings.records,
                    onClick = { runGuarded(controller::navigateToRecords) },
                )
                Item(
                    text = hostStrings.settings,
                    onClick = { runGuarded(controller::navigateToSettings) },
                )
            }
        }

        pendingHostAction?.let { action ->
            SillageNativeDiscardChangesDialog(
                languageMode = controller.state.appearance.languageMode,
                themeMode = controller.state.appearance.themeMode,
                onDismissRequest = { pendingHostAction = null },
                onDiscard = {
                    pendingHostAction = null
                    action()
                },
            )
        }

        SillageNativeApp(controller = controller, platform = platform)
    }
}

private fun showDesktopStartupError(error: Throwable?) {
    val chinese = Locale.getDefault().language.equals("zh", ignoreCase = true)
    val message = when {
        error == null && chinese -> "Sillage 已在运行。请先关闭另一个窗口后再试。"
        error == null -> "Sillage is already running. Close the other window and try again."
        chinese -> "Sillage 无法锁定本地数据目录，因此未启动。请检查目录权限后再试。"
        else -> "Sillage could not lock its local data folder, so it did not start. Check folder permissions and try again."
    }
    if (GraphicsEnvironment.isHeadless()) {
        System.err.println(message)
    } else {
        JOptionPane.showMessageDialog(
            null,
            message,
            "Sillage",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}

private fun isMacOs(): Boolean =
    System.getProperty("os.name").contains("mac", ignoreCase = true)

private fun desktopPlatformName(): String {
    val osName = System.getProperty("os.name")
    return when {
        osName.contains("mac", ignoreCase = true) -> "macOS"
        osName.contains("win", ignoreCase = true) -> "Windows"
        else -> osName
    }
}

private fun openDataFolder(snapshotPath: Path): Boolean {
    return try {
        if (!Desktop.isDesktopSupported()) return false
        val directory = snapshotPath.toAbsolutePath().normalize().parent
        java.nio.file.Files.createDirectories(directory)
        Desktop.getDesktop().open(directory.toFile())
        true
    } catch (_: Exception) {
        false
    }
}

private fun exportDesktopBackup(
    owner: Frame,
    repository: LocalClientRepository,
    title: String,
): Boolean {
    val selected = selectBackupPath(
        owner = owner,
        title = title,
        mode = FileDialog.SAVE,
        defaultFile = "sillage-backup-${LocalDate.now()}.json",
    ) ?: return false
    val destination = selected.ensureJsonExtension()
    DesktopClientSnapshotStorage(destination).write(repository.exportBackup())
    return true
}

private fun restoreDesktopBackup(
    owner: Frame,
    repository: LocalClientRepository,
    title: String,
): Boolean {
    val source = selectBackupPath(
        owner = owner,
        title = title,
        mode = FileDialog.LOAD,
    ) ?: return false
    repository.restoreBackup(Files.readString(source, StandardCharsets.UTF_8))
    return true
}

private fun selectBackupPath(
    owner: Frame,
    title: String,
    mode: Int,
    defaultFile: String? = null,
): Path? {
    val dialog = FileDialog(owner, title, mode).apply {
        file = defaultFile
        filenameFilter = FilenameFilter { _, name -> name.endsWith(".json", ignoreCase = true) }
        isVisible = true
    }
    val directory = dialog.directory ?: return null
    val file = dialog.file ?: return null
    return Path.of(directory, file)
}

internal fun Path.ensureJsonExtension(): Path =
    if (fileName.toString().endsWith(".json", ignoreCase = true)) {
        this
    } else {
        resolveSibling("${fileName}.json")
    }
