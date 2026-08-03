package app.sillage.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.sillage.core.localdata.LocalClientRepository
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativePlatform
import platform.UIKit.UIViewController

private const val IosVersion = "0.1.0"

fun MainViewController(): UIViewController = ComposeUIViewController {
    val storage = remember { IosClientSnapshotStorage() }
    val repository = remember(storage) {
        LocalClientRepository(
            storage = storage,
            runtimeValues = IosRuntimeValues(),
        )
    }
    val controller = remember(repository) {
        SillageNativeController(
            recordsRepository = repository,
            recordWriteRepository = repository,
            recordLifecycleRepository = repository,
            preferencesRepository = repository,
            todayProvider = ::currentLocalDate,
        )
    }
    val platform = remember(storage) {
        SillageNativePlatform(
            name = "iOS",
            dataLocation = storage.location,
            version = IosVersion,
        )
    }

    SillageNativeApp(
        controller = controller,
        platform = platform,
    )
}
