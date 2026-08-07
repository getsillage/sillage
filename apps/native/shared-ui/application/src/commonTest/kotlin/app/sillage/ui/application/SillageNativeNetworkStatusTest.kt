package app.sillage.ui.application

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SillageNativeNetworkStatusTest {
    @Test
    fun emitsOnlyConfirmedUnavailableToAvailableTransitions() = runTest {
        val events = flowOf(
            SillageNativeNetworkStatus.Unknown,
            SillageNativeNetworkStatus.Available,
            SillageNativeNetworkStatus.Available,
            SillageNativeNetworkStatus.Unavailable,
            SillageNativeNetworkStatus.Unavailable,
            SillageNativeNetworkStatus.Unknown,
            SillageNativeNetworkStatus.Available,
            SillageNativeNetworkStatus.Available,
            SillageNativeNetworkStatus.Unavailable,
            SillageNativeNetworkStatus.Available,
        ).networkRecoveryEvents().toList()

        assertEquals(listOf(Unit, Unit), events)
    }
}
