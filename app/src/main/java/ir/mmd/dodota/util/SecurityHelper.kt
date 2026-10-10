package ir.mmd.dodota.util

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityHelper {

    private const val SALT = "DODOTA_FINANCE_SECURE_SALT_2026"

    /**
     * Hashes a 4 to 6 digit PIN using SHA-256 with salt.
     */
    fun hashPin(pin: String): String {
        val input = "$SALT:$pin"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies if the entered PIN matches the stored hash.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean {
        if (storedHash.isEmpty()) return true
        return hashPin(pin) == storedHash
    }

    /**
     * Generates a secure human-readable Recovery Code (e.g. DODOTA-7X9K-3M2W).
     */
    fun generateRecoveryCode(): String {
        val charPool = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val random = SecureRandom()
        fun randomSegment(length: Int): String {
            return (1..length)
                .map { charPool[random.nextInt(charPool.length)] }
                .joinToString("")
        }
        return "DODOTA-${randomSegment(4)}-${randomSegment(4)}"
    }
}
