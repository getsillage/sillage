package app.sillage.ios

import app.sillage.core.localdata.ClientRuntimeValues
import app.sillage.core.localdata.ClientSnapshotStorage
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSISO8601DateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

private const val SnapshotKey = "app.sillage.client.snapshot.v1"

internal class IosClientSnapshotStorage(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : ClientSnapshotStorage {
    override val location: String = "NSUserDefaults"

    override fun read(): String? = defaults.stringForKey(SnapshotKey)

    override fun write(value: String) {
        defaults.setObject(value, forKey = SnapshotKey)
    }
}

internal class IosRuntimeValues : ClientRuntimeValues {
    override fun nextRecordId(): String = NSUUID().UUIDString()

    override fun currentTimestamp(): String =
        NSISO8601DateFormatter().stringFromDate(NSDate())
}

internal fun currentLocalDate(): String = NSDateFormatter().run {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
    stringFromDate(NSDate())
}
