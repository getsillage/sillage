@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.sillage.ios

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStoreException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun rejectsInvalidCredentialWithoutCreatingKeychainItem() {
        val store = IosAuthenticationCredentialStore()
        val baseUrl = "https://keychain-invalid-${NSUUID.UUID().UUIDString}.example.test"

        try {
            store.delete(baseUrl)

            assertFailsWith<AuthenticationCredentialStoreException> {
                store.write(baseUrl, "")
            }
            assertFailsWith<AuthenticationCredentialStoreException> {
                store.write(baseUrl, "x".repeat(5_000))
            }
            assertEquals(AuthenticationCredentialReadResult.Missing, store.read(baseUrl))
        } finally {
            store.delete(baseUrl)
        }
    }
}
