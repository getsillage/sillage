package app.sillage.desktop

private const val ThirdPartyNoticesResource = "third_party_notices.txt"

private object DesktopThirdPartyNoticesResourceOwner

internal fun loadDesktopThirdPartyNotices(
    classLoader: ClassLoader = DesktopThirdPartyNoticesResourceOwner::class.java.classLoader,
): String {
    val notices = checkNotNull(classLoader.getResourceAsStream(ThirdPartyNoticesResource)) {
        "The desktop third-party notices resource is missing."
    }.bufferedReader().use { it.readText() }
    check(notices.isNotBlank()) { "The desktop third-party notices resource is empty." }
    return notices
}
