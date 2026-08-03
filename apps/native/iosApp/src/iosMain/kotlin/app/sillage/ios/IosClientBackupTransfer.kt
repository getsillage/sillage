@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package app.sillage.ios

import app.sillage.core.localdata.LocalClientRepository
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.native.ref.WeakReference
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

internal class IosViewControllerReference {
    private var reference: WeakReference<UIViewController>? = null

    fun attach(viewController: UIViewController) {
        reference = WeakReference(viewController)
    }

    fun get(): UIViewController? = reference?.get()
}

internal class IosClientBackupTransfer(
    private val repository: LocalClientRepository,
    private val presenter: () -> UIViewController?,
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
    private val temporaryDirectory: String = NSTemporaryDirectory(),
) {
    private var activeDelegate: IosDocumentPickerDelegate? = null

    suspend fun exportBackup(): Boolean {
        val exportUrl = NSURL.fileURLWithPath(
            "${temporaryDirectory.trimEnd('/')}/sillage-backup-${currentLocalDate()}.json",
        )
        check(
            NSString.create(string = repository.exportBackup()).writeToURL(
                url = exportUrl,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            ),
        ) { "The iOS backup file could not be prepared." }

        return try {
            presentDocumentPicker(
                UIDocumentPickerViewController(
                    forExportingURLs = listOf(exportUrl),
                    asCopy = true,
                ),
            )?.isNotEmpty() == true
        } finally {
            fileManager.removeItemAtURL(exportUrl, error = null)
        }
    }

    suspend fun restoreBackup(): Boolean {
        val selectedUrl = presentDocumentPicker(
            UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeJSON),
                asCopy = true,
            ).apply {
                allowsMultipleSelection = false
                shouldShowFileExtensions = true
            },
        )?.firstOrNull() ?: return false

        return try {
            val rawBackup = NSString.stringWithContentsOfURL(
                url = selectedUrl,
                encoding = NSUTF8StringEncoding,
                error = null,
            ) ?: error("The selected iOS backup could not be decoded as UTF-8.")
            repository.restoreBackup(rawBackup)
            true
        } finally {
            fileManager.removeItemAtURL(selectedUrl, error = null)
        }
    }

    private suspend fun presentDocumentPicker(
        picker: UIDocumentPickerViewController,
    ): List<NSURL>? {
        check(activeDelegate == null) { "An iOS document picker is already active." }
        val presenter = presenter() ?: return null

        return suspendCoroutine { continuation ->
            val delegate = IosDocumentPickerDelegate { urls ->
                activeDelegate = null
                continuation.resume(urls)
            }
            activeDelegate = delegate
            picker.delegate = delegate
            presenter.presentViewController(
                viewControllerToPresent = picker,
                animated = true,
                completion = null,
            )
        }
    }
}

private class IosDocumentPickerDelegate(
    private val onFinish: (List<NSURL>?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var completed = false

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        finish(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        finish(null)
    }

    private fun finish(urls: List<NSURL>?) {
        if (completed) return
        completed = true
        onFinish(urls)
    }
}
