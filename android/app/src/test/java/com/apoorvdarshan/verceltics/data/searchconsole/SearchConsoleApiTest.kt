package com.apoorvdarshan.verceltics.data.searchconsole

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchConsoleApiTest {
    @Test
    fun listUsesFixedWebmastersOriginAndReturnsOnlyVerifiedProperties() {
        val transport = RecordingTransport()
        val parser = FakeParser(
            properties = SearchConsolePropertyList(
                listOf(
                    SearchConsoleProperty("sc-domain:verified.com", "siteOwner"),
                    SearchConsoleProperty("sc-domain:unverified.com", "siteUnverifiedUser"),
                ),
            ),
        )

        val result = SearchConsoleApi(transport, parser, nowMillis = { 0L })
            .newListVerifiedPropertiesCall(credential())
            .execute()

        assertEquals(listOf("webmasters", "v3", "sites"), transport.lastRequest?.pathSegments)
        assertEquals(SearchConsoleGoogleOrigin.WEBMASTERS, transport.lastRequest?.origin)
        assertEquals(listOf("sc-domain:verified.com"), result.properties.map { it.siteUrl })
    }

    @Test
    fun analyticsEncodesIosCompatibleQueryAndPropertyAsOneSafePathSegment() {
        val transport = RecordingTransport()
        val query = SearchConsoleAnalyticsQuery(
            SearchConsoleDateRange("2026-07-01", "2026-07-31"),
            dimensions = listOf(SearchConsoleDimension.DATE, SearchConsoleDimension.QUERY),
            dimensionFilterGroups = listOf(
                SearchConsoleDimensionFilterGroup(
                    listOf(
                        SearchConsoleDimensionFilter(
                            SearchConsoleFilterDimension.QUERY,
                            SearchConsoleFilterOperator.CONTAINS,
                            "swift \"ui\"",
                        ),
                    ),
                ),
            ),
            rowLimit = 250,
            startRow = 500,
            dataState = SearchConsoleDataState.ALL,
        )

        SearchConsoleApi(transport, FakeParser(), nowMillis = { 0L })
            .newAnalyticsPageCall(credential(), "sc-domain:example.com", query)
            .execute()

        val request = checkNotNull(transport.lastRequest)
        assertEquals(
            listOf("webmasters", "v3", "sites", "sc-domain:example.com", "searchAnalytics", "query"),
            request.pathSegments,
        )
        val body = checkNotNull(transport.lastBodyText)
        assertTrue(body.contains("\"startDate\":\"2026-07-01\""))
        assertTrue(body.contains("\"dimensions\":[\"date\",\"query\"]"))
        assertTrue(body.contains("swift \\\"ui\\\""))
        assertTrue(body.contains("\"startRow\":500"))
    }

    @Test
    fun sitemapAndInspectionRoutesPreserveEncodedUserValuesAndFixedCustomVerb() {
        val transport = RecordingTransport()
        val api = SearchConsoleApi(transport, FakeParser(), nowMillis = { 0L })
        val credential = credential()
        api.newListSitemapsCall(
            credential,
            "https://example.com/",
            "https://example.com/sitemap-index.xml",
        ).execute()
        assertEquals(
            listOf("sitemapIndex" to "https://example.com/sitemap-index.xml"),
            transport.lastRequest?.queryParameters,
        )

        api.newGetSitemapCall(
            credential,
            "https://example.com/",
            "https://example.com/sitemap.xml?locale=en",
        ).execute()
        assertEquals(
            "https://example.com/sitemap.xml?locale=en",
            transport.lastRequest?.pathSegments?.last(),
        )

        api.newInspectUrlCall(
            credential,
            "https://example.com/article",
            "https://example.com/",
        ).execute()
        assertEquals(SearchConsoleGoogleOrigin.INSPECTION, transport.lastRequest?.origin)
        assertEquals("index:inspect", transport.lastRequest?.pathSegments?.last())
        assertTrue(transport.lastRequest?.literalColonInLastSegment == true)
    }

    @Test
    fun refreshUsesOnlyTokenOriginAndPreservesRefreshIdentityWhenGoogleOmitsIt() {
        val secret = "refresh-never-print"
        val transport = RecordingTransport()
        val parser = FakeParser(
            token = SearchConsoleTokenResponse(
                SecretValue.of("new-access"), null, "Bearer", null, 3_600,
            ),
        )

        val refreshed = SearchConsoleApi(transport, parser, nowMillis = { 1_000L })
            .newRefreshCredentialCall(credential(refresh = secret), "client.apps.googleusercontent.com")
            .execute()

        assertEquals(SearchConsoleGoogleOrigin.OAUTH_TOKEN, transport.lastRequest?.origin)
        assertEquals(listOf("token"), transport.lastRequest?.pathSegments)
        assertEquals(SecretValue.of(secret), refreshed.refreshToken)
        assertEquals(3_601_000L, refreshed.expiresAtMillis)
        assertTrue(checkNotNull(transport.lastBodyText).contains("grant_type=refresh_token"))
        assertFalse(checkNotNull(transport.lastRequest).toString().contains(secret))
        assertFalse(refreshed.toString().contains(secret))
    }

    @Test
    fun expiredOrUnderScopedCredentialFailsBeforeTransport() {
        val transport = RecordingTransport()
        val api = SearchConsoleApi(transport, FakeParser(), nowMillis = { 10_000L })
        val expired = credential(expiresAt = 20_000L)
        val error = assertThrows(SearchConsoleApiException::class.java) {
            api.newListVerifiedPropertiesCall(expired)
        }
        assertEquals(SearchConsoleFailureKind.EXPIRED_CREDENTIAL, error.failure.kind)
        assertEquals(0, transport.requestCount)

        val underScoped = SearchConsoleOAuthCredential(
            SecretValue.of("access"), null, "Bearer", listOf("openid"),
            1_000_000L, null, null,
        )
        val scopeError = assertThrows(SearchConsoleApiException::class.java) {
            api.newListVerifiedPropertiesCall(underScoped)
        }
        assertEquals(SearchConsoleFailureKind.AUTHORIZATION, scopeError.failure.kind)
    }

    @Test
    fun malformedErrorBodyCannotMaskAuthenticationAndProviderTextIsNotRendered() {
        val reflected = "reflected-provider-secret"
        val transport = RecordingTransport(401, reflected.encodeToByteArray())
        val parser = FakeParser(throwOnError = true)
        val error = assertThrows(SearchConsoleApiException::class.java) {
            SearchConsoleApi(transport, parser, nowMillis = { 0L })
                .newListVerifiedPropertiesCall(credential())
                .execute()
        }
        assertEquals(SearchConsoleFailureKind.AUTHENTICATION, error.failure.kind)
        assertEquals(401, error.failure.statusCode)
        assertFalse(error.toString().contains(reflected))
    }

    @Test
    fun validationRejectsInvalidDatesRowsDuplicatesAndUnsafeUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            SearchConsoleApi.validateQuery(
                SearchConsoleAnalyticsQuery(SearchConsoleDateRange("2026-02-29", "2026-03-01")),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchConsoleApi.validateQuery(
                SearchConsoleAnalyticsQuery(
                    SearchConsoleDateRange("2026-01-01", "2026-01-02"),
                    rowLimit = 25_001,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchConsoleApi.validatedSiteUrl("sc-domain:bad/domain")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SearchConsoleApi.validatedHttpUrl("https://user:pass@example.com", "URL")
        }
    }

    private fun credential(
        refresh: String? = "refresh",
        expiresAt: Long = 1_000_000L,
    ) = SearchConsoleOAuthCredential(
        SecretValue.of("access"), refresh?.let(SecretValue::of), "Bearer",
        SearchConsoleOAuthCredential.REQUIRED_SCOPES, expiresAt, "subject", "a@example.com",
    )

    private class RecordingTransport(
        private val status: Int = 200,
        private val responseBody: ByteArray = byteArrayOf(1),
    ) : SearchConsoleHttpTransport {
        var lastRequest: SearchConsoleHttpRequest? = null
        var lastBodyText: String? = null
        var requestCount = 0

        override fun newCall(request: SearchConsoleHttpRequest): CancelableCall<HttpResponse> {
            lastRequest = request
            lastBodyText = request.takeBody()?.decodeToString()
            requestCount += 1
            return valueCall(HttpResponse(status, responseBody, emptyMap()))
        }
    }

    private class FakeParser(
        private val properties: SearchConsolePropertyList = SearchConsolePropertyList(emptyList()),
        private val token: SearchConsoleTokenResponse = SearchConsoleTokenResponse(
            SecretValue.of("new-access"), null, "Bearer", null, 3_600,
        ),
        private val throwOnError: Boolean = false,
    ) : SearchConsoleJsonParser {
        override fun parseProperties(bytes: ByteArray) = properties
        override fun parseAnalytics(bytes: ByteArray) = SearchConsoleAnalyticsResponse(emptyList(), null, null)
        override fun parseSitemaps(bytes: ByteArray) = emptyList<SearchConsoleSitemap>()
        override fun parseSitemap(bytes: ByteArray) = SearchConsoleSitemap(
            "https://example.com/sitemap.xml", null, false, false, null, null, 0, 0, emptyList(),
        )
        override fun parseInspection(bytes: ByteArray) = SearchConsoleUrlInspectionResult(
            null, null, null, null, null,
        )
        override fun parseTokenResponse(bytes: ByteArray) = token
        override fun parseErrorReason(bytes: ByteArray): String? {
            if (throwOnError) throw SearchConsoleResponseFormatException("malformed")
            return null
        }
    }

    companion object {
        private fun <T> valueCall(value: T): CancelableCall<T> = object : CancelableCall<T> {
            override fun execute(): T = value
            override fun cancel() = Unit
        }
    }
}
