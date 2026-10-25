package com.vix.one.ms

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val ITERATIONS = 12000
    private const val GCM_TAG = 128
    private const val IV_SIZE = 12

    // Master secret is also split (not the same as API key)
    private fun masterSecret(): CharArray {
        val p = arrayOf("VixChat", "Sec", "2026", "Key!")
        return p.joinToString("").toCharArray()
    }

    private fun deriveKey(salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(masterSecret(), salt, ITERATIONS, KEY_BITS)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encrypt(plain: String, contextId: String): String {
        val salt = contextId.toByteArray(Charsets.UTF_8).copyOf(16)
        val key = deriveKey(salt)
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + encrypted
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String, contextId: String): String {
        return try {
            val salt = contextId.toByteArray(Charsets.UTF_8).copyOf(16)
            val key = deriveKey(salt)
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = combined.copyOfRange(0, IV_SIZE)
            val ciphertext = combined.copyOfRange(IV_SIZE, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            "[не удалось расшифровать]"
        }
    }
}
