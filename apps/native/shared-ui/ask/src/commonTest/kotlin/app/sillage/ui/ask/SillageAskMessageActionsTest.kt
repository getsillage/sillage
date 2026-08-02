package app.sillage.ui.ask

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SillageAskMessageActionsTest {
    @Test
    fun emptyAnswerWithoutVariantsOrRegenerationHasNoActions() {
        val presentation = presentation(content = "   ")

        assertFalse(presentation.visible)
        assertFalse(presentation.showVariants)
        assertFalse(presentation.showRegenerate)
        assertFalse(presentation.showSave)
    }

    @Test
    fun selectedVariantExposesNeighborIdsAndPosition() {
        val presentation = presentation(
            content = "Answer",
            variantIds = listOf("first", "second", "third"),
            selectedIndex = 1,
        )

        assertTrue(presentation.visible)
        assertTrue(presentation.showVariants)
        assertEquals(2, presentation.position)
        assertEquals(3, presentation.totalVariants)
        assertEquals("first", presentation.previousVariantId)
        assertTrue(presentation.previousVariantEnabled)
        assertEquals("third", presentation.nextVariantId)
        assertTrue(presentation.nextVariantEnabled)
    }

    @Test
    fun variantChangeDisablesBothNeighborsWithoutLosingTargets() {
        val presentation = presentation(
            content = "Answer",
            variantIds = listOf("first", "second", "third"),
            selectedIndex = 1,
            variantChanging = true,
        )

        assertEquals("first", presentation.previousVariantId)
        assertFalse(presentation.previousVariantEnabled)
        assertEquals("third", presentation.nextVariantId)
        assertFalse(presentation.nextVariantEnabled)
    }

    @Test
    fun unknownSelectionDoesNotInventVariantTargets() {
        val presentation = presentation(
            content = "Answer",
            variantIds = listOf("first", "second"),
            selectedIndex = -1,
        )

        assertNull(presentation.previousVariantId)
        assertFalse(presentation.previousVariantEnabled)
        assertNull(presentation.nextVariantId)
        assertFalse(presentation.nextVariantEnabled)
    }

    @Test
    fun regenerationAndSaveFollowRequestGates() {
        val presentation = presentation(
            content = "Answer",
            canRegenerate = true,
            regenerating = true,
            savingDisabled = false,
        )

        assertTrue(presentation.showRegenerate)
        assertFalse(presentation.regenerateEnabled)
        assertTrue(presentation.showSave)
        assertFalse(presentation.saveEnabled)
    }

    @Test
    fun answerPositionUsesAPoliteLiveRegion() {
        val semantics = SemanticsConfiguration()

        semantics.applySillageAskVariantSemantics("Answer 2 of 3")

        assertEquals(
            listOf("Answer 2 of 3"),
            semantics[SemanticsProperties.ContentDescription],
        )
        assertEquals(
            LiveRegionMode.Polite,
            semantics[SemanticsProperties.LiveRegion],
        )
    }

    private fun presentation(
        content: String,
        variantIds: List<String> = listOf("only"),
        selectedIndex: Int = 0,
        canRegenerate: Boolean = false,
        regenerating: Boolean = false,
        variantChanging: Boolean = false,
        savingDisabled: Boolean = false,
    ): SillageAskMessageActionsPresentation = sillageAskMessageActionsPresentation(
        content = content,
        variantIds = variantIds,
        selectedIndex = selectedIndex,
        canRegenerate = canRegenerate,
        regenerating = regenerating,
        variantChanging = variantChanging,
        savingDisabled = savingDisabled,
    )
}
