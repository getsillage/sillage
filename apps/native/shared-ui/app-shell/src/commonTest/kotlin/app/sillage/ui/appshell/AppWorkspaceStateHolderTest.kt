package app.sillage.ui.appshell

import app.sillage.core.domain.records.Memo
import app.sillage.features.ask.AskFeatureStateHolder
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.features.settings.AIProfileDraft
import app.sillage.features.settings.SettingsFeatureStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class AppWorkspaceStateHolderTest {
    @Test
    fun featureUpdatesReplaceOnlyTheirOwnedState() {
        val initial = AppWorkspaceStateHolder()

        val records = initial.updateRecords { it.replaceVisibleRecords(listOf(memo("records"))) }
        val settings = records.updateSettings { it.replaceProfiles(listOf(profile("settings"))) }
        val ask = settings.updateAsk { it.updateQuestion("ask") }

        assertEquals(listOf("records"), ask.records.records.map(Memo::id))
        assertEquals(listOf("settings"), ask.settings.profiles.map(AIProfileDraft::name))
        assertEquals("ask", ask.ask.question)
        assertSame(initial.settings, records.settings)
        assertSame(records.ask, settings.ask)
        assertSame(settings.records, ask.records)
    }

    @Test
    fun clientWorkspaceClearIsAtomicAcrossFeatures() {
        val initial = AppWorkspaceStateHolder(
            records = RecordsFeatureStateHolder().replaceVisibleRecords(listOf(memo("online"))),
            settings = SettingsFeatureStateHolder().replaceProfiles(listOf(profile("online"))),
            ask = AskFeatureStateHolder().updateQuestion("pending question"),
        )
        val originalSessionGeneration = initial.ask.session.generation
        val offlineProfile = profile("offline")

        val cleared = initial.clearClientWorkspace(
            settingsProfiles = listOf(offlineProfile),
            settingsAutoSummaryEnabled = true,
            askInvalidateStream = true,
            askInvalidateVariant = true,
        )

        assertEquals(emptyList(), cleared.records.records)
        assertEquals(listOf(offlineProfile), cleared.settings.profiles)
        assertEquals(true, cleared.settings.autoSummaryEnabled)
        assertEquals("", cleared.ask.question)
        assertNotEquals(originalSessionGeneration, cleared.ask.session.generation)
    }

    @Test
    fun offlineEntryClearsThenSeedsTheLocalSnapshot() {
        val localMemo = memo("offline")
        val localProfile = profile("local")
        val initial = AppWorkspaceStateHolder(
            records = RecordsFeatureStateHolder().replaceVisibleRecords(listOf(memo("online"))),
            ask = AskFeatureStateHolder().updateQuestion("pending question"),
        )

        val offline = initial.enterOfflineClientWorkspace(
            memos = listOf(localMemo),
            settingsProfiles = listOf(localProfile),
            settingsAutoSummaryEnabled = true,
        )

        assertEquals(listOf(localMemo), offline.records.records)
        assertEquals(listOf(localProfile), offline.settings.profiles)
        assertEquals(true, offline.settings.autoSummaryEnabled)
        assertEquals("", offline.ask.question)
    }

    private fun memo(id: String): Memo = Memo(
        id = id,
        content = id,
        entryDate = "2026-08-02",
        version = 1,
        createdAt = "2026-08-02T00:00:00Z",
        updatedAt = "2026-08-02T00:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = null,
    )

    private fun profile(name: String): AIProfileDraft = AIProfileDraft(name = name)
}
