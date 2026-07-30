package app.sillage.data

import android.content.SharedPreferences

/**
 * Small encrypted-string layer over SharedPreferences.
 *
 * Values are stored under a separate `secure.*` key and encrypted with an
 * Android Keystore AES-GCM key. Reads fall back to the legacy plaintext key so
 * existing installs migrate lazily the next time the value is saved.
 */
internal class SecurePreferences(
    private val prefs: SharedPreferences,
    private val cipher: ValueCipher = KeystoreCipher(),
) {
    fun getString(key: String, fallback: String?): String? {
        val encrypted = prefs.getString(secureKey(key), null)
        if (encrypted != null) {
            return runCatching { cipher.decrypt(encrypted) }.getOrElse { fallback }
        }
        return prefs.getString(key, fallback)
    }

    /**
     * Reads a value while distinguishing "nothing stored" from "stored but
     * unreadable" (for example after a Keystore key loss). Callers that guard
     * user data must not treat an unreadable value as absent.
     */
    fun readString(key: String): SecureReadResult {
        val encrypted = prefs.getString(secureKey(key), null)
        if (encrypted != null) {
            return runCatching { SecureReadResult.Value(cipher.decrypt(encrypted)) }
                .getOrElse { SecureReadResult.Unreadable(encrypted) }
        }
        val legacy = prefs.getString(key, null) ?: return SecureReadResult.Missing
        return SecureReadResult.Value(legacy)
    }

    fun putString(
        editor: SharedPreferences.Editor,
        key: String,
        value: String,
    ): SharedPreferences.Editor {
        return editor
            .putString(secureKey(key), cipher.encrypt(value))
            .remove(key)
    }

    fun remove(
        editor: SharedPreferences.Editor,
        key: String,
    ): SharedPreferences.Editor {
        return editor
            .remove(secureKey(key))
            .remove(key)
    }

    private fun secureKey(key: String): String = "secure.$key"

}

internal sealed interface SecureReadResult {
    data object Missing : SecureReadResult

    data class Value(val value: String) : SecureReadResult

    /** Data exists but cannot be decrypted; [rawPayload] is the stored ciphertext. */
    data class Unreadable(val rawPayload: String) : SecureReadResult
}
