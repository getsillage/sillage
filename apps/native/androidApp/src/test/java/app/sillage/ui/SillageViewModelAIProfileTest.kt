package app.sillage.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.editorKey
import app.sillage.data.LocalDataStore
import app.sillage.data.LocalStateStorage
import app.sillage.data.SecureReadResult
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
    private lateinit var localDataStore: LocalDataStore

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE).edit().clear().commit()
        localDataStore = LocalDataStore(InMemoryLocalStateStorage())
    }

    @Test
    fun defaultSelectionStaysInTheDraftUntilExplicitSave() {
        val original = listOf(
            aiProfile(id = "profile-1", name = "原默认", active = true),
            aiProfile(id = "profile-2", name = "新默认", active = false),
        )
        val storedJson = prepareOfflineProfiles(original)
        val viewModel = SillageViewModel(context, localDataStore = localDataStore)
        val requestId = viewModel.state.value.settings.profilesRequestId

        viewModel.setAIProfileDefault(1)

        assertEquals(listOf(false, true), viewModel.state.value.settings.profiles.map { it.active })
        assertEquals(requestId, viewModel.state.value.settings.profilesRequestId)
        assertFalse(viewModel.state.value.settings.profilesSaving)
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
        val viewModel = SillageViewModel(context, localDataStore = localDataStore)
        val requestId = viewModel.state.value.settings.profilesRequestId

        assertTrue(viewModel.removeAIProfile(0))

        assertEquals(listOf("profile-2"), viewModel.state.value.settings.profiles.map { it.id })
        assertTrue(viewModel.state.value.settings.profiles.single().active)
        assertEquals(requestId, viewModel.state.value.settings.profilesRequestId)
        assertFalse(viewModel.state.value.settings.profilesSaving)
        assertNull(viewModel.state.value.notice)
        assertStoredProfilesAreUnchanged(storedJson)
    }

    @Test
    fun deletingAnEarlierNewDraftKeepsTheLaterDraftIdentityAndResult() {
        prepareOfflineProfiles(emptyList())
        val viewModel = SillageViewModel(context, localDataStore = localDataStore)
        viewModel.addAIProfile()
        viewModel.addAIProfile()
        val drafts = viewModel.state.value.settings.profiles
        val laterDraftKey = drafts[1].editorKey(1)
        assertTrue(drafts[0].draftKey.isNotBlank())
        assertTrue(drafts[1].draftKey.isNotBlank())
        assertFalse(drafts[0].draftKey == drafts[1].draftKey)

        viewModel.loadAIModels(1)
        val result = requireNotNull(viewModel.state.value.settings.testResults[laterDraftKey])
        assertTrue(viewModel.removeAIProfile(0))

        val remainingKey = viewModel.state.value.settings.profiles.single().editorKey(0)
        assertEquals(laterDraftKey, remainingKey)
        assertEquals(result, viewModel.state.value.settings.testResults[remainingKey])
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
        localDataStore.mergeWith(SillageExportCodec.fromJson(storedJson))
        return storedJson
    }

    private fun assertStoredProfilesAreUnchanged(storedJson: String) {
        assertEquals(storedJson, SillageExportCodec.toLocalJson(localDataStore.exportData()))
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

    private class InMemoryLocalStateStorage : LocalStateStorage {
        private val values = mutableMapOf<String, String>()

        override fun readString(key: String): SecureReadResult =
            values[key]?.let(SecureReadResult::Value) ?: SecureReadResult.Missing

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun putStrings(values: Map<String, String>) {
            this.values.putAll(values)
        }
    }
}
