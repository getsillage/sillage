package app.sillage.desktop

import app.sillage.ui.application.SillageNativeNetworkStatus
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopNetworkMonitorTest {
    @Test
    fun publishesPolledNetworkStatusChanges() = runTest {
        var currentStatus = SillageNativeNetworkStatus.Unavailable
        val monitor = DesktopNetworkMonitor(
            dispatcher = StandardTestDispatcher(testScheduler),
            pollIntervalMillis = 100L,
            statusProvider = { currentStatus },
        )
        try {
            assertEquals(SillageNativeNetworkStatus.Unavailable, monitor.status.value)
            runCurrent()

            currentStatus = SillageNativeNetworkStatus.Available
            advanceTimeBy(100L)
            runCurrent()

            assertEquals(SillageNativeNetworkStatus.Available, monitor.status.value)
        } finally {
            monitor.close()
        }
    }
}
