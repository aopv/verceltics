package com.apoorvdarshan.verceltics.data.account

interface AccountCipher {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): SealedPayload

    fun decrypt(payload: SealedPayload, associatedData: ByteArray): ByteArray
}

class SealedPayload(initializationVector: ByteArray, ciphertext: ByteArray) {
    private val storedInitializationVector = initializationVector.copyOf()
    private val storedCiphertext = ciphertext.copyOf()

    fun initializationVector(): ByteArray = storedInitializationVector.copyOf()

    fun ciphertext(): ByteArray = storedCiphertext.copyOf()

    override fun toString(): String =
        "SealedPayload(initializationVector=<redacted>, ciphertext=<redacted>)"
}
