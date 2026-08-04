package app.sillage.ios

import app.sillage.ui.application.SillageNativeNetworkStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
internal class IosNetworkMonitor : AutoCloseable {
    private val monitor = nw_path_monitor_create()
    private val mutableStatus = MutableStateFlow(SillageNativeNetworkStatus.Unknown)
    val status: StateFlow<SillageNativeNetworkStatus> = mutableStatus.asStateFlow()

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            mutableStatus.value = if (nw_path_get_status(path) == nw_path_status_satisfied) {
                SillageNativeNetworkStatus.Available
            } else {
                SillageNativeNetworkStatus.Unavailable
            }
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    override fun close() {
        nw_path_monitor_cancel(monitor)
    }
}
