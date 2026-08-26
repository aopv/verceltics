package com.apoorvdarshan.verceltics.data.account

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AccountEnvelopeCodecTest {
    @Test
    fun roundTripPreservesSealedBytes() {
        val payload = SealedPayload(
            initializationVector = ByteArray(12) { it.toByte() },
            ciphertext = ByteArray(48) { (it * 3).toByte() },
        )

        val decoded = AccountEnvelopeCodec.decode(AccountEnvelopeCodec.encode(payload))

        assertArrayEquals(payload.initializationVector(), decoded.initializationVector())
        assertArrayEquals(payload.ciphertext(), decoded.ciphertext())
    }

    @Test
    fun rejectsUnknownVersion() {
        val bytes = AccountEnvelopeCodec.encode(
            SealedPayload(ByteArray(12), ByteArray(32)),
        )
        bytes[5] = 2

        assertThrows(IllegalArgumentException::class.java) {
            AccountEnvelopeCodec.decode(bytes)
        }
    }

    @Test
    fun rejectsTrailingOrTruncatedData() {
        val bytes = AccountEnvelopeCodec.encode(
            SealedPayload(ByteArray(12), ByteArray(32)),
        )

        assertThrows(IllegalArgumentException::class.java) {
            AccountEnvelopeCodec.decode(bytes + 0x01)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AccountEnvelopeCodec.decode(bytes.copyOf(bytes.size - 1))
        }
    }
}
