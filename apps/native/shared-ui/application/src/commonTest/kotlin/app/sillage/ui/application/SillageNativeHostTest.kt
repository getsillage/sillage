package app.sillage.ui.application

import app.sillage.core.application.preferences.ClientPreferenceValues
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNativeHostTest {
    @Test
    fun providesLocalizedDesktopMenuCopy() {
        val english = sillageNativeHostStrings(ClientPreferenceValues.LANGUAGE_EN)
        val chinese = sillageNativeHostStrings(ClientPreferenceValues.LANGUAGE_ZH_CN)

        assertEquals("File", english.fileMenu)
        assertEquals("New record", english.newRecord)
        assertEquals("Quit", english.quit)
        assertEquals("文件", chinese.fileMenu)
        assertEquals("新建记录", chinese.newRecord)
        assertEquals("退出", chinese.quit)
    }
}
