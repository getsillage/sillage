package app.sillage.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.sillage.core.localdata.LocalClientRepository
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativePlatform
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "Sillage",
        state = rememberWindowState(width = 1180.dp, height = 760.dp),
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(820, 600)
        }
        SillageNativeApp(controller = controller, platform = platform)
    }
}

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
