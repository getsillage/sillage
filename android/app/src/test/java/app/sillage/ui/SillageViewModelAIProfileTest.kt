package app.sillage.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sillage.data.AIProfileDraft
import app.sillage.data.SessionStore
import app.sillage.data.SillageExportCodec
import app.sillage.data.SillageExportData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SillageViewModelAIProfileTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun defaultSelectionStaysInTheDraftUntilExplicitSave() {
        val original = listOf(
            aiProfile(id = "profile-1", name = "原默认", active = true),
            aiProfile(id = "profile-2", name = "新默认", active = false),
        )
        val storedJson = prepareOfflineProfiles(original)
        val viewModel = SillageViewModel(context)
        val requestId = viewModel.state.value.aiSettingsRequestId

        viewModel.setAIProfileDefault(1)

        assertEquals(listOf(false, true), viewModel.state.value.aiProfiles.map { it.active })
        assertEquals(requestId, viewModel.state.value.aiSettingsRequestId)
        assertFalse(viewModel.state.value.aiSettingsSaving)
        assertNull(viewModel.state.value.notice)
        assertStoredProfilesAreUnchanged(storedJson)
    }

    @Test
    fun deletionStaysInTheDraftAndChoosesANewDefaultUntilExplicitSave() {
        val original = listOf(
            aiProfile(id = "profile-1", name = "原默认", active = true),
            aiProfile(id = "profile-2", name = "保留档案", active = false),
        )
        val storedJson = prepareOfflineProfiles(original)
        val viewModel = SillageViewModel(context)
        val requestId = viewModel.state.value.aiSettingsRequestId

        assertTrue(viewModel.removeAIProfile(0))

        assertEquals(listOf("profile-2"), viewModel.state.value.aiProfiles.map { it.id })
        assertTrue(viewModel.state.value.aiProfiles.single().active)
        assertEquals(requestId, viewModel.state.value.aiSettingsRequestId)
        assertFalse(viewModel.state.value.aiSettingsSaving)
        assertNull(viewModel.state.value.notice)
        assertStoredProfilesAreUnchanged(storedJson)
    }

    @Test
    fun deletingAnEarlierNewDraftKeepsTheLaterDraftIdentityAndResult() {
        prepareOfflineProfiles(emptyList())
        val viewModel = SillageViewModel(context)
        viewModel.addAIProfile()
        viewModel.addAIProfile()
        val drafts = viewModel.state.value.aiProfiles
        val laterDraftKey = drafts[1].uiKey(1)
        assertTrue(drafts[0].draftKey.isNotBlank())
        assertTrue(drafts[1].draftKey.isNotBlank())
        assertFalse(drafts[0].draftKey == drafts[1].draftKey)

        viewModel.loadAIModels(1)
        val result = requireNotNull(viewModel.state.value.aiTestResults[laterDraftKey])
        assertTrue(viewModel.removeAIProfile(0))

        val remainingKey = viewModel.state.value.aiProfiles.single().uiKey(0)
        assertEquals(laterDraftKey, remainingKey)
        assertEquals(result, viewModel.state.value.aiTestResults[remainingKey])
    }

    private fun prepareOfflineProfiles(profiles: List<AIProfileDraft>): String {
        SessionStore(context).saveAppMode(SessionStore.MODE_OFFLINE)
        val storedJson = SillageExportCodec.toLocalJson(
            SillageExportData(
                formatVersion = SillageExportCodec.FORMAT_VERSION,
                exportedAt = "2026-07-14T00:00:00Z",
                themeMode = "",
                memoViewMode = "",
                autoSummary = false,
                memos = emptyList(),
                memoAI = emptyList(),
                aiProfiles = profiles,
                askConversations = emptyList(),
                askMessages = emptyList(),
            ),
        )
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
            .edit()
            .putString("data", storedJson)
            .commit()
        return storedJson
    }

    private fun assertStoredProfilesAreUnchanged(storedJson: String) {
        val preferences = context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE)
        assertEquals(storedJson, preferences.getString("data", null))
        assertFalse(preferences.contains("secure.data"))
    }

    private fun aiProfile(
        id: String,
        name: String,
        active: Boolean,
    ): AIProfileDraft {
        return AIProfileDraft(
            id = id,
            name = name,
            enabled = true,
            active = active,
        )
    }
}
