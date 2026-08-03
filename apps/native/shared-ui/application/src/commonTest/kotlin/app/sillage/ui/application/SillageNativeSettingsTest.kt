package app.sillage.ui.application

import app.sillage.core.application.preferences.ClientPreferenceValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageNativeSettingsTest {
    @Test
    fun presentationMapsSharedSectionsAndPreservesMetadataOrder() {
        val strings = sillageNativeStrings(ClientPreferenceValues.LANGUAGE_EN)
        val presentation = sillageNativeSettingsPresentation(
            strings = strings,
            platform = SillageNativePlatform(
                name = "macOS",
                dataLocation = "/tmp/sillage.json",
                version = "1.2.3",
            ),
        )

        assertEquals("Appearance", presentation.appearanceStrings.sectionTitle)
        assertEquals("Choose the interface language", presentation.appearanceStrings.language.supporting)
        assertEquals(
            listOf(ClientPreferenceValues.LANGUAGE_ZH_CN, ClientPreferenceValues.LANGUAGE_EN),
            presentation.languageOptions.map { it.value },
        )
        assertEquals("Data", presentation.dataStrings.sectionTitle)
        assertEquals("Restore backup", presentation.dataStrings.importTitle)
        assertEquals("About", presentation.aboutStrings.sectionTitle)
        assertNull(presentation.aboutStrings.licensesTitle)
        assertEquals(
            listOf("Mode", "Platform", "Version"),
            presentation.aboutValues.map { it.label },
        )
        assertEquals(
            listOf(strings.offlineModeValue, "macOS", "1.2.3"),
            presentation.aboutValues.map { it.value },
        )
    }

    @Test
    fun presentationOffersPackagedLicenseNotices() {
        val strings = sillageNativeStrings(ClientPreferenceValues.LANGUAGE_EN)
        val presentation = sillageNativeSettingsPresentation(
            strings = strings,
            platform = SillageNativePlatform(
                name = "Windows",
                dataLocation = "client-v1.json",
                version = "1.2.3",
                thirdPartyNotices = "Package inventory",
            ),
        )

        assertEquals("Open-source licenses", presentation.aboutStrings.licensesTitle)
        assertEquals(
            "Review licenses and notices included with this app",
            presentation.aboutStrings.licensesSupporting,
        )
    }

    @Test
    fun authenticationCopyDisclosesInitialPushThenPull() {
        val english = sillageNativeStrings(ClientPreferenceValues.LANGUAGE_EN)
        val chinese = sillageNativeStrings(ClientPreferenceValues.LANGUAGE_ZH_CN)

        assertEquals(
            "Create the only account. Local changes are then pushed to this server before its current records are pulled.",
            english.initializeAccountSupporting,
        )
        assertEquals(
            "Signing in pushes local changes to this server before pulling its current records.",
            english.signInSupporting,
        )
        assertEquals("Local-first · foreground + manual sync", english.offlineModeValue)
        assertEquals(
            "创建此实例的唯一账号。随后会先将本机更改推送到这台服务器，再拉取服务器上的当前记录。",
            chinese.initializeAccountSupporting,
        )
        assertEquals(
            "登录会先将本机更改推送到这台服务器，再拉取服务器上的当前记录。",
            chinese.signInSupporting,
        )
        assertEquals("本地优先 · 前台 + 手动同步", chinese.offlineModeValue)
    }
}
