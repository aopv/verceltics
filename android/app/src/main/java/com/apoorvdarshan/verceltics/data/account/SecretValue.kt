package com.apoorvdarshan.verceltics.data.account

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * A deliberately non-printable secret wrapper.
 *
 * The backing [String] is only exposed for the duration of [use]. This cannot make JVM strings
 * erasable, but it prevents accidental interpolation, logging, or data-class `toString()` leaks.
 */
class SecretValue private constructor(private val rawValue: String) {
    fun <T> use(block: (String) -> T): T = block(rawValue)

    internal fun utf8Bytes(): ByteArray = rawValue.toByteArray(StandardCharsets.UTF_8)

    override fun toString(): String = "<redacted>"

    override fun equals(other: Any?): Boolean {
        if (other !is SecretValue) return false
        val left = utf8Bytes()
        val right = other.utf8Bytes()
        return try {
            MessageDigest.isEqual(left, right)
        } finally {
            left.fill(0)
            right.fill(0)
        }
    }

    override fun hashCode(): Int = 0

    companion object {
        private const val MAX_SECRET_CHARACTERS = 16_384

        fun of(rawValue: String): SecretValue {
            require(rawValue.isNotBlank()) { "A secret cannot be blank." }
            require(rawValue.length <= MAX_SECRET_CHARACTERS) { "The secret is too long." }
            require(rawValue.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "A secret cannot contain control delimiters."
            }
            return SecretValue(rawValue)
        }
    }
}
