package app.sillage.desktop

import kotlin.test.Test
import kotlin.test.assertContains

class DesktopThirdPartyNoticesTest {
    @Test
    fun packagedNoticesContainRuntimeInventoryAndLicense() {
        val notices = loadDesktopThirdPartyNotices()

        assertContains(notices, "Sillage Desktop - Open-source software notices")
        assertContains(notices, "apps/native/desktopApp/gradle.lockfile")
        assertContains(notices, "net.java.dev.jna:jna:5.19.1")
        assertContains(notices, "Apache License 2.0")
    }
}
