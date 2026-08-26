package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.ProviderHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetlifyApiTest {
    @Test
    fun validationUsesExactVersionedEndpointBearerAndIosFallbackSemantics() {
        val client = RecordingHttpClient()
        val parser = FakeParser(user = NetlifyUser(null, null, null, null, null, null))
        val token = SecretValue.of("netlify-secret")

        val profile = NetlifyApi(client, parser).newValidatePersonalTokenCall(token).execute()

        assertEquals("/api/v1/user", client.lastPath)
        assertEquals(token, client.lastToken)
        assertEquals("Netlify Account", profile.displayName)
        assertEquals(16, profile.id.length)
        assertTrue(profile.id.matches(Regex("[0-9a-f]{16}")))
    }

    @Test
    fun sitesDeploysDetailsAndBuildReadsUseOnlyGetCallsAtScopedPaths() {
        val client = RecordingHttpClient()
        val api = NetlifyApi(client, FakeParser())
        val token = SecretValue.of("netlify-secret")

        api.newListSitesPageCall(token, page = 2, perPage = 100).execute()
        assertEquals("/api/v1/sites", client.lastPath)
        assertEquals(listOf("per_page" to "100", "page" to "2"), client.lastQuery)

        api.newSiteDetailsCall(token, "site-123").execute()
        assertEquals("/api/v1/sites/site-123", client.lastPath)

        api.newListDeploymentsPageCall(token, "site-123", 3, 50).execute()
        assertEquals("/api/v1/sites/site-123/deploys", client.lastPath)
        assertEquals(listOf("per_page" to "50", "page" to "3"), client.lastQuery)

        api.newListBuildsPageCall(token, "site-123", 1, 100).execute()
        assertEquals("/api/v1/sites/site-123/builds", client.lastPath)

        api.newBuildCall(token, "build-456").execute()
        assertEquals("/api/v1/builds/build-456", client.lastPath)
        assertEquals(5, client.requestCount)
        assertEquals(token, client.lastToken)
    }

    @Test
    fun pathAndPageInputsAreBoundedBeforeTransport() {
        val api = NetlifyApi(RecordingHttpClient(), FakeParser())
        val token = SecretValue.of("token")

        assertThrows(IllegalArgumentException::class.java) {
            api.newSiteDetailsCall(token, "../../other-origin")
        }
        assertThrows(IllegalArgumentException::class.java) {
            api.newListSitesPageCall(token, page = 0, perPage = 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            api.newListSitesPageCall(token, page = 1, perPage = 101)
        }
    }

    @Test
    fun authenticationFailureConsumesBodyWithoutLeakingTokenOrProviderText() {
        val secret = "never-log-this-netlify-token"
        val client = RecordingHttpClient(statusCode = 401, responseBody = secret.encodeToByteArray())
        val error = assertThrows(NetlifyApiException::class.java) {
            NetlifyApi(client, FakeParser(errorCode = "unauthorized"))
                .newValidatePersonalTokenCall(SecretValue.of(secret))
                .execute()
        }

        assertEquals(NetlifyFailureKind.AUTHENTICATION, error.failure.kind)
        assertEquals(401, error.failure.statusCode)
        assertEquals("unauthorized", error.errorCode)
        assertFalse(error.toString().contains(secret))
        assertFalse(error.message.orEmpty().contains(secret))
    }

    @Test
    fun malformedErrorBodyCannotMaskAuthenticationClassification() {
        val client = RecordingHttpClient(statusCode = 401, responseBody = byteArrayOf(0, 1, 2))
        val error = assertThrows(NetlifyApiException::class.java) {
            NetlifyApi(client, FakeParser(throwWhileParsingError = true))
                .newValidatePersonalTokenCall(SecretValue.of("token"))
                .execute()
        }

        assertEquals(NetlifyFailureKind.AUTHENTICATION, error.failure.kind)
        assertEquals(401, error.failure.statusCode)
        assertEquals(null, error.errorCode)
    }

    @Test
    fun providerControlledErrorCodeIsRedactedFromExceptionRendering() {
        val reflectedSecret = "reflected-secret-token"
        val error = assertThrows(NetlifyApiException::class.java) {
            NetlifyApi(
                RecordingHttpClient(statusCode = 403, responseBody = reflectedSecret.encodeToByteArray()),
                FakeParser(errorCode = reflectedSecret),
            ).newValidatePersonalTokenCall(SecretValue.of("token"))
                .execute()
        }

        assertEquals(reflectedSecret, error.errorCode)
        assertFalse(error.toString().contains(reflectedSecret))
        assertFalse(error.message.orEmpty().contains(reflectedSecret))
        assertTrue(error.toString().contains("<redacted>"))
    }

    @Test
    fun publicApiContainsNoMutationOrGenericRawRequestEscapeHatch() {
        val methodNames = NetlifyReadApi::class.java.methods.map { it.name.lowercase() }
        assertTrue(methodNames.all { name ->
            listOf("create", "update", "delete", "post", "put", "patch", "redeploy", "raw")
                .none(name::contains)
        })
    }

    private class RecordingHttpClient(
        private val statusCode: Int = 200,
        private val responseBody: ByteArray = byteArrayOf(1),
    ) : ProviderHttpClient {
        var lastPath: String? = null
        var lastQuery: List<Pair<String, String>> = emptyList()
        var lastToken: SecretValue? = null
        var requestCount: Int = 0

        override fun newGetCall(
            relativePath: String,
            queryParameters: List<Pair<String, String>>,
            bearerToken: SecretValue?,
            headers: Map<String, String>,
        ): CancelableCall<HttpResponse> {
            lastPath = relativePath
            lastQuery = queryParameters
            lastToken = bearerToken
            requestCount += 1
            return valueCall(HttpResponse(statusCode, responseBody, emptyMap()))
        }
    }

    private class FakeParser(
        private val user: NetlifyUser = NetlifyUser(
            id = "user-123",
            uid = null,
            fullName = "Apoorv",
            name = null,
            email = "apoorv@example.com",
            avatarUrl = null,
        ),
        private val errorCode: String? = null,
        private val throwWhileParsingError: Boolean = false,
    ) : NetlifyJsonParser {
        override fun parseUser(bytes: ByteArray): NetlifyUser = user

        override fun parseSites(bytes: ByteArray): List<NetlifySite> = listOf(SITE)

        override fun parseSiteDetails(bytes: ByteArray, expectedSiteId: String): NetlifySiteDetails =
            NetlifySiteDetails(SITE.copy(id = expectedSiteId), emptyList(), null, null)

        override fun parseDeployments(bytes: ByteArray): List<NetlifyDeployment> = listOf(DEPLOYMENT)

        override fun parseBuilds(bytes: ByteArray): List<NetlifyBuild> = listOf(BUILD)

        override fun parseBuild(bytes: ByteArray): NetlifyBuild = BUILD

        override fun parseErrorCode(bytes: ByteArray): String? {
            if (throwWhileParsingError) throw NetlifyResponseFormatException("malformed error body")
            return errorCode
        }
    }

    companion object {
        private val SITE = NetlifySite("site-123", "verceltics", null, null, "current", null, null)
        private val DEPLOYMENT = NetlifyDeployment(
            "deploy-123", "Production", "ready", 1L, null, "main", "Initial deploy",
        )
        private val BUILD = NetlifyBuild("build-456", "deploy-123", "abc", true, null, 1L)

        private fun <T> valueCall(value: T): CancelableCall<T> = object : CancelableCall<T> {
            override fun execute(): T = value
            override fun cancel() = Unit
        }
    }
}
