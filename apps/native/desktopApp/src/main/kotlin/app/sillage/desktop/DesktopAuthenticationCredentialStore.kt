package app.sillage.desktop

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStore
import app.sillage.core.network.AuthenticationCredentialStoreException
import app.sillage.core.network.MemoryOnlyAuthenticationCredentialStore
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.mac.CoreFoundation.CFDataRef
import com.sun.jna.platform.mac.CoreFoundation.CFDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFIndex
import com.sun.jna.platform.mac.CoreFoundation.CFMutableDictionaryRef
import com.sun.jna.platform.mac.CoreFoundation.CFStringRef
import com.sun.jna.platform.mac.CoreFoundation.CFTypeRef
import com.sun.jna.ptr.PointerByReference
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal fun desktopAuthenticationCredentialStore(
    osName: String = System.getProperty("os.name"),
): AuthenticationCredentialStore {
    return if (isMacOsName(osName)) {
        MacOsAuthenticationCredentialStore()
    } else {
        MemoryOnlyAuthenticationCredentialStore
    }
}

internal fun isMacOsName(osName: String): Boolean =
    osName.contains("mac", ignoreCase = true)

/** Stores only the refresh credential in the current user's non-synchronizing macOS Keychain. */
internal class MacOsAuthenticationCredentialStore : AuthenticationCredentialStore {
    override val persistsAcrossLaunches: Boolean = true

    override fun read(baseUrl: String): AuthenticationCredentialReadResult = secureOperation {
        val query = createBaseQuery(baseUrl)
        try {
            query.setValue(symbols.secReturnData, symbols.cfBooleanTrue)
            query.setValue(symbols.secMatchLimit, symbols.secMatchLimitOne)
            val result = PointerByReference()
            return@secureOperation when (security.SecItemCopyMatching(query, result)) {
                ErrSecItemNotFound -> AuthenticationCredentialReadResult.Missing
                ErrSecSuccess -> {
                    val data = CFDataRef(result.value ?: unavailable())
                    try {
                        if (!data.isTypeID(CoreFoundation.DATA_TYPE_ID)) unavailable()
                        AuthenticationCredentialReadResult.Available(readUtf8(data))
                    } finally {
                        data.release()
                    }
                }
                else -> unavailable()
            }
        } finally {
            query.release()
        }
    }

    override fun write(baseUrl: String, refreshCookie: String) = secureOperation {
        val data = createUtf8Data(refreshCookie)
        try {
            val query = createBaseQuery(baseUrl)
            try {
                val update = createDictionary()
                try {
                    update.setValue(symbols.secValueData, data)
                    when (security.SecItemUpdate(query, update)) {
                        ErrSecSuccess -> Unit
                        ErrSecItemNotFound -> {
                            query.setValue(symbols.secValueData, data)
                            when (security.SecItemAdd(query, null)) {
                                ErrSecSuccess -> Unit
                                ErrSecDuplicateItem -> {
                                    val retryQuery = createBaseQuery(baseUrl)
                                    try {
                                        if (
                                            security.SecItemUpdate(retryQuery, update) !=
                                                ErrSecSuccess
                                        ) {
                                            unavailable()
                                        }
                                    } finally {
                                        retryQuery.release()
                                    }
                                }
                                else -> unavailable()
                            }
                        }
                        else -> unavailable()
                    }
                } finally {
                    update.release()
                }
            } finally {
                query.release()
            }
        } finally {
            data.release()
        }
    }

    override fun delete(baseUrl: String) = secureOperation {
        val query = createBaseQuery(baseUrl)
        try {
            when (security.SecItemDelete(query)) {
                ErrSecSuccess,
                ErrSecItemNotFound,
                -> Unit
                else -> unavailable()
            }
        } finally {
            query.release()
        }
    }

    private fun createBaseQuery(baseUrl: String): CFMutableDictionaryRef {
        val query = createDictionary()
        try {
            query.setValue(symbols.secClass, symbols.secClassGenericPassword)
            withCfString(KeychainService) { service ->
                query.setValue(symbols.secAttrService, service)
            }
            withCfString(baseUrl) { account ->
                query.setValue(symbols.secAttrAccount, account)
            }
            query.setValue(symbols.secAttrSynchronizable, symbols.cfBooleanFalse)
            return query
        } catch (error: Throwable) {
            query.release()
            throw error
        }
    }

    private fun createDictionary(): CFMutableDictionaryRef {
        return CoreFoundation.INSTANCE.CFDictionaryCreateMutable(
            null,
            CFIndex(0),
            symbols.dictionaryKeyCallbacks,
            symbols.dictionaryValueCallbacks,
        ) ?: unavailable()
    }

    private fun createUtf8Data(value: String): CFDataRef {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.isEmpty() || bytes.size > MaxStoredCredentialBytes) unavailable()
        try {
            val memory = Memory(bytes.size.toLong())
            try {
                memory.write(0, bytes, 0, bytes.size)
                return CoreFoundation.INSTANCE.CFDataCreate(
                    null,
                    memory,
                    CFIndex(bytes.size.toLong()),
                ) ?: unavailable()
            } finally {
                memory.clear()
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun readUtf8(data: CFDataRef): String {
        val length = data.length
        if (length <= 0 || length > MaxStoredCredentialBytes) unavailable()
        val bytes = data.bytePtr.getByteArray(0, length)
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } finally {
            bytes.fill(0)
        }
    }

    private inline fun <T> withCfString(value: String, operation: (CFStringRef) -> T): T {
        val string = CFStringRef.createCFString(value)
        return try {
            operation(string)
        } finally {
            string.release()
        }
    }

    private inline fun <T> secureOperation(operation: () -> T): T {
        return try {
            operation()
        } catch (error: AuthenticationCredentialStoreException) {
            throw error
        } catch (error: Throwable) {
            if (error is VirtualMachineError || error is ThreadDeath) throw error
            unavailable()
        }
    }

    private fun unavailable(): Nothing = throw AuthenticationCredentialStoreException()

    private companion object {
        const val KeychainService = "app.sillage.native.authentication.refresh"
        const val MaxStoredCredentialBytes = 4096
        const val ErrSecSuccess = 0
        const val ErrSecDuplicateItem = -25299
        const val ErrSecItemNotFound = -25300

        val security: SecurityFramework by lazy {
            Native.load(SecurityFrameworkPath, SecurityFramework::class.java)
        }
        val symbols: MacOsKeychainSymbols by lazy(::MacOsKeychainSymbols)
    }
}

private interface SecurityFramework : Library {
    fun SecItemCopyMatching(query: CFDictionaryRef, result: PointerByReference): Int

    fun SecItemUpdate(query: CFDictionaryRef, attributesToUpdate: CFDictionaryRef): Int

    fun SecItemAdd(attributes: CFDictionaryRef, result: PointerByReference?): Int

    fun SecItemDelete(query: CFDictionaryRef): Int
}

private class MacOsKeychainSymbols {
    private val securityLibrary = NativeLibrary.getInstance(SecurityFrameworkPath)
    private val coreFoundationLibrary = NativeLibrary.getInstance(CoreFoundationFrameworkPath)

    val dictionaryKeyCallbacks: Pointer =
        coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks")
    val dictionaryValueCallbacks: Pointer =
        coreFoundationLibrary.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks")

    val secClass = securityString("kSecClass")
    val secClassGenericPassword = securityString("kSecClassGenericPassword")
    val secAttrService = securityString("kSecAttrService")
    val secAttrAccount = securityString("kSecAttrAccount")
    val secAttrSynchronizable = securityString("kSecAttrSynchronizable")
    val secReturnData = securityString("kSecReturnData")
    val secMatchLimit = securityString("kSecMatchLimit")
    val secMatchLimitOne = securityString("kSecMatchLimitOne")
    val secValueData = securityString("kSecValueData")
    val cfBooleanTrue = coreFoundationType("kCFBooleanTrue")
    val cfBooleanFalse = coreFoundationType("kCFBooleanFalse")

    private fun securityString(name: String): CFStringRef {
        return CFStringRef(indirectPointer(securityLibrary, name))
    }

    private fun coreFoundationType(name: String): CFTypeRef {
        return CFTypeRef(indirectPointer(coreFoundationLibrary, name))
    }

    private fun indirectPointer(library: NativeLibrary, name: String): Pointer {
        return library.getGlobalVariableAddress(name).getPointer(0)
            ?: throw AuthenticationCredentialStoreException()
    }
}

private const val SecurityFrameworkPath =
    "/System/Library/Frameworks/Security.framework/Security"
private const val CoreFoundationFrameworkPath =
    "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"
