@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.sillage.ios

import app.sillage.core.network.AuthenticationCredentialReadResult
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.Foundation.NSUUID

class IosAuthenticationCredentialStoreTest {
    @Test
    fun roundTripsRotatesAndDeletesGenericPasswordItem() {
        val store = IosAuthenticationCredentialStore()
        val baseUrl = "https://keychain-${NSUUID.UUID().UUIDString}.example.test"

        try {
            store.delete(baseUrl)
            assertEquals(AuthenticationCredentialReadResult.Missing, store.read(baseUrl))

            store.write(baseUrl, "refresh-old")
            assertEquals(
                AuthenticationCredentialReadResult.Available("refresh-old"),
                store.read(baseUrl),
            )

            store.write(baseUrl, "refresh-new")
            assertEquals(
                AuthenticationCredentialReadResult.Available("refresh-new"),
                store.read(baseUrl),
            )

            store.delete(baseUrl)
            assertEquals(AuthenticationCredentialReadResult.Missing, store.read(baseUrl))
        } finally {
            store.delete(baseUrl)
        }
    }
}
