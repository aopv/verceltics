package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.ProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.SecureProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.map
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

interface NetlifyReadApi {
    fun newValidatePersonalTokenCall(token: SecretValue): CancelableCall<NetlifyProfile>

    fun newListSitesPageCall(
        token: SecretValue,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifySite>>

    fun newSiteDetailsCall(
        token: SecretValue,
        siteId: String,
    ): CancelableCall<NetlifySiteDetails>

    fun newListDeploymentsPageCall(
        token: SecretValue,
        siteId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifyDeployment>>

    fun newListBuildsPageCall(
        token: SecretValue,
        siteId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifyBuild>>

    fun newBuildCall(token: SecretValue, buildId: String): CancelableCall<NetlifyBuild>
}

/** Netlify's fixed-origin, personal-token, read-only REST surface. */
class NetlifyApi(
    private val httpClient: ProviderHttpClient = SecureProviderHttpClient(ORIGIN),
    private val jsonParser: NetlifyJsonParser = AndroidNetlifyJsonParser(),
) : NetlifyReadApi {
    override fun newValidatePersonalTokenCall(token: SecretValue): CancelableCall<NetlifyProfile> =
        httpClient.newGetCall(
            relativePath = "$API_PREFIX/user",
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "validate the Netlify token")
            val user = response.useBody(jsonParser::parseUser)
            normalizedProfile(user, token)
        }

    override fun newListSitesPageCall(
        token: SecretValue,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifySite>> {
        requirePage(page, perPage)
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/sites",
            queryParameters = listOf("per_page" to perPage.toString(), "page" to page.toString()),
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load Netlify sites")
            response.useBody(jsonParser::parseSites)
        }
    }

    override fun newSiteDetailsCall(
        token: SecretValue,
        siteId: String,
    ): CancelableCall<NetlifySiteDetails> {
        val safeSiteId = safePathSegment(siteId, "site")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/sites/$safeSiteId",
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load the Netlify site")
            response.useBody { jsonParser.parseSiteDetails(it, siteId) }
        }
    }

    override fun newListDeploymentsPageCall(
        token: SecretValue,
        siteId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifyDeployment>> {
        requirePage(page, perPage)
        val safeSiteId = safePathSegment(siteId, "site")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/sites/$safeSiteId/deploys",
            queryParameters = listOf("per_page" to perPage.toString(), "page" to page.toString()),
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load Netlify deploys")
            response.useBody(jsonParser::parseDeployments)
        }
    }

    override fun newListBuildsPageCall(
        token: SecretValue,
        siteId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<List<NetlifyBuild>> {
        requirePage(page, perPage)
        val safeSiteId = safePathSegment(siteId, "site")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/sites/$safeSiteId/builds",
            queryParameters = listOf("per_page" to perPage.toString(), "page" to page.toString()),
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load Netlify builds")
            response.useBody(jsonParser::parseBuilds)
        }
    }

    override fun newBuildCall(token: SecretValue, buildId: String): CancelableCall<NetlifyBuild> {
        val safeBuildId = safePathSegment(buildId, "build")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/builds/$safeBuildId",
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load the Netlify build")
            response.useBody(jsonParser::parseBuild)
        }
    }

    private fun normalizedProfile(user: NetlifyUser, token: SecretValue): NetlifyProfile {
        val id = firstNonBlank(user.id, user.uid, user.email) ?: credentialFingerprint(token)
        val displayName = firstNonBlank(user.fullName, user.name, user.email) ?: "Netlify Account"
        return NetlifyProfile(
            id = id,
            displayName = displayName,
            email = user.email,
            avatarUrl = user.avatarUrl,
        )
    }

    private fun requireSuccessful(response: HttpResponse, operation: String) {
        if (response.statusCode in 200..299) return
        // Error metadata is optional. A malformed provider body must never hide the reliable HTTP
        // classification (especially 401/403 authentication failures).
        val errorCode = response.useBody { body ->
            runCatching { jsonParser.parseErrorCode(body) }.getOrNull()
        }
        val failure = when (response.statusCode) {
            401, 403 -> NetlifyFailure(
                NetlifyFailureKind.AUTHENTICATION,
                "Netlify rejected this personal token.",
                response.statusCode,
            )
            404 -> NetlifyFailure(
                NetlifyFailureKind.NOT_FOUND,
                "Netlify could not find the requested resource.",
                response.statusCode,
            )
            408 -> NetlifyFailure(
                NetlifyFailureKind.NETWORK,
                "Netlify timed out while trying to $operation.",
                response.statusCode,
            )
            429 -> NetlifyFailure(
                NetlifyFailureKind.RATE_LIMITED,
                "Netlify is rate limiting requests. Please try again shortly.",
                response.statusCode,
            )
            in 500..599 -> NetlifyFailure(
                NetlifyFailureKind.TEMPORARY,
                "Netlify is temporarily unavailable.",
                response.statusCode,
            )
            else -> NetlifyFailure(
                NetlifyFailureKind.TEMPORARY,
                "Unable to $operation (HTTP ${response.statusCode}).",
                response.statusCode,
            )
        }
        throw NetlifyApiException(
            failure = failure,
            errorCode = errorCode?.takeIf(SAFE_ERROR_CODE::matches),
        )
    }

    private inline fun <T> HttpResponse.useBody(block: (ByteArray) -> T): T {
        val bytes = takeBody()
        return try {
            block(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun requirePage(page: Int, perPage: Int) {
        require(page in 1..MAXIMUM_PAGE_NUMBER) { "Invalid Netlify page." }
        require(perPage in 1..MAXIMUM_PAGE_SIZE) { "Netlify page size must be between 1 and 100." }
    }

    private fun safePathSegment(value: String, label: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_ID_CHARACTERS) {
            "A Netlify $label id is required."
        }
        require(SAFE_PATH_SEGMENT.matches(trimmed)) {
            "The Netlify $label id is not safe for a provider path."
        }
        return trimmed
    }

    private fun credentialFingerprint(token: SecretValue): String {
        val bytes = token.use { it.toByteArray(StandardCharsets.UTF_8) }
        val digest = try {
            MessageDigest.getInstance("SHA-256").digest(bytes)
        } finally {
            bytes.fill(0)
        }
        return try {
            digest.joinToString("") { "%02x".format(it) }.take(16)
        } finally {
            digest.fill(0)
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    companion object {
        const val ORIGIN: String = "https://api.netlify.com/"
        const val API_PREFIX: String = "/api/v1"
        const val MAXIMUM_PAGE_SIZE: Int = 100
        const val MAXIMUM_PAGE_NUMBER: Int = 200
        private val SAFE_PATH_SEGMENT = Regex("[A-Za-z0-9._~-]+")
        private val SAFE_ERROR_CODE = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}

class NetlifyApiException(
    val failure: NetlifyFailure,
    val errorCode: String?,
) : RuntimeException(failure.message) {
    override fun toString(): String =
        "NetlifyApiException(kind=${failure.kind}, statusCode=${failure.statusCode}, " +
            "errorCode=${if (errorCode == null) "none" else "<redacted>"})"
}
