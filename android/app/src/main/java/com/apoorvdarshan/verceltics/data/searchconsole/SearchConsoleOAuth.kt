package com.apoorvdarshan.verceltics.data.searchconsole

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.apoorvdarshan.verceltics.BuildConfig
import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

data class SearchConsoleOAuthClientConfiguration(
    val clientId: String,
    val redirectScheme: String,
) {
    init {
        require(CLIENT_ID.matches(clientId)) { "Invalid Google OAuth client id." }
        require(URI_SCHEME.matches(redirectScheme)) { "Invalid Google OAuth redirect scheme." }
        require(clientId.endsWith(GOOGLE_CLIENT_ID_SUFFIX)) { "Invalid Google OAuth client id." }
        require(redirectScheme == expectedRedirectScheme(clientId)) {
            "The Google OAuth redirect scheme must match the client id."
        }
    }

    val redirectUri: String get() = "$redirectScheme:/oauthredirect"

    companion object {
        private val CLIENT_ID = Regex("[A-Za-z0-9._:-]{1,2048}")
        private val URI_SCHEME = Regex("[A-Za-z][A-Za-z0-9+.-]{1,255}")
        private const val GOOGLE_CLIENT_ID_SUFFIX = ".apps.googleusercontent.com"

        private fun expectedRedirectScheme(clientId: String): String =
            "com.googleusercontent.apps.${clientId.removeSuffix(GOOGLE_CLIENT_ID_SUFFIX)}"

        fun current(): SearchConsoleOAuthClientConfiguration? {
            val clientId = BuildConfig.GOOGLE_OAUTH_CLIENT_ID.trim()
            val redirectScheme = BuildConfig.GOOGLE_OAUTH_REDIRECT_SCHEME.trim()
            if (clientId.isEmpty() || redirectScheme.isEmpty() ||
                redirectScheme == "verceltics-oauth-unconfigured"
            ) {
                return null
            }
            return runCatching { SearchConsoleOAuthClientConfiguration(clientId, redirectScheme) }
                .getOrNull()
        }
    }
}

internal class SearchConsoleOAuthException(message: String) : Exception(message) {
    override fun toString(): String = "SearchConsoleOAuthException(message=$message)"
}

/**
 * One-use PKCE transaction. The verifier and expected state stay private and are never copied
 * into Compose state, saved state, intents, or logs.
 */
internal class SearchConsolePkceTransaction private constructor(
    val authorizationUri: URI,
    private val expectedState: SecretValue,
    internal val codeVerifier: SecretValue,
    private val redirectUri: URI,
) {
    private val consumed = AtomicBoolean(false)

    fun authorizationCode(callbackUri: URI): SecretValue {
        if (!consumed.compareAndSet(false, true)) {
            throw SearchConsoleOAuthException("This Google authorization callback was already handled.")
        }
        if (!callbackUri.scheme.equals(redirectUri.scheme, ignoreCase = true) ||
            callbackUri.path != redirectUri.path || callbackUri.host != redirectUri.host
        ) {
            throw SearchConsoleOAuthException("Google returned an invalid authorization callback.")
        }
        val values = decodeQuery(callbackUri.rawQuery)
        values["error"]?.takeIf(String::isNotBlank)?.let { providerError ->
            val detail = values["error_description"]?.takeIf(String::isNotBlank) ?: providerError
            throw SearchConsoleOAuthException(detail.take(300))
        }
        val returnedState = values["state"]?.takeIf(String::isNotBlank)
            ?: throw SearchConsoleOAuthException("Google authorization could not be verified.")
        val stateMatches = expectedState.use { expected ->
            val expectedBytes = expected.toByteArray(StandardCharsets.UTF_8)
            val returnedBytes = returnedState.toByteArray(StandardCharsets.UTF_8)
            try {
                MessageDigest.isEqual(expectedBytes, returnedBytes)
            } finally {
                expectedBytes.fill(0)
                returnedBytes.fill(0)
            }
        }
        if (!stateMatches) {
            throw SearchConsoleOAuthException("Google authorization could not be verified.")
        }
        return values["code"]
            ?.takeIf { it.isNotBlank() && it.length <= MAX_AUTHORIZATION_CODE_CHARACTERS }
            ?.let(SecretValue::of)
            ?: throw SearchConsoleOAuthException("Google did not return an authorization code.")
    }

    override fun toString(): String = "SearchConsolePkceTransaction(<redacted>)"

    companion object {
        private const val MAX_AUTHORIZATION_CODE_CHARACTERS = 16_384

        fun create(
            configuration: SearchConsoleOAuthClientConfiguration,
            randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
        ): SearchConsolePkceTransaction {
            val verifier = randomBytes(64).toBase64Url()
            val state = randomBytes(32).toBase64Url()
            val challengeBytes = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.UTF_8))
            val challenge = try {
                challengeBytes.toBase64Url()
            } finally {
                challengeBytes.fill(0)
            }
            val authorizationUri = URI(
                SearchConsoleApi.AUTHORIZATION_ENDPOINT + "?" + formQuery(
                    listOf(
                        "client_id" to configuration.clientId,
                        "redirect_uri" to configuration.redirectUri,
                        "response_type" to "code",
                        "scope" to SearchConsoleOAuthCredential.REQUIRED_SCOPES.joinToString(" "),
                        "access_type" to "offline",
                        "include_granted_scopes" to "true",
                        "prompt" to "consent",
                        "code_challenge" to challenge,
                        "code_challenge_method" to "S256",
                        "state" to state,
                    ),
                ),
            )
            return SearchConsolePkceTransaction(
                authorizationUri = authorizationUri,
                expectedState = SecretValue.of(state),
                codeVerifier = SecretValue.of(verifier),
                redirectUri = URI(configuration.redirectUri),
            )
        }

        private fun decodeQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrBlank()) return emptyMap()
            val result = linkedMapOf<String, String>()
            rawQuery.split('&').forEach { pair ->
                val separator = pair.indexOf('=')
                val rawName = if (separator < 0) pair else pair.substring(0, separator)
                val rawValue = if (separator < 0) "" else pair.substring(separator + 1)
                val name = URLDecoder.decode(rawName, StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
                if (name.isBlank() || result.put(name, value) != null) {
                    throw SearchConsoleOAuthException("Google returned an invalid authorization callback.")
                }
            }
            return result
        }
    }
}

internal interface SearchConsoleOAuthAuthorizer {
    val configuration: SearchConsoleOAuthClientConfiguration?

    suspend fun authorize(): SearchConsoleOAuthCredential

    suspend fun refresh(credential: SearchConsoleOAuthCredential): SearchConsoleOAuthCredential
}

/** Native browser + callback implementation. No credential material crosses the UI boundary. */
internal class NativeSearchConsoleOAuthAuthorizer(
    private val context: Context,
    override val configuration: SearchConsoleOAuthClientConfiguration? =
        SearchConsoleOAuthClientConfiguration.current(),
    private val api: SearchConsoleOAuthApi = SearchConsoleApi(),
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "verceltics-search-console-oauth").apply { isDaemon = true }
    },
) : SearchConsoleOAuthAuthorizer {
    override suspend fun authorize(): SearchConsoleOAuthCredential {
        val configured = configuration ?: throw SearchConsoleOAuthException(
            "Google OAuth is ready, but this Android build does not include its client configuration yet.",
        )
        val transaction = SearchConsolePkceTransaction.create(configured)
        val callback = SearchConsoleOAuthCallbackBroker.awaitCallback(
            context.applicationContext,
            transaction.authorizationUri,
            URI(configured.redirectUri),
        )
        val authorizationCode = transaction.authorizationCode(callback)
        val credential = api.newExchangeAuthorizationCodeCall(
            authorizationCode = authorizationCode,
            codeVerifier = transaction.codeVerifier,
            clientId = configured.clientId,
            redirectUri = configured.redirectUri,
            requestedScopes = SearchConsoleOAuthCredential.REQUIRED_SCOPES,
        ).executeAwait(executor)
        val identity = api.newIdentityCall(credential).executeAwait(executor)
        return SearchConsoleOAuthCredential(
            accessToken = credential.accessToken,
            refreshToken = credential.refreshToken,
            tokenType = credential.tokenType,
            scopes = credential.scopes,
            expiresAtMillis = credential.expiresAtMillis,
            subject = identity.subject,
            email = identity.email,
        )
    }

    override suspend fun refresh(
        credential: SearchConsoleOAuthCredential,
    ): SearchConsoleOAuthCredential {
        val configured = configuration ?: throw SearchConsoleOAuthException(
            "This saved Google credential needs refresh, but OAuth is not configured in this build.",
        )
        return api.newRefreshCredentialCall(credential, configured.clientId).executeAwait(executor)
    }
}

/** Process-local handoff from the exported callback activity to the active PKCE coroutine. */
internal object SearchConsoleOAuthCallbackBroker {
    private val lock = Any()
    private data class PendingCallback(
        val continuation: CancellableContinuation<URI>,
        val redirectUri: URI,
    )

    private var pending: PendingCallback? = null

    suspend fun awaitCallback(context: Context, authorizationUri: URI, redirectUri: URI): URI =
        suspendCancellableCoroutine { continuation ->
            synchronized(lock) {
                if (pending != null) {
                    continuation.resumeWithException(
                        SearchConsoleOAuthException("A Google authorization request is already open."),
                    )
                    return@suspendCancellableCoroutine
                }
                pending = PendingCallback(continuation, redirectUri)
            }
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    if (pending?.continuation === continuation) pending = null
                }
            }
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUri.toASCIIString())).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (error: Exception) {
                val claimed = synchronized(lock) {
                    (pending?.continuation === continuation).also { if (it) pending = null }
                }
                if (claimed && continuation.isActive) continuation.resumeWithException(
                    SearchConsoleOAuthException("No browser is available for Google authorization."),
                )
            }
        }

    fun deliver(callbackUri: URI): Boolean {
        val claimed = synchronized(lock) {
            val candidate = pending ?: return@synchronized null
            if (!callbackUri.matchesOAuthRedirect(candidate.redirectUri)) return@synchronized null
            pending = null
            candidate
        } ?: return false
        if (claimed.continuation.isActive) claimed.continuation.resume(callbackUri)
        return true
    }
}

internal fun URI.matchesOAuthRedirect(expected: URI): Boolean =
    scheme.equals(expected.scheme, ignoreCase = true) &&
        path == expected.path && host == expected.host &&
        userInfo == null && port == -1 && fragment == null

private suspend fun <T> CancelableCall<T>.executeAwait(executor: ExecutorService): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        executor.execute {
            try {
                val value = execute()
                if (continuation.isActive) continuation.resume(value)
            } catch (error: CancellationException) {
                if (continuation.isActive) continuation.cancel(error)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

private fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also(SecureRandom()::nextBytes)

private fun ByteArray.toBase64Url(): String = try {
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)
} finally {
    fill(0)
}

private fun formQuery(values: List<Pair<String, String>>): String = values.joinToString("&") {
    URLEncoder.encode(it.first, StandardCharsets.UTF_8.name()) + "=" +
        URLEncoder.encode(it.second, StandardCharsets.UTF_8.name())
}
