package app.sillage.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SillageAIProfilesEditorTest {
    @Test
    fun editorStateOwnsAndNormalizesProfileSelection() {
        val state = SillageAIProfilesEditorState()

        assertNull(state.selectedIndex(profileCount = 2))
        state.select(1)
        assertEquals(1, state.selectedIndex(profileCount = 2))
        assertNull(state.selectedIndex(profileCount = 1))
        state.clearSelection()
        assertNull(state.selectedIndex(profileCount = 2))
    }

    @Test
    fun newProfileSelectionTargetsCurrentProfileCount() {
        val state = SillageAIProfilesEditorState()

        state.select(3)

        assertNull(state.selectedIndex(profileCount = 3))
        assertEquals(3, state.selectedIndex(profileCount = 4))
    }
}
