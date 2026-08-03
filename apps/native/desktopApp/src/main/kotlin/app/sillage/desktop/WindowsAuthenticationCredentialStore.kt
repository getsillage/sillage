package app.sillage.desktop

import app.sillage.core.network.AuthenticationCredentialReadResult
import app.sillage.core.network.AuthenticationCredentialStore
import app.sillage.core.network.AuthenticationCredentialStoreException
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

private const val MaxCredentialBlobBytes = 5 * 512

/** Stores only the refresh credential in the current user's Windows Credential Manager. */
internal class WindowsAuthenticationCredentialStore : AuthenticationCredentialStore {
    override val persistsAcrossLaunches: Boolean = true

    override fun read(baseUrl: String): AuthenticationCredentialReadResult = secureOperation {
        val targetName = createWideString(credentialTargetName(baseUrl))
        try {
            val result = PointerByReference()
            if (
                api.CredReadW(
                    targetName,
                    CredTypeGeneric,
                    FlagsNone,
                    result,
                ) == False
            ) {
                if (kernel32.GetLastError() == ErrorNotFound) {
                    return@secureOperation AuthenticationCredentialReadResult.Missing
                }
                unavailable()
            }

            val credentialPointer = result.value ?: unavailable()
            var credential: WindowsCredential? = null
            try {
                credential = WindowsCredential(credentialPointer)
                credential.read()
                if (
                    credential.type != CredTypeGeneric ||
                    credential.persist != CredPersistLocalMachine
                ) {
                    unavailable()
                }
                AuthenticationCredentialReadResult.Available(readUtf8(credential))
            } finally {
                try {
                    credential?.clearBlob()
                } finally {
                    api.CredFree(credentialPointer)
                }
            }
        } finally {
            targetName.clear()
        }
    }

    override fun write(baseUrl: String, refreshCookie: String) = secureOperation {
        val bytes = refreshCookie.toByteArray(StandardCharsets.UTF_8)
        try {
            if (bytes.isEmpty() || bytes.size > MaxCredentialBlobBytes) unavailable()
            val blob = Memory(bytes.size.toLong())
            try {
                blob.write(0, bytes, 0, bytes.size)
                val targetName = createWideString(credentialTargetName(baseUrl))
                try {
                    val userName = createWideString(CredentialUserName)
                    try {
                        val credential = WindowsCredential().apply {
                            type = CredTypeGeneric
                            this.targetName = targetName
                            credentialBlobSize = bytes.size
                            credentialBlob = blob
                            persist = CredPersistLocalMachine
                            this.userName = userName
                            write()
                        }
                        if (api.CredWriteW(credential, FlagsNone) == False) {
                            unavailable()
                        }
                    } finally {
                        userName.clear()
                    }
                } finally {
                    targetName.clear()
                }
            } finally {
                blob.clear()
            }
        } finally {
            bytes.fill(0)
        }
    }

    override fun delete(baseUrl: String) = secureOperation {
        val targetName = createWideString(credentialTargetName(baseUrl))
        try {
            if (
                api.CredDeleteW(
                    targetName,
                    CredTypeGeneric,
                    FlagsNone,
                ) == False &&
                kernel32.GetLastError() != ErrorNotFound
            ) {
                unavailable()
            }
        } finally {
            targetName.clear()
        }
    }

    private fun readUtf8(credential: WindowsCredential): String {
        val length = credential.credentialBlobSize
        if (length <= 0 || length > MaxCredentialBlobBytes) unavailable()
        val blob = credential.credentialBlob ?: unavailable()
        val bytes = blob.getByteArray(0, length)
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

    private fun createWideString(value: String): Memory {
        val byteSize = (value.length.toLong() + 1) * Native.WCHAR_SIZE
        return Memory(byteSize).apply {
            setWideString(0, value)
        }
    }

    private fun credentialTargetName(baseUrl: String): String {
        val targetName = CredentialTargetPrefix + baseUrl
        if ('\u0000' in targetName || targetName.length > MaxGenericTargetNameCharacters) {
            unavailable()
        }
        return targetName
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
        const val CredentialTargetPrefix = "app.sillage.native.authentication.refresh:"
        const val CredentialUserName = "Sillage"
        const val MaxGenericTargetNameCharacters = 32_767
        const val CredTypeGeneric = 1
        const val CredPersistLocalMachine = 2
        const val FlagsNone = 0
        const val False = 0
        const val ErrorNotFound = 1168

        val kernel32: Kernel32 by lazy { Kernel32.INSTANCE }
        val api: WindowsCredentialManagerApi by lazy {
            kernel32
            Native.load("Advapi32", WindowsCredentialManagerApi::class.java)
        }
    }
}

internal fun windowsCredentialStructureSize(): Int = WindowsCredential().size()

private interface WindowsCredentialManagerApi : StdCallLibrary {
    fun CredReadW(
        targetName: Pointer,
        type: Int,
        flags: Int,
        credential: PointerByReference,
    ): Int

    fun CredWriteW(
        credential: WindowsCredential,
        flags: Int,
    ): Int

    fun CredDeleteW(
        targetName: Pointer,
        type: Int,
        flags: Int,
    ): Int

    fun CredFree(buffer: Pointer)
}

@Structure.FieldOrder(
    "flags",
    "type",
    "targetName",
    "comment",
    "lastWritten",
    "credentialBlobSize",
    "credentialBlob",
    "persist",
    "attributeCount",
    "attributes",
    "targetAlias",
    "userName",
)
internal class WindowsCredential : Structure {
    @JvmField
    var flags: Int = 0

    @JvmField
    var type: Int = 0

    @JvmField
    var targetName: Pointer? = null

    @JvmField
    var comment: Pointer? = null

    @JvmField
    var lastWritten: WindowsFileTime = WindowsFileTime()

    @JvmField
    var credentialBlobSize: Int = 0

    @JvmField
    var credentialBlob: Pointer? = null

    @JvmField
    var persist: Int = 0

    @JvmField
    var attributeCount: Int = 0

    @JvmField
    var attributes: Pointer? = null

    @JvmField
    var targetAlias: Pointer? = null

    @JvmField
    var userName: Pointer? = null

    constructor() : super()

    constructor(pointer: Pointer) : super(pointer)

    fun clearBlob() {
        val length = credentialBlobSize
        if (length in 1..MaxCredentialBlobBytes) {
            credentialBlob?.setMemory(0, length.toLong(), 0)
        }
    }
}

@Structure.FieldOrder(
    "lowDateTime",
    "highDateTime",
)
internal class WindowsFileTime : Structure() {
    @JvmField
    var lowDateTime: Int = 0

    @JvmField
    var highDateTime: Int = 0
}
