package app.sillage.data

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small encrypted-string layer over SharedPreferences.
 *
 * Values are stored under a separate `secure.*` key and encrypted with an
 * Android Keystore AES-GCM key. Reads fall back to the legacy plaintext key so
 * existing installs migrate lazily the next time the value is saved.
 */
internal class SecurePreferences(private val prefs: SharedPreferences) {
    fun getString(key: String, fallback: String?): String? {
        val encrypted = prefs.getString(secureKey(key), null)
        if (encrypted != null) {
            return runCatching { decrypt(encrypted) }.getOrElse { fallback }
        }
        return prefs.getString(key, fallback)
    }

    fun putString(
        editor: SharedPreferences.Editor,
        key: String,
        value: String,
    ): SharedPreferences.Editor {
        return editor
            .putString(secureKey(key), encrypt(value))
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

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return listOf(
            PAYLOAD_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
        ).joinToString(":")
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(":")
        if (parts.size != 3 || parts[0] != PAYLOAD_VERSION) {
            throw IllegalArgumentException("Unsupported secure preference payload")
        }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "sillage_secure_preferences_v1"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
        private const val PAYLOAD_VERSION = "v1"
    }
}
