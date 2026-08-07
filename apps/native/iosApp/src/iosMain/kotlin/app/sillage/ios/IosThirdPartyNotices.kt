@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.sillage.ios

import platform.Foundation.NSBundle
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

private const val ThirdPartyNoticesResourceName = "ThirdPartyNotices"

internal fun loadIosThirdPartyNotices(bundle: NSBundle = NSBundle.mainBundle): String {
    val path = checkNotNull(
        bundle.pathForResource(ThirdPartyNoticesResourceName, ofType = "txt"),
    ) { "The iOS third-party notices resource is missing." }
    val notices = checkNotNull(
        NSString.stringWithContentsOfFile(
            path = path,
            encoding = NSUTF8StringEncoding,
            error = null,
        ),
    ) { "The iOS third-party notices resource is not valid UTF-8." }
    check(notices.isNotBlank()) { "The iOS third-party notices resource is empty." }
    return notices
}
