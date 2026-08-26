package com.apoorvdarshan.verceltics.data.vercel

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.account.VercelAccount
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.ProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.SecureProviderHttpClient
import com.apoorvdarshan.verceltics.data.network.map

class VercelApi(
    private val httpClient: ProviderHttpClient = SecureProviderHttpClient(BASE_URL),
    private val jsonParser: VercelJsonParser = AndroidVercelJsonParser(),
) {
    fun newValidatePersonalTokenCall(token: SecretValue): CancelableCall<VercelUser> =
        httpClient.newGetCall(
            relativePath = "/v2/user",
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "validate the Vercel token")
            response.useBody(jsonParser::parseUser)
        }

    fun newListProjectsCall(
        token: SecretValue,
        limit: Int = DEFAULT_PROJECT_LIMIT,
        until: String? = null,
    ): CancelableCall<VercelProjectsPage> {
        require(limit in 1..MAX_PROJECT_LIMIT) { "Vercel project limit must be between 1 and 100." }
        val query = buildList {
            add("limit" to limit.toString())
            until?.takeIf(String::isNotBlank)?.let { add("until" to it) }
        }
        return httpClient.newGetCall(
            relativePath = "/v9/projects",
            queryParameters = query,
            bearerToken = token,
        ).map { response ->
            requireSuccessful(response, "load Vercel projects")
            response.useBody(jsonParser::parseProjects)
        }
    }

    fun accountForValidatedUser(
        user: VercelUser,
        token: SecretValue,
        nowMillis: Long = System.currentTimeMillis(),
    ): VercelAccount = VercelAccount(
        id = user.id,
        displayName = user.name?.takeIf(String::isNotBlank) ?: user.username,
        email = user.email,
        token = token,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis,
    )

    private fun requireSuccessful(response: HttpResponse, operation: String) {
        if (response.statusCode in 200..299) return
        val errorCode = response.useBody(jsonParser::parseErrorCode)
        val message = when (response.statusCode) {
            401, 403 -> "Vercel rejected this personal token."
            404 -> "Vercel could not find the requested resource."
            408 -> "Vercel timed out while trying to $operation."
            429 -> "Vercel is rate limiting requests. Please try again shortly."
            in 500..599 -> "Vercel is temporarily unavailable."
            else -> "Unable to $operation (HTTP ${response.statusCode})."
        }
        throw VercelApiException(
            statusCode = response.statusCode,
            errorCode = errorCode,
            message = message,
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

    companion object {
        const val BASE_URL: String = "https://api.vercel.com/"
        private const val DEFAULT_PROJECT_LIMIT = 100
        private const val MAX_PROJECT_LIMIT = 100
    }
}
