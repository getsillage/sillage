package app.sillage.ui.appshell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationPolicyTest {
    @Test
    fun historyAddsCurrentDestinationOnlyWhenOpeningAnotherDestination() {
        val history = listOf(AppDestination.Memos)

        assertEquals(
            history,
            AppNavigationPolicy.historyFor(
                current = AppDestination.MemoDetail,
                history = history,
                destination = AppDestination.MemoDetail,
            ),
        )
        assertEquals(
            history + AppDestination.MemoDetail,
            AppNavigationPolicy.historyFor(
                current = AppDestination.MemoDetail,
                history = history,
                destination = AppDestination.Editor,
            ),
        )
    }

    @Test
    fun backPopsHistoryOrUsesFallback() {
        assertEquals(
            AppBackNavigation(
                screen = AppDestination.MemoDetail,
                history = listOf(AppDestination.Memos),
            ),
            AppNavigationPolicy.back(
                history = listOf(AppDestination.Memos, AppDestination.MemoDetail),
                fallback = AppDestination.Memos,
            ),
        )
        assertEquals(
            AppBackNavigation(
                screen = AppDestination.Memos,
                history = emptyList(),
            ),
            AppNavigationPolicy.back(
                history = emptyList(),
                fallback = AppDestination.Memos,
            ),
        )
    }

    @Test
    fun recordsRootBackPolicyCoversSecondaryDestinationsAndCalendar() {
        assertTrue(
            AppNavigationPolicy.shouldReturnToRecords(
                current = AppDestination.Ask,
                recordsCalendarActive = false,
            ),
        )
        assertTrue(
            AppNavigationPolicy.shouldReturnToRecords(
                current = AppDestination.AISettings,
                recordsCalendarActive = false,
            ),
        )
        assertTrue(
            AppNavigationPolicy.shouldReturnToRecords(
                current = AppDestination.Memos,
                recordsCalendarActive = true,
            ),
        )
        assertFalse(
            AppNavigationPolicy.shouldReturnToRecords(
                current = AppDestination.MemoDetail,
                recordsCalendarActive = false,
            ),
        )
    }
}
