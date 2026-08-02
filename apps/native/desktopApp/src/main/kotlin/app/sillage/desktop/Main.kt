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
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativeDiscardChangesDialog
import app.sillage.ui.application.SillageNativePlatform
import app.sillage.ui.application.sillageNativeHostStrings
import java.awt.Desktop
import java.awt.Dimension
import java.nio.file.Path
import java.time.LocalDate

private const val DesktopVersion = "0.1.0"

fun main() = application {
    val snapshotPath = remember { DesktopDataPaths.defaultSnapshotPath() }
    val storage = remember(snapshotPath) { DesktopClientSnapshotStorage(snapshotPath) }
    val repository = remember(storage) {
        LocalClientRepository(storage, DesktopRuntimeValues())
    }
    val controller = remember(repository) {
        SillageNativeController(
            recordsRepository = repository,
            recordWriteRepository = repository,
            recordLifecycleRepository = repository,
            preferencesRepository = repository,
            todayProvider = { LocalDate.now().toString() },
        )
    }
    val platform = remember(snapshotPath) {
        SillageNativePlatform(
            name = desktopPlatformName(),
            dataLocation = snapshotPath.toAbsolutePath().normalize().toString(),
            version = DesktopVersion,
            openDataLocation = { openDataFolder(snapshotPath) },
        )
    }
    val primaryShortcutUsesMeta = remember { isMacOs() }
    val hostStrings = sillageNativeHostStrings(controller.state.appearance.languageMode)
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
                    enabled = controller.state.storageAvailable &&
                        platform.openDataLocation != null,
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
