package app.sillage.desktop

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStore
import app.sillage.core.network.AuthenticationCredentialStoreException
import app.sillage.core.network.MemoryOnlyAuthenticationCredentialStore
import com.sun.jna.Native
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopAuthenticationCredentialStoreTest {
    @Test
    fun selectsPersistentStoresForSupportedDesktopVaults() {
        val macOsStore = desktopAuthenticationCredentialStore("Mac OS X")
        val windowsStore = desktopAuthenticationCredentialStore("Windows 11")

        assertIs<MacOsAuthenticationCredentialStore>(macOsStore)
        assertTrue(macOsStore.persistsAcrossLaunches)
        assertIs<WindowsAuthenticationCredentialStore>(windowsStore)
        assertTrue(windowsStore.persistsAcrossLaunches)
        assertSame(
            MemoryOnlyAuthenticationCredentialStore,
            desktopAuthenticationCredentialStore("Linux"),
        )
        assertFalse(MemoryOnlyAuthenticationCredentialStore.persistsAcrossLaunches)
    }

    @Test
    fun mapsWindowsCredentialStructureForCurrentPointerSize() {
        val expectedSize = when (Native.POINTER_SIZE) {
            8 -> 80
            4 -> 52
            else -> error("Unsupported pointer size: ${Native.POINTER_SIZE}")
        }

        assertEquals(expectedSize, windowsCredentialStructureSize())
    }

    @Test
    fun rejectsInvalidWindowsCredentialInputsBeforeNativeCall() {
        val store = WindowsAuthenticationCredentialStore()

        assertFailsWith<AuthenticationCredentialStoreException> {
            store.write("https://example.test", "x".repeat(2_561))
        }
        assertFailsWith<AuthenticationCredentialStoreException> {
            store.delete("https://example.test\u0000alias")
        }
        assertFailsWith<AuthenticationCredentialStoreException> {
            store.read("https://" + "x".repeat(32_768))
        }
    }

    @Test
    fun roundTripsRotatesAndDeletesGenericPasswordItemOnMacOs() {
        if (!isMacOsName(System.getProperty("os.name"))) return
        assertRoundTrip(
            store = MacOsAuthenticationCredentialStore(),
            baseUrl = "https://keychain-${UUID.randomUUID()}.example.test",
        )
    }

    @Test
    fun roundTripsRotatesAndDeletesGenericCredentialOnWindows() {
        if (!isWindowsName(System.getProperty("os.name"))) return
        assertRoundTrip(
            store = WindowsAuthenticationCredentialStore(),
            baseUrl = "https://credential-manager-${UUID.randomUUID()}.example.test",
        )
    }

    @Test
    fun rejectsInvalidCredentialWithoutCreatingVaultItemOnPersistentDesktopHost() {
        val store = when {
            isMacOsName(System.getProperty("os.name")) ->
                MacOsAuthenticationCredentialStore()
            isWindowsName(System.getProperty("os.name")) ->
                WindowsAuthenticationCredentialStore()
            else -> return
        }
        val baseUrl = "https://credential-invalid-${UUID.randomUUID()}.example.test"

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

    private fun assertRoundTrip(
        store: AuthenticationCredentialStore,
        baseUrl: String,
    ) {
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
