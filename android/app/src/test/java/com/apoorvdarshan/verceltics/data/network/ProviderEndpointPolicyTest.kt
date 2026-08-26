package com.apoorvdarshan.verceltics.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ProviderEndpointPolicyTest {
    private val policy = ProviderEndpointPolicy("https://api.vercel.com/")

    @Test
    fun resolvesProviderRelativePathAndEncodesQuery() {
        val uri = policy.resolve(
            relativePath = "/v9/projects",
            queryParameters = listOf("limit" to "100", "search" to "hello world"),
        )

        assertEquals("https", uri.scheme)
        assertEquals("api.vercel.com", uri.host)
        assertEquals("/v9/projects", uri.path)
        assertEquals("limit=100&search=hello%20world", uri.rawQuery)
    }

    @Test
    fun rejectsCleartextCredentialsAbsoluteUrlsAndTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderEndpointPolicy("http://api.vercel.com/")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderEndpointPolicy("https://user@example.com/")
        }
        listOf(
            "https://evil.example/v2/user",
            "//evil.example/v2/user",
            "/v2/../admin",
            "/v2/%2e%2e/admin",
            "/v2/%252e%252e/admin",
            "/v2/user?token=bad",
        ).forEach { unsafe ->
            assertThrows("Expected rejection for $unsafe", IllegalArgumentException::class.java) {
                policy.resolve(unsafe)
            }
        }
    }

    @Test
    fun redirectsMustRemainOnExactHttpsOrigin() {
        val current = URI("https://api.vercel.com/v2/user")

        assertEquals(
            URI("https://api.vercel.com/v2/profile"),
            policy.resolveRedirect(current, "/v2/profile"),
        )
        listOf(
            "http://api.vercel.com/v2/profile",
            "https://evil.example/v2/profile",
            "https://user@api.vercel.com/v2/profile",
            "https://api.vercel.com:444/v2/profile",
        ).forEach { unsafe ->
            assertThrows("Expected rejection for $unsafe", IllegalArgumentException::class.java) {
                policy.resolveRedirect(current, unsafe)
            }
        }
    }

    @Test
    fun authorizationAndHostHeadersAreProtectedCaseInsensitively() {
        listOf("Authorization", "authorization", "HOST", "Host").forEach { protectedName ->
            assertThrows(IllegalArgumentException::class.java) {
                policy.validateUnprivilegedHeaders(mapOf(protectedName to "value"))
            }
        }
        policy.validateUnprivilegedHeaders(mapOf("X-Team-Id" to "team_123"))
        assertTrue(policy.isSameOrigin(URI("https://api.vercel.com:443/v9/projects")))
    }
}
