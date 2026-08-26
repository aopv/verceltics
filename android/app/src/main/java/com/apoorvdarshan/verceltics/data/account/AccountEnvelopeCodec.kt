package com.apoorvdarshan.verceltics.data.account

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** Versioned binary envelope. Only the nonce and authenticated ciphertext are stored. */
object AccountEnvelopeCodec {
    private const val MAGIC = 0x56435441 // VCTA
    private const val FORMAT_VERSION = 1
    private const val CIPHER_AES_256_GCM = 1
    private const val GCM_IV_BYTES = 12
    private const val MIN_CIPHERTEXT_BYTES = 16
    private const val MAX_CIPHERTEXT_BYTES = 512 * 1024

    fun encode(payload: SealedPayload): ByteArray {
        val initializationVector = payload.initializationVector()
        val ciphertext = payload.ciphertext()
        require(initializationVector.size == GCM_IV_BYTES) { "Unexpected GCM nonce size." }
        require(ciphertext.size in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
            "Unexpected encrypted account size."
        }
        return try {
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { output ->
                    output.writeInt(MAGIC)
                    output.writeShort(FORMAT_VERSION)
                    output.writeShort(CIPHER_AES_256_GCM)
                    output.writeShort(initializationVector.size)
                    output.writeInt(ciphertext.size)
                    output.write(initializationVector)
                    output.write(ciphertext)
                }
                bytes.toByteArray()
            }
        } finally {
            initializationVector.fill(0)
            ciphertext.fill(0)
        }
    }

    fun decode(bytes: ByteArray): SealedPayload {
        require(bytes.size >= HEADER_BYTES + GCM_IV_BYTES + MIN_CIPHERTEXT_BYTES) {
            "The account envelope is truncated."
        }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC) { "Unknown account envelope." }
            require(input.readUnsignedShort() == FORMAT_VERSION) {
                "Unsupported account envelope version."
            }
            require(input.readUnsignedShort() == CIPHER_AES_256_GCM) {
                "Unsupported account cipher."
            }
            val initializationVectorSize = input.readUnsignedShort()
            val ciphertextSize = input.readInt()
            require(initializationVectorSize == GCM_IV_BYTES) { "Invalid GCM nonce size." }
            require(ciphertextSize in MIN_CIPHERTEXT_BYTES..MAX_CIPHERTEXT_BYTES) {
                "Invalid encrypted account size."
            }
            require(input.available() == initializationVectorSize + ciphertextSize) {
                "Invalid account envelope length."
            }
            val initializationVector = ByteArray(initializationVectorSize)
            val ciphertext = ByteArray(ciphertextSize)
            input.readFully(initializationVector)
            input.readFully(ciphertext)
            return try {
                SealedPayload(initializationVector, ciphertext)
            } finally {
                initializationVector.fill(0)
                ciphertext.fill(0)
            }
        }
    }

    private const val HEADER_BYTES = 4 + 2 + 2 + 2 + 4
}
