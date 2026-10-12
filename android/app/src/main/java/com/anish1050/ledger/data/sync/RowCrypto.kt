package com.anish1050.ledger.data.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// AES-256-GCM per row. The key is derived once from the user's passphrase and
// the server only ever sees ciphertext and nonce.
class RowCrypto(private val key: ByteArray) {

    private val random = SecureRandom()

    data class Sealed(val ciphertext: ByteArray, val nonce: ByteArray)

    fun encrypt(plain: String): Sealed {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return Sealed(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), nonce)
    }

    fun decrypt(ciphertext: ByteArray, nonce: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    companion object {
        const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private const val ITERATIONS = 600_000

        fun deriveKey(passphrase: CharArray, userId: String): ByteArray {
            val spec = PBEKeySpec(passphrase, "ledger:$userId".toByteArray(), ITERATIONS, 256)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }
    }
}
