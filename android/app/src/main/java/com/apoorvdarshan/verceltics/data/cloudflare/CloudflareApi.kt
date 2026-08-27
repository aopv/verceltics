package com.apoorvdarshan.verceltics.data.cloudflare

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.ProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.SecureProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.map

interface CloudflareReadApi {
    fun newVerifyTokenCall(token: SecretValue): CancelableCall<CloudflareTokenVerification>

    fun newAccountsPageCall(
        token: SecretValue,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflareAccountSummary>>

    fun newZonesPageCall(
        token: SecretValue,
        accountId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflareZone>>

    fun newPagesProjectsPageCall(
        token: SecretValue,
        accountId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflarePagesProject>>

    fun newWorkerScriptsCall(
        token: SecretValue,
        accountId: String,
    ): CancelableCall<List<CloudflareWorkerScript>>
}

/**
 * Cloudflare's fixed-origin, API-token-only, read-only dashboard surface.
 *
 * Deliberately excluded from this foundation: `/user` plus email/global-key authentication, every
 * mutation, and non-dashboard products. The iOS API-token flow also validates with token verify and
 * accounts rather than requiring the broader `/user` profile.
 */
class CloudflareApi(
    private val httpClient: ProviderHttpClient = SecureProviderHttpClient(ORIGIN),
    private val jsonParser: CloudflareJsonParser = AndroidCloudflareJsonParser(),
) : CloudflareReadApi {
    override fun newVerifyTokenCall(token: SecretValue): CancelableCall<CloudflareTokenVerification> =
        httpClient.newGetCall(
            relativePath = "$API_PREFIX/user/tokens/verify",
            bearerToken = token,
        ).map { response ->
            parseSuccessful(response, "verify the Cloudflare API token", jsonParser::parseTokenVerification)
        }

    override fun newAccountsPageCall(
        token: SecretValue,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflareAccountSummary>> {
        requirePage(page, perPage)
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/accounts",
            queryParameters = paginationQuery(page, perPage),
            bearerToken = token,
        ).map { response ->
            parseSuccessful(response, "load Cloudflare accounts", jsonParser::parseAccountsPage)
        }
    }

    override fun newZonesPageCall(
        token: SecretValue,
        accountId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflareZone>> {
        requirePage(page, perPage)
        val normalizedAccountId = safePathSegment(accountId, "account")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/zones",
            queryParameters = listOf("account.id" to normalizedAccountId) + paginationQuery(page, perPage),
            bearerToken = token,
        ).map { response ->
            parseSuccessful(response, "load Cloudflare zones", jsonParser::parseZonesPage)
        }
    }

    override fun newPagesProjectsPageCall(
        token: SecretValue,
        accountId: String,
        page: Int,
        perPage: Int,
    ): CancelableCall<CloudflarePage<CloudflarePagesProject>> {
        requirePage(page, perPage)
        val normalizedAccountId = safePathSegment(accountId, "account")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/accounts/$normalizedAccountId/pages/projects",
            queryParameters = paginationQuery(page, perPage),
            bearerToken = token,
        ).map { response ->
            parseSuccessful(response, "load Cloudflare Pages projects", jsonParser::parsePagesProjectsPage)
        }
    }

    override fun newWorkerScriptsCall(
        token: SecretValue,
        accountId: String,
    ): CancelableCall<List<CloudflareWorkerScript>> {
        val normalizedAccountId = safePathSegment(accountId, "account")
        return httpClient.newGetCall(
            relativePath = "$API_PREFIX/accounts/$normalizedAccountId/workers/scripts",
            bearerToken = token,
        ).map { response ->
            parseSuccessful(response, "load Cloudflare Workers", jsonParser::parseWorkerScripts)
        }
    }

    private inline fun <T> parseSuccessful(
        response: HttpResponse,
        operation: String,
        parser: (ByteArray) -> T,
    ): T {
        requireSuccessful(response, operation)
        return try {
            response.useBody(parser)
        } catch (error: CloudflareEnvelopeRejectedException) {
            throw CloudflareApiException(
                failure = CloudflareFailure(
                    CloudflareFailureKind.PROVIDER_REJECTED,
                    "Cloudflare rejected the request while trying to $operation.",
                ),
                errorCode = error.errorCode,
            )
        }
    }

    private fun requireSuccessful(response: HttpResponse, operation: String) {
        if (response.statusCode in 200..299) return
        // Error metadata is optional. Malformed or reflected provider text must not mask the
        // reliable HTTP classification or escape into user-facing/loggable exception strings.
        val errorCode = response.useBody { body ->
            runCatching { jsonParser.parseErrorCode(body) }.getOrNull()
        }
        val failure = when (response.statusCode) {
            401 -> CloudflareFailure(
                CloudflareFailureKind.AUTHENTICATION,
                "Cloudflare rejected this API token.",
                response.statusCode,
            )
            403 -> CloudflareFailure(
                CloudflareFailureKind.AUTHORIZATION,
                "This Cloudflare API token cannot access the requested resource.",
                response.statusCode,
            )
            404 -> CloudflareFailure(
                CloudflareFailureKind.NOT_FOUND,
                "Cloudflare could not find the requested resource.",
                response.statusCode,
            )
            408 -> CloudflareFailure(
                CloudflareFailureKind.NETWORK,
                "Cloudflare timed out while trying to $operation.",
                response.statusCode,
            )
            429 -> CloudflareFailure(
                CloudflareFailureKind.RATE_LIMITED,
                "Cloudflare is rate limiting requests. Please try again shortly.",
                response.statusCode,
            )
            in 500..599 -> CloudflareFailure(
                CloudflareFailureKind.TEMPORARY,
                "Cloudflare is temporarily unavailable.",
                response.statusCode,
            )
            else -> CloudflareFailure(
                CloudflareFailureKind.TEMPORARY,
                "Unable to $operation (HTTP ${response.statusCode}).",
                response.statusCode,
            )
        }
        throw CloudflareApiException(
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
        require(page in 1..MAXIMUM_PAGE_NUMBER) { "Invalid Cloudflare page." }
        require(perPage in 1..MAXIMUM_PAGE_SIZE) {
            "Cloudflare page size must be between 1 and $MAXIMUM_PAGE_SIZE."
        }
    }

    private fun safePathSegment(value: String, label: String): String {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= CF_MAX_ID_CHARACTERS) {
            "A Cloudflare $label id is required."
        }
        require(SAFE_PATH_SEGMENT.matches(trimmed)) {
            "The Cloudflare $label id is not safe for a provider path."
        }
        return trimmed
    }

    private fun paginationQuery(page: Int, perPage: Int): List<Pair<String, String>> = listOf(
        "page" to page.toString(),
        "per_page" to perPage.toString(),
    )

    companion object {
        const val ORIGIN = "https://api.cloudflare.com/"
        const val API_PREFIX = "/client/v4"
        const val MAXIMUM_PAGE_SIZE = 100
        const val MAXIMUM_PAGE_NUMBER = 500
        private val SAFE_PATH_SEGMENT = Regex("[A-Za-z0-9._~-]+")
        private val SAFE_ERROR_CODE = Regex("[A-Za-z0-9._:-]{1,128}")
    }
}

class CloudflareApiException(
    val failure: CloudflareFailure,
    val errorCode: String?,
) : RuntimeException(failure.message) {
    override fun toString(): String =
        "CloudflareApiException(kind=${failure.kind}, statusCode=${failure.statusCode}, " +
            "errorCode=${if (errorCode == null) "none" else "<redacted>"})"
}
