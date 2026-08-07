package app.sillage.desktop

import app.sillage.ui.application.SillageNativeNetworkStatus
import java.net.NetworkInterface
import java.net.SocketException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class DesktopNetworkMonitor(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 2_000L,
    private val statusProvider: () -> SillageNativeNetworkStatus = ::desktopNetworkStatus,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableStatus = MutableStateFlow(statusProvider())
    val status: StateFlow<SillageNativeNetworkStatus> = mutableStatus.asStateFlow()

    init {
        require(pollIntervalMillis > 0L)
        scope.launch {
            while (isActive) {
                delay(pollIntervalMillis)
                mutableStatus.value = statusProvider()
            }
        }
    }

    override fun close() {
        scope.cancel()
    }
}

private fun desktopNetworkStatus(): SillageNativeNetworkStatus = try {
    val interfaces = NetworkInterface.getNetworkInterfaces()
        ?: return SillageNativeNetworkStatus.Unknown
    val available = interfaces.asSequence().any { networkInterface ->
        networkInterface.isUp && !networkInterface.isLoopback
    }
    if (available) {
        SillageNativeNetworkStatus.Available
    } else {
        SillageNativeNetworkStatus.Unavailable
    }
} catch (_: SocketException) {
    SillageNativeNetworkStatus.Unknown
} catch (_: SecurityException) {
    SillageNativeNetworkStatus.Unknown
}
