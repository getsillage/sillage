package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageSettingsLanguageRowTest {
    private val strings = SillageSettingsLanguageStrings(
        title = "Language",
        supporting = "Choose the interface language.",
    )
    private val options = listOf(
        SillageSettingsLanguageOption(value = "zh-CN", label = "中文"),
        SillageSettingsLanguageOption(value = "en", label = "English"),
    )

    @Test
    fun presentationSelectsMatchingHostLanguageValue() {
        val presentation = sillageSettingsLanguagePresentation(
            selectedLanguage = "en",
            options = options,
            strings = strings,
            enabled = true,
        )

        assertEquals("Language", presentation.title)
        assertEquals("Choose the interface language.", presentation.supporting)
        assertEquals(listOf("zh-CN", "en"), presentation.options.map { it.value })
        assertFalse(presentation.options[0].selected)
        assertTrue(presentation.options[1].selected)
        assertTrue(presentation.enabled)
    }

    @Test
    fun presentationPreservesDisabledHostGate() {
        val presentation = sillageSettingsLanguagePresentation(
            selectedLanguage = "zh-CN",
            options = options,
            strings = strings,
            enabled = false,
        )

        assertTrue(presentation.options[0].selected)
        assertFalse(presentation.enabled)
    }
}
