package app.sillage.ui.memos

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sillage.R
import app.sillage.data.Memo
import app.sillage.ui.theme.SillageTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RecentlyDeletedMemoRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun exposesRestoreAndConfirmedPurgeActions() {
        var restored = false
        var purged = false
        compose.setContent {
            SillageTheme(darkTheme = false) {
                RecentlyDeletedMemoRow(
                    memo = deletedMemo(),
                    mutating = false,
                    onRestore = { restored = true },
                    onPurge = { purged = true },
                )
            }
        }

        val restore = context.getString(R.string.action_restore)
        compose.waitUntilExactlyOneExists(hasText(restore) and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText(restore) and hasClickAction()).performClick()
        compose.runOnIdle { assertTrue(restored) }

        val deleteForever = context.getString(R.string.action_delete_forever)
        compose.onNode(hasText(deleteForever) and hasClickAction()).performClick()
        compose.runOnIdle { assertFalse(purged) }

        val confirmDelete = context.getString(R.string.action_confirm_delete)
        compose.waitUntilExactlyOneExists(hasText(confirmDelete) and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText(confirmDelete) and hasClickAction()).performClick()
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

    companion object {
        private const val TIMEOUT_MS = 20_000L
    }
}
