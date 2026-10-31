package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted key–value storage for personal data (name, nickname, "about me").
 *
 * - AES-256-GCM (authenticated: any tampering makes decryption fail).
 * - The key is generated inside the Android Keystore (hardware-backed on most phones) and can
 *   never be read out of it, so copying the app's files to another device reveals nothing.
 * - Each value has its own random 12-byte IV; the key name is bound as associated data so values
 *   can't be swapped between keys.
 */
class SecureStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getString(key: String, default: String = ""): String {
        val stored = prefs.getString(key, null) ?: return default
        return decrypt(key, stored) ?: default
    }

    fun putString(key: String, value: String) {
        val encrypted = encrypt(key, value) ?: return
        prefs.edit().putString(key, encrypted).apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(key: String, plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.e(TAG, "Encrypt failed: ${e.javaClass.simpleName}")
        null
    }

    private fun decrypt(key: String, encoded: String): String? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        // Tampered value or a key that was wiped (e.g. lock-screen reset): treat as missing.
        Log.w(TAG, "Decrypt failed: ${e.javaClass.simpleName}")
        null
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecureStore"
        const val FILE = "mochi_secure"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mochi_secure_store_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
