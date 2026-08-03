@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package app.sillage.ios

import app.sillage.core.localdata.ClientRuntimeValues
import app.sillage.core.localdata.ClientSnapshotMigrationSource
import app.sillage.core.localdata.ClientSnapshotStorage
import app.sillage.core.localdata.MigratingClientSnapshotStorage
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private const val SnapshotKey = "app.sillage.client.snapshot.v1"

internal class IosClientSnapshotStorage(
    snapshotPath: String = defaultIosSnapshotPath(),
    defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ClientSnapshotStorage by MigratingClientSnapshotStorage(
    primary = IosFileSnapshotStorage(snapshotPath),
    legacy = IosUserDefaultsSnapshotMigrationSource(defaults),
)

private class IosFileSnapshotStorage(
    private val snapshotPath: String,
    private val fileManager: NSFileManager = NSFileManager.defaultManager,
) : ClientSnapshotStorage {
    override val location: String = snapshotPath

    override fun read(): String? {
        if (!fileManager.fileExistsAtPath(snapshotPath)) return null
        return NSString.stringWithContentsOfFile(
            path = snapshotPath,
            encoding = NSUTF8StringEncoding,
            error = null,
        ) ?: error("The iOS local snapshot could not be decoded as UTF-8.")
    }

    override fun write(value: String) {
        val directory = snapshotPath.substringBeforeLast('/')
        check(
            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
        ) { "The iOS local data directory could not be created." }
        check(
            NSString.create(string = value).writeToFile(
                path = snapshotPath,
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            ),
        ) { "The iOS local snapshot could not be written atomically." }
    }
}

private class IosUserDefaultsSnapshotMigrationSource(
    private val defaults: NSUserDefaults,
) : ClientSnapshotMigrationSource {
    override fun read(): String? = defaults.stringForKey(SnapshotKey)

    override fun clear() {
        defaults.removeObjectForKey(SnapshotKey)
    }
}

internal fun defaultIosSnapshotPath(homeDirectory: String = NSHomeDirectory()): String =
    "$homeDirectory/Library/Application Support/Sillage/client-v1.json"

internal class IosRuntimeValues : ClientRuntimeValues {
    override fun nextRecordId(): String = NSUUID().UUIDString()

    override fun nextMutationId(): String = NSUUID().UUIDString()

    override fun currentTimestamp(): String =
        NSISO8601DateFormatter().stringFromDate(NSDate())
}

internal fun currentLocalDate(): String = NSDateFormatter().run {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
    stringFromDate(NSDate())
}
