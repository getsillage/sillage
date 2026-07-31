package app.sillage.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sillage.MainActivity
import app.sillage.data.LocalDataStore
import app.sillage.data.LocalStateStore
import app.sillage.data.MemoListFilter
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class OfflineRecordJourneyTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<out ComponentActivity>? = null

    @Before
    fun resetAppState() {
        context.deleteDatabase(LocalStateStore.DATABASE_NAME)
        context.getSharedPreferences("sillage.local_data", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("sillage.session", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun closeActivity() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun createsAnOfflineRecordAndLoadsItAfterAColdActivityRelaunch() {
        val record = "设备级离线记录 ${System.nanoTime()}"
        launch()

        compose.waitUntilExactlyOneExists(hasText("离线模式") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("离线模式") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasContentDescription("新建记录") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasContentDescription("新建记录") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("内容") and hasSetTextAction(), TIMEOUT_MS)
        compose.onNode(hasText("内容") and hasSetTextAction()).performTextReplacement(record)
        compose.onNode(hasContentDescription("保存") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasContentDescription("更多操作") and hasClickAction(), TIMEOUT_MS)

        scenario?.close()
        scenario = null
        launch()

        compose.waitUntilExactlyOneExists(hasText(record) and hasClickAction(), TIMEOUT_MS)
    }

    @Test
    fun opensTheBundledOpenSourceNoticesFromSettings() {
        launch()

        compose.waitUntilExactlyOneExists(hasText("离线模式") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("离线模式") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("设置") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasScrollAction(), TIMEOUT_MS)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("开源软件许可"))
        compose.waitUntilExactlyOneExists(hasText("开源软件许可") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("开源软件许可") and hasClickAction())
            .performClick()

        compose.waitUntilAtLeastOneExists(
            hasText("Sillage Android — Open-source software notices", substring = true),
            TIMEOUT_MS,
        )
    }

    @Test
    fun restoresAndPermanentlyDeletesARecordFromRecentlyDeleted() {
        val record = "最近删除生命周期 ${System.nanoTime()}"
        seedOfflineMemo(record)
        launch()

        compose.waitUntilExactlyOneExists(hasText("离线模式") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("离线模式") and hasClickAction()).performClick()
        openRecordDetail(record)
        deleteOpenRecordFromDetail()
        selectMemoFilter(MemoListFilter.Deleted)
        compose.waitUntilAtLeastOneExists(hasText(record), TIMEOUT_MS)
        compose.onNode(hasText("恢复") and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("最近删除中没有记录。"), TIMEOUT_MS)

        selectMemoFilter(MemoListFilter.Unarchived)
        openRecordDetail(record)
        deleteOpenRecordFromDetail()
        selectMemoFilter(MemoListFilter.Deleted)
        compose.waitUntilAtLeastOneExists(hasText(record), TIMEOUT_MS)
        compose.onNode(hasText("永久删除") and hasClickAction()).performClick()
        compose.onNode(hasText("确认删除") and hasClickAction()).performClick()

        compose.waitUntilAtLeastOneExists(hasText("最近删除中没有记录。"), TIMEOUT_MS)
        compose.onAllNodes(hasText(record)).assertCountEquals(0)
    }

    private fun seedOfflineMemo(content: String) {
        val stateStore = LocalStateStore(context)
        try {
            LocalDataStore(stateStore).createMemo(content, LocalDate.now().toString())
        } finally {
            stateStore.close()
        }
    }

    private fun openRecordDetail(record: String) {
        compose.waitUntilExactlyOneExists(hasText(record) and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText(record) and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasContentDescription("更多操作") and hasClickAction(), TIMEOUT_MS)
    }

    private fun selectMemoFilter(filter: MemoListFilter) {
        val tag = "memo-filter-${filter.name}"
        waitForMemoListIdle()
        compose.waitUntilExactlyOneExists(hasTestTag(tag) and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasTestTag(tag) and hasClickAction()).performTouchInput { click() }
        waitForMemoListIdle()
    }

    private fun waitForMemoListIdle() {
        compose.waitUntilExactlyOneExists(
            hasContentDescription("刷新记录") and hasClickAction() and isEnabled(),
            TIMEOUT_MS,
        )
    }

    private fun deleteOpenRecordFromDetail() {
        compose.waitUntilExactlyOneExists(hasContentDescription("更多操作") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasContentDescription("更多操作") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("删除") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("删除") and hasClickAction()).performClick()
        compose.waitUntilExactlyOneExists(hasText("确认删除") and hasClickAction(), TIMEOUT_MS)
        compose.onNode(hasText("确认删除") and hasClickAction()).performClick()
        compose.waitUntilAtLeastOneExists(hasText("已移至最近删除。"), TIMEOUT_MS)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    companion object {
        private const val TIMEOUT_MS = 20_000L
    }
}
