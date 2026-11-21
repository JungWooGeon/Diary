package com.pass.diary.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN → "pbkdf2-sha256$iterations$salt$hash" (Base64). The PIN itself is never stored (v1 kept it in plain text). */
object PinHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun encode(pin: String, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): String {
        val b64 = Base64.getEncoder()
        return "pbkdf2-sha256$$iterations$${b64.encodeToString(salt)}$${b64.encodeToString(derive(pin, salt, iterations))}"
    }

    fun verify(pin: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val b64 = Base64.getDecoder()
        val salt = runCatching { b64.decode(parts[2]) }.getOrNull() ?: return false
        val want = runCatching { b64.decode(parts[3]) }.getOrNull() ?: return false
        return MessageDigest.isEqual(want, derive(pin, salt, iterations))
    }

    fun isValidPin(pin: String) = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    const val PIN_LENGTH = 4

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
