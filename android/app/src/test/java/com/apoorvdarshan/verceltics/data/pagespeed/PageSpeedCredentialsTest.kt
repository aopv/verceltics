package com.apoorvdarshan.verceltics.data.pagespeed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class PageSpeedCredentialsTest {
    @Test
    fun normalizesHttpsSiteIdentityWithoutLeakingApiKey() {
        val credentials = PageSpeedCredentials.create(
            apiKey = "  google-api-secret  ",
            siteUrl = "  HTTPS://Example.COM:443/Case/Path?q=hello%20world#ignored  ",
        )

        assertEquals(
            "https://example.com/Case/Path?q=hello%20world",
            credentials.siteUrl.toASCIIString(),
        )
        assertFalse(credentials.toString().contains("google-api-secret"))
        assertEquals("google-api-secret", credentials.apiKey.use { it })
    }

    @Test
    fun rejectsCleartextCredentialsAndIncompleteOrCredentialedUrls() {
        listOf(
            "http://example.com",
            "example.com",
            "https://user:password@example.com",
            "https:///missing-host",
        ).forEach { unsafe ->
            assertThrows("Expected rejection for $unsafe", IllegalArgumentException::class.java) {
                PageSpeedCredentials.create("key", unsafe)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            PageSpeedCredentials.create("   ", "https://example.com")
        }
    }
}
