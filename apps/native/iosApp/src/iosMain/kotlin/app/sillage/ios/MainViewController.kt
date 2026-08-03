package app.sillage.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.sillage.core.localdata.LocalClientRepository
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativePlatform
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

private const val FallbackIosVersion = "0.3.1"

fun MainViewController(): UIViewController {
    val viewControllerReference = IosViewControllerReference()
    val viewController = ComposeUIViewController {
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
        val backupTransfer = remember(repository) {
            IosClientBackupTransfer(
                repository = repository,
                presenter = viewControllerReference::get,
            )
        }
        val platform = remember(storage, backupTransfer) {
            SillageNativePlatform(
                name = "iOS",
                dataLocation = storage.location,
                version = currentIosVersion(),
                exportBackup = backupTransfer::exportBackup,
                restoreBackup = backupTransfer::restoreBackup,
            )
        }

        SillageNativeApp(
            controller = controller,
            platform = platform,
        )
    }
    viewControllerReference.attach(viewController)
    return viewController
}

private fun currentIosVersion(): String {
    val version = NSBundle.mainBundle
        .objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    return version?.takeIf { it.isNotBlank() } ?: FallbackIosVersion
}
