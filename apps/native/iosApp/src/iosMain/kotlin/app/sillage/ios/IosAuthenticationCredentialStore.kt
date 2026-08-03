@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.sillage.ios

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStore
import app.sillage.core.network.AuthenticationCredentialStoreException
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Stores only the refresh credential in the app's non-synchronizing iOS Keychain. */
internal class IosAuthenticationCredentialStore : AuthenticationCredentialStore {
    override val persistsAcrossLaunches: Boolean = true

    override fun read(baseUrl: String): AuthenticationCredentialReadResult {
        val query = createBaseQuery(baseUrl)
        try {
            set(query, kSecReturnData, kCFBooleanTrue)
            set(query, kSecMatchLimit, kSecMatchLimitOne)
            return memScoped {
                val result = alloc<CFTypeRefVar>()
                result.value = null
                when (SecItemCopyMatching(query, result.ptr)) {
                    errSecItemNotFound -> AuthenticationCredentialReadResult.Missing
                    errSecSuccess -> {
                        val value = result.value ?: unavailable()
                        try {
                            if (CFGetTypeID(value) != CFDataGetTypeID()) unavailable()
                            AuthenticationCredentialReadResult.Available(readUtf8(value.reinterpret()))
                        } finally {
                            CFRelease(value)
                        }
                    }
                    else -> unavailable()
                }
            }
        } finally {
            CFRelease(query)
        }
    }

    override fun write(baseUrl: String, refreshCookie: String) {
        val query = createBaseQuery(baseUrl)
        val update = createDictionary()
        val data = createUtf8Data(refreshCookie)
        try {
            set(update, kSecValueData, data)
            when (SecItemUpdate(query, update)) {
                errSecSuccess -> Unit
                errSecItemNotFound -> {
                    set(query, kSecValueData, data)
                    set(
                        query,
                        kSecAttrAccessible,
                        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                    )
                    when (SecItemAdd(query, null)) {
                        errSecSuccess -> Unit
                        errSecDuplicateItem -> {
                            val retryQuery = createBaseQuery(baseUrl)
                            try {
                                if (SecItemUpdate(retryQuery, update) != errSecSuccess) unavailable()
                            } finally {
                                CFRelease(retryQuery)
                            }
                        }
                        else -> unavailable()
                    }
                }
                else -> unavailable()
            }
        } finally {
            CFRelease(data)
            CFRelease(update)
            CFRelease(query)
        }
    }

    override fun delete(baseUrl: String) {
        val query = createBaseQuery(baseUrl)
        try {
            when (SecItemDelete(query)) {
                errSecSuccess,
                errSecItemNotFound,
                -> Unit
                else -> unavailable()
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun createBaseQuery(baseUrl: String): CFMutableDictionaryRef {
        val query = createDictionary()
        set(query, kSecClass, kSecClassGenericPassword)
        withString(KeychainService) { service ->
            set(query, kSecAttrService, service)
        }
        withString(baseUrl) { account ->
            set(query, kSecAttrAccount, account)
        }
        return query
    }

    private fun createDictionary(): CFMutableDictionaryRef {
        return memScoped {
            CFDictionaryCreateMutable(
                allocator = kCFAllocatorDefault,
                capacity = 0,
                keyCallBacks = kCFTypeDictionaryKeyCallBacks.ptr,
                valueCallBacks = kCFTypeDictionaryValueCallBacks.ptr,
            )
        } ?: unavailable()
    }

    private fun set(
        dictionary: CFMutableDictionaryRef,
        key: CFTypeRef?,
        value: CFTypeRef?,
    ) {
        CFDictionarySetValue(
            dictionary,
            key ?: unavailable(),
            value ?: unavailable(),
        )
    }

    private fun createUtf8Data(value: String): CFDataRef {
        val bytes = value.encodeToByteArray()
        try {
            if (bytes.isEmpty() || bytes.size.toLong() > MaxStoredCredentialBytes) unavailable()
            return bytes.usePinned { pinned ->
                CFDataCreate(
                    allocator = kCFAllocatorDefault,
                    bytes = pinned.addressOf(0).reinterpret<UByteVar>(),
                    length = bytes.size.toLong(),
                )
            } ?: unavailable()
        } finally {
            bytes.fill(0)
        }
    }

    private fun readUtf8(data: CFDataRef): String {
        val length = CFDataGetLength(data)
        if (length <= 0 || length > MaxStoredCredentialBytes) unavailable()
        val bytes = CFDataGetBytePtr(data) ?: unavailable()
        val copy = ByteArray(length.toInt()) { index -> bytes[index].toByte() }
        return try {
            copy.decodeToString(throwOnInvalidSequence = true)
        } finally {
            copy.fill(0)
        }
    }

    private inline fun <T> withString(value: String, operation: (CFStringRef) -> T): T {
        val string = CFStringCreateWithCString(
            alloc = kCFAllocatorDefault,
            cStr = value,
            encoding = kCFStringEncodingUTF8,
        ) ?: unavailable()
        return try {
            operation(string)
        } finally {
            CFRelease(string)
        }
    }

    private fun unavailable(): Nothing = throw AuthenticationCredentialStoreException()

    private companion object {
        const val KeychainService = "app.sillage.native.authentication.refresh"
        const val MaxStoredCredentialBytes = 4096L
    }
}
