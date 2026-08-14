package app.sillage.ios

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.sillage.core.localdata.LocalClientRepository
import app.sillage.core.network.RemoteInstanceAuthenticationRepositoryFactory
import app.sillage.core.network.RemoteInstanceBootstrapRepository
import app.sillage.ui.application.SillageNativeApp
import app.sillage.ui.application.SillageNativeController
import app.sillage.ui.application.SillageNativePlatform
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

internal const val FallbackIosVersion = "0.4.0"

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
        val httpTransport = remember { IosSillageHttpTransport() }
        val networkMonitor = remember { IosNetworkMonitor() }
        DisposableEffect(networkMonitor) {
            onDispose(networkMonitor::close)
        }
        val bootstrapRepository = remember(httpTransport) {
            RemoteInstanceBootstrapRepository(httpTransport)
        }
        val authenticationCredentialStore = remember { IosAuthenticationCredentialStore() }
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
                todayProvider = ::currentLocalDate,
                memoSyncWorkspaceFactory = repository,
                memoSyncGatewayFactory = authenticationRepositoryFactory,
                askClientFactory = authenticationRepositoryFactory,
            )
        }
        val backupTransfer = remember(repository) {
            IosClientBackupTransfer(
                repository = repository,
                presenter = viewControllerReference::get,
            )
        }
        val thirdPartyNotices = remember { loadIosThirdPartyNotices() }
        val platform = remember(
            storage,
            backupTransfer,
            authenticationCredentialStore,
            thirdPartyNotices,
            networkMonitor,
        ) {
            SillageNativePlatform(
                name = "iOS",
                dataLocation = storage.location,
                version = currentIosVersion(),
                thirdPartyNotices = thirdPartyNotices,
                authenticationPersistsAcrossLaunches =
                    authenticationCredentialStore.persistsAcrossLaunches,
                networkStatus = networkMonitor.status,
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
