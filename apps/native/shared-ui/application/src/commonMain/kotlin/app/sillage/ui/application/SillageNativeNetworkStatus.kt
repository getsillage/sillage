package app.sillage.ui.application

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class SillageNativeNetworkStatus {
    Unknown,
    Unavailable,
    Available,
}

internal fun Flow<SillageNativeNetworkStatus>.networkRecoveryEvents(): Flow<Unit> = flow {
    var wasUnavailable = false
    collect { status ->
        when (status) {
            SillageNativeNetworkStatus.Unknown -> Unit
            SillageNativeNetworkStatus.Unavailable -> wasUnavailable = true
            SillageNativeNetworkStatus.Available -> {
                if (wasUnavailable) {
                    wasUnavailable = false
                    emit(Unit)
                }
            }
        }
    }
}
