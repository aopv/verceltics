package com.apoorvdarshan.verceltics.data.searchconsole

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SearchConsoleOAuthTest {
    private val configuration = SearchConsoleOAuthClientConfiguration(
        clientId = "12345-example.apps.googleusercontent.com",
        redirectScheme = "com.googleusercontent.apps.12345-example",
    )

    @Test
    fun authorizationRequestUsesPkceOfflineConsentAndReadOnlyScope() {
        val transaction = SearchConsolePkceTransaction.create(
            configuration = configuration,
            randomBytes = { size -> ByteArray(size) { if (size == 64) 0x21 else 0x42 } },
        )
        val uri = transaction.authorizationUri
        val query = decodedQuery(uri)

        assertEquals("https", uri.scheme)
        assertEquals("accounts.google.com", uri.host)
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("offline", query["access_type"])
        assertEquals("consent", query["prompt"])
        assertTrue(query.getValue("scope").contains(SearchConsoleOAuthCredential.READ_ONLY_SCOPE))
        assertFalse(query.getValue("code_challenge").contains("="))
        assertFalse(transaction.toString().contains(query.getValue("state")))
    }

    @Test
    fun callbackRequiresExactRedirectAndConstantTimeStateMatch() {
        val transaction = SearchConsolePkceTransaction.create(
            configuration = configuration,
            randomBytes = { size -> ByteArray(size) { if (size == 64) 0x13 else 0x37 } },
        )
        val state = decodedQuery(transaction.authorizationUri).getValue("state")
        val callback = URI(
            "${configuration.redirectUri}?code=one-use-code&state=$state",
        )

        assertEquals("one-use-code", transaction.authorizationCode(callback).use { it })

        val mismatchTransaction = SearchConsolePkceTransaction.create(
            configuration = configuration,
            randomBytes = { size -> ByteArray(size) { if (size == 64) 0x24 else 0x48 } },
        )
        val failure = runCatching {
            mismatchTransaction.authorizationCode(
                URI("${configuration.redirectUri}?code=one-use-code&state=wrong"),
            )
        }.exceptionOrNull()
        assertTrue(failure is SearchConsoleOAuthException)
        assertFalse(failure.toString().contains("one-use-code"))
    }

    @Test
    fun duplicateCallbackFieldsAndProviderErrorsAreRejectedWithoutLeakingCode() {
        val transaction = SearchConsolePkceTransaction.create(
            configuration = configuration,
            randomBytes = { size -> ByteArray(size) { size.toByte() } },
        )
        val state = decodedQuery(transaction.authorizationUri).getValue("state")

        val duplicate = runCatching {
            transaction.authorizationCode(
                URI("${configuration.redirectUri}?code=first&code=second&state=$state"),
            )
        }.exceptionOrNull()
        assertTrue(duplicate is SearchConsoleOAuthException)

        val providerTransaction = SearchConsolePkceTransaction.create(
            configuration = configuration,
            randomBytes = { size -> ByteArray(size) { (size + 1).toByte() } },
        )
        val providerState = decodedQuery(providerTransaction.authorizationUri).getValue("state")
        val provider = runCatching {
            providerTransaction.authorizationCode(
                URI("${configuration.redirectUri}?error=access_denied&state=$providerState"),
            )
        }.exceptionOrNull()
        assertTrue(provider is SearchConsoleOAuthException)
        assertEquals("access_denied", provider?.message)
    }

    @Test
    fun configurationRequiresReverseClientIdSchemeAndRejectsReservedSchemes() {
        listOf("https", "http", "file", "content", "intent", "verceltics-google").forEach { scheme ->
            assertThrows(IllegalArgumentException::class.java) {
                SearchConsoleOAuthClientConfiguration(configuration.clientId, scheme)
            }
        }
        assertEquals(
            "com.googleusercontent.apps.12345-example:/oauthredirect",
            configuration.redirectUri,
        )
    }

    @Test
    fun callbackPreflightRequiresExactRedirectBeforeBrokerCanClaimIt() {
        val expected = URI(configuration.redirectUri)
        assertTrue(URI("${configuration.redirectUri}?code=x&state=y").matchesOAuthRedirect(expected))
        assertFalse(URI("${configuration.redirectScheme}:/wrong?code=x").matchesOAuthRedirect(expected))
        assertFalse(URI("${configuration.redirectScheme}://attacker/oauthredirect?code=x").matchesOAuthRedirect(expected))
        assertFalse(URI("${configuration.redirectUri}#fragment").matchesOAuthRedirect(expected))
    }

    private fun decodedQuery(uri: URI): Map<String, String> = uri.rawQuery
        .split('&')
        .associate { pair ->
            val (name, value) = pair.split('=', limit = 2)
            URLDecoder.decode(name, StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }
}
