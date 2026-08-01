package app.sillage.ui.settings

import app.sillage.features.settings.AIProfilesMutationStateHolder
import app.sillage.features.settings.SettingsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SillageAIProfilesHeaderCardTest {
    private val strings = SillageAIProfilesHeaderStrings(
        title = "AI profiles",
        supporting = "Configure providers used by Sillage.",
        newProfile = "New",
        saving = "Saving",
        save = "Save",
    )

    @Test
    fun presentationReadsSaveStateFromFeatureAggregate() {
        val idle = sillageAIProfilesHeaderPresentation(
            state = SettingsFeatureStateHolder(),
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )
        val saving = sillageAIProfilesHeaderPresentation(
            state = SettingsFeatureStateHolder(
                profilesMutation = AIProfilesMutationStateHolder(saving = true),
            ),
            strings = strings,
            editingBlocked = false,
            mutationBlocked = false,
        )

        assertEquals("AI profiles", idle.title)
        assertEquals("Configure providers used by Sillage.", idle.supporting)
        assertEquals("New", idle.newProfileAction)
        assertEquals("Save", idle.saveAction)
        assertFalse(idle.saving)
        assertTrue(idle.addEnabled)
        assertTrue(idle.saveEnabled)
        assertEquals("Saving", saving.saveAction)
        assertTrue(saving.saving)
        assertFalse(saving.addEnabled)
        assertFalse(saving.saveEnabled)
    }

    @Test
    fun presentationAppliesHostOperationGates() {
        val blocked = sillageAIProfilesHeaderPresentation(
            state = SettingsFeatureStateHolder(),
            strings = strings,
            editingBlocked = true,
            mutationBlocked = true,
        )

        assertFalse(blocked.addEnabled)
        assertFalse(blocked.saveEnabled)
    }
}
