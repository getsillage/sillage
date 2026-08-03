package app.sillage.desktop

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStoreException
import app.sillage.core.network.MemoryOnlyAuthenticationCredentialStore
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopAuthenticationCredentialStoreTest {
    @Test
    fun selectsPersistentStoreOnlyForMacOs() {
        assertTrue(desktopAuthenticationCredentialStore("Mac OS X").persistsAcrossLaunches)
        assertSame(
            MemoryOnlyAuthenticationCredentialStore,
            desktopAuthenticationCredentialStore("Windows 11"),
        )
        assertSame(
            MemoryOnlyAuthenticationCredentialStore,
            desktopAuthenticationCredentialStore("Linux"),
        )
        assertFalse(MemoryOnlyAuthenticationCredentialStore.persistsAcrossLaunches)
    }

    @Test
    fun roundTripsRotatesAndDeletesGenericPasswordItemOnMacOs() {
        if (!isMacOsName(System.getProperty("os.name"))) return
        val store = MacOsAuthenticationCredentialStore()
        val baseUrl = "https://keychain-${UUID.randomUUID()}.example.test"

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
    fun rejectsInvalidCredentialWithoutCreatingKeychainItemOnMacOs() {
        if (!isMacOsName(System.getProperty("os.name"))) return
        val store = MacOsAuthenticationCredentialStore()
        val baseUrl = "https://keychain-invalid-${UUID.randomUUID()}.example.test"

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
