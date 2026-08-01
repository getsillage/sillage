package app.sillage.features.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AIProfileDiagnosticsStateHolderTest {
    private val context = AIProfileDiagnosticsContext(
        appMode = "online",
        clientContextGeneration = 5,
        anotherOperationInProgress = false,
    )
    private val profile = AIProfileDraft(id = "profile-1", name = "Primary")

    @Test
    fun testRequestIsSingleFlightAndProfileSnapshotBound() {
        val idle = AIProfileDiagnosticsStateHolder()
        val request = requireNotNull(idle.nextTestRequest(profile, 0, context))
        val testing = requireNotNull(idle.beginTest(request, listOf(profile), context))

        assertTrue(testing.busy)
        assertNull(testing.nextModelsRequest(profile, 0, context))
        assertTrue(testing.canApplyTest(request, listOf(profile), context))
        assertFalse(
            testing.canApplyTest(
                request,
                listOf(profile.copy(model = "edited")),
                context,
            ),
        )

        val completed = requireNotNull(
            testing.completeTest(request, "Connected", listOf(profile), context),
        )
        assertFalse(completed.busy)
        assertEquals("Connected", completed.testResults[profile.id])
    }

    @Test
    fun modelRequestRejectsRemovalModeChangeAndLateCompletion() {
        val idle = AIProfileDiagnosticsStateHolder()
        val request = requireNotNull(idle.nextModelsRequest(profile, 0, context))
        val loading = requireNotNull(idle.beginModels(request, listOf(profile), context))

        assertFalse(loading.canApplyModels(request, emptyList(), context))
        assertFalse(loading.canApplyModels(request, listOf(profile), context.copy(appMode = "offline")))

        val completed = requireNotNull(
            loading.completeModels(
                request,
                models = listOf("model-a"),
                message = "Loaded",
                profiles = listOf(profile),
                context = context,
            ),
        )
        assertEquals(listOf("model-a"), completed.modelResults[profile.id])
        assertEquals("Loaded", completed.testResults[profile.id])
        assertNull(completed.completeModels(request, emptyList(), "Late", listOf(profile), context))
    }

    @Test
    fun unsavedEditorIdentitySurvivesListReindexing() {
        val draft = AIProfileDraft(draftKey = "draft-1", name = "Draft")
        val another = AIProfileDraft(draftKey = "draft-2", name = "Another")
        val idle = AIProfileDiagnosticsStateHolder()
        val request = requireNotNull(idle.nextTestRequest(draft, 1, context))
        val testing = requireNotNull(idle.beginTest(request, listOf(another, draft), context))

        assertTrue(testing.canApplyTest(request, listOf(draft), context))
    }

    @Test
    fun resetInvalidatesRequestsAndClearsResults() {
        val seeded = AIProfileDiagnosticsStateHolder(
            testingProfileKey = profile.id,
            testResults = mapOf(profile.id to "Old"),
            modelResults = mapOf(profile.id to listOf("old-model")),
            testRequestId = 3,
            modelsRequestId = 4,
        )

        val reset = seeded.reset()

        assertFalse(reset.busy)
        assertTrue(reset.testResults.isEmpty())
        assertTrue(reset.modelResults.isEmpty())
        assertEquals(4, reset.testRequestId)
        assertEquals(5, reset.modelsRequestId)
    }
}
