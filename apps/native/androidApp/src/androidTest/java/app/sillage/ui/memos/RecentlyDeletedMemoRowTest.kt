package app.sillage.ui.memos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sillage.core.domain.records.Memo
import app.sillage.features.records.RecordsFeatureStateHolder
import app.sillage.ui.records.RECENTLY_DELETED_PURGE_TEST_TAG
import app.sillage.ui.records.RECENTLY_DELETED_RESTORE_TEST_TAG
import app.sillage.ui.records.SillageRecentlyDeletedRecordRow
import app.sillage.ui.records.SillageRecentlyDeletedRecordStrings
import app.sillage.ui.theme.SillageTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecentlyDeletedMemoRowTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun exposesRestoreAndConfirmedPurgeActions() {
        var restored = false
        var purged = false
        compose.setContent {
            SillageTheme(darkTheme = false) {
                SillageRecentlyDeletedRecordRow(
                    state = RecordsFeatureStateHolder(),
                    memo = deletedMemo(),
                    strings = SillageRecentlyDeletedRecordStrings(
                        blankRecord = "Blank record",
                        deletedAtLabel = "Deleted just now",
                        purgeSupporting = "This cannot be undone",
                        restoreAction = "Restore",
                        deleteForeverAction = "Delete forever",
                        confirmDeleteAction = "Confirm delete",
                        cancelAction = "Cancel",
                    ),
                    restoreIcon = Icons.Rounded.RestoreFromTrash,
                    purgeIcon = Icons.Rounded.DeleteForever,
                    onRestore = { restored = true },
                    onPurge = { purged = true },
                )
            }
        }

        compose.onNodeWithTag(RECENTLY_DELETED_RESTORE_TEST_TAG).performClick()
        compose.runOnIdle { assertTrue(restored) }

        compose.onNodeWithTag(RECENTLY_DELETED_PURGE_TEST_TAG).performClick()
        compose.runOnIdle { assertFalse(purged) }

        compose.onNodeWithTag(RECENTLY_DELETED_PURGE_TEST_TAG).performClick()
        compose.runOnIdle { assertTrue(purged) }
    }

    private fun deletedMemo(): Memo = Memo(
        id = "deleted-device-test",
        content = "Recently deleted device test",
        entryDate = "2026-07-31",
        version = 2,
        createdAt = "2026-07-31T00:00:00Z",
        updatedAt = "2026-07-31T01:00:00Z",
        favoritedAt = null,
        archivedAt = null,
        deletedAt = "2026-07-31T01:00:00Z",
    )
}
