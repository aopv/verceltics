package com.apoorvdarshan.verceltics.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VercelAccountPayloadCodecTest {
    @Test
    fun roundTripPreservesAccountAndRawProviderId() {
        val original = account("secret-token-value")

        val decoded = VercelAccountPayloadCodec.decode(VercelAccountPayloadCodec.encode(original))

        assertEquals("vercel", decoded.providerId)
        assertEquals(original.id, decoded.id)
        assertEquals(original.displayName, decoded.displayName)
        assertEquals(original.email, decoded.email)
        assertEquals(original.token, decoded.token)
        assertEquals(original.createdAtMillis, decoded.createdAtMillis)
        assertEquals(original.updatedAtMillis, decoded.updatedAtMillis)
    }

    @Test
    fun printableRepresentationsNeverContainToken() {
        val token = "do-not-print-this-token"
        val account = account(token)

        assertEquals("<redacted>", account.token.toString())
        assertTrue(account.toString().contains("token=<redacted>"))
        assertFalse(account.toString().contains(token))
        assertFalse(SealedPayload(ByteArray(12), ByteArray(16)).toString().contains("["))
    }

    private fun account(token: String) = VercelAccount(
        id = "user_123",
        displayName = "Apoorv",
        email = "apoorv@example.com",
        token = SecretValue.of(token),
        createdAtMillis = 1_000L,
        updatedAtMillis = 2_000L,
    )
}
