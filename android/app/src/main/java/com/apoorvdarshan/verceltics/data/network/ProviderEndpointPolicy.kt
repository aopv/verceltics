package com.apoorvdarshan.verceltics.data.network

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Resolves provider-relative paths while preventing authority changes and credential injection. */
class ProviderEndpointPolicy(baseUrl: String) {
    val baseUri: URI = URI(baseUrl).normalize().also(::validateBaseUri)

    fun resolve(
        relativePath: String,
        queryParameters: List<Pair<String, String>> = emptyList(),
    ): URI {
        require(relativePath.isNotBlank()) { "A provider-relative path is required." }
        require(relativePath.length <= MAX_PATH_CHARACTERS) { "Provider path is too long." }
        require('\\' !in relativePath) { "Backslashes are not allowed in provider paths." }
        require('?' !in relativePath && '#' !in relativePath) {
            "Query parameters and fragments must not be embedded in provider paths."
        }

        val candidate = URI(relativePath)
        require(!candidate.isAbsolute && candidate.rawAuthority == null && candidate.userInfo == null) {
            "Only provider-relative paths are allowed."
        }
        val rawPath = candidate.rawPath.orEmpty()
        require('%' !in rawPath) { "Encoded provider path components are not allowed." }
        require(candidate.path.split('/').none { it == "." || it == ".." }) {
            "Provider path traversal is not allowed."
        }

        val normalizedPath = "/" + candidate.path.orEmpty().trimStart('/')
        val query = encodeQuery(queryParameters)
        val resolvedWithoutQuery = URI(
            baseUri.scheme,
            null,
            baseUri.host,
            baseUri.port,
            normalizedPath,
            null,
            null,
        )
        val resolved = if (query == null) {
            resolvedWithoutQuery
        } else {
            URI(resolvedWithoutQuery.toASCIIString() + "?" + query)
        }
        check(isSameOrigin(resolved)) { "Provider path escaped the configured origin." }
        return resolved
    }

    fun resolveRedirect(current: URI, location: String): URI {
        require(location.isNotBlank() && location.length <= MAX_REDIRECT_CHARACTERS) {
            "Invalid redirect location."
        }
        val locationUri = URI(location)
        require(locationUri.userInfo == null && locationUri.fragment == null) {
            "Redirect credentials and fragments are not allowed."
        }
        val target = current.resolve(locationUri).normalize()
        require(target.scheme.equals("https", ignoreCase = true)) {
            "Redirects must remain on HTTPS."
        }
        require(target.userInfo == null && isSameOrigin(target)) {
            "Cross-origin provider redirects are not allowed."
        }
        return target
    }

    fun isSameOrigin(uri: URI): Boolean =
        uri.scheme.equals(baseUri.scheme, ignoreCase = true) &&
            (uri.host?.equals(baseUri.host, ignoreCase = true) == true) &&
            effectivePort(uri) == effectivePort(baseUri) &&
            uri.userInfo == null

    fun validateUnprivilegedHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            require(name.isNotBlank() && HEADER_NAME.matches(name)) { "Invalid HTTP header name." }
            require(name.lowercase(Locale.ROOT) !in PROTECTED_HEADERS) {
                "The $name header is controlled by the secure HTTP client."
            }
            require(value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Invalid HTTP header value."
            }
            require(value.length <= MAX_HEADER_VALUE_CHARACTERS) { "HTTP header value is too long." }
        }
    }

    private fun validateBaseUri(uri: URI) {
        require(uri.scheme.equals("https", ignoreCase = true)) { "Provider base URL must use HTTPS." }
        require(!uri.host.isNullOrBlank()) { "Provider base URL must include a host." }
        require(uri.userInfo == null) { "Provider base URL cannot include user information." }
        require(uri.query == null && uri.fragment == null) {
            "Provider base URL cannot include a query or fragment."
        }
    }

    private fun encodeQuery(parameters: List<Pair<String, String>>): String? {
        if (parameters.isEmpty()) return null
        require(parameters.size <= MAX_QUERY_PARAMETERS) { "Too many query parameters." }
        return parameters.joinToString("&") { (name, value) ->
            require(name.isNotBlank() && name.length <= MAX_QUERY_COMPONENT_CHARACTERS) {
                "Invalid query parameter name."
            }
            require(value.length <= MAX_QUERY_COMPONENT_CHARACTERS) {
                "Query parameter value is too long."
            }
            "${encodeQueryComponent(name)}=${encodeQueryComponent(value)}"
        }
    }

    private fun encodeQueryComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        else -> -1
    }

    companion object {
        private val HEADER_NAME = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
        private val PROTECTED_HEADERS = setOf("authorization", "host")
        private const val MAX_PATH_CHARACTERS = 4_096
        private const val MAX_REDIRECT_CHARACTERS = 8_192
        private const val MAX_QUERY_PARAMETERS = 64
        private const val MAX_QUERY_COMPONENT_CHARACTERS = 4_096
        private const val MAX_HEADER_VALUE_CHARACTERS = 8_192
    }
}
