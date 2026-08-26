package com.apoorvdarshan.verceltics.data.pagespeed

import com.apoorvdarshan.verceltics.data.account.SecretValue
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * PageSpeed's non-OAuth credential pair.
 *
 * The API key is deliberately kept in [SecretValue], so interpolating this object cannot reveal it.
 * The site URL is a page being measured, never an HTTP endpoint controlled by the caller.
 */
class PageSpeedCredentials private constructor(
    val siteUrl: URI,
    internal val apiKey: SecretValue,
) {
    override fun toString(): String =
        "PageSpeedCredentials(siteUrl=$siteUrl, apiKey=<redacted>)"

    companion object {
        fun create(apiKey: String, siteUrl: String): PageSpeedCredentials {
            val normalizedKey = apiKey.trim()
            require(normalizedKey.length <= MAX_API_KEY_CHARACTERS) {
                "The Google API key is too long."
            }
            return PageSpeedCredentials(
                siteUrl = normalizeSiteUrl(siteUrl),
                apiKey = SecretValue.of(normalizedKey),
            )
        }

        internal fun restored(apiKey: SecretValue, siteUrl: URI): PageSpeedCredentials =
            PageSpeedCredentials(
                siteUrl = normalizeSiteUrl(siteUrl.toASCIIString()),
                apiKey = apiKey,
            )

        /** Mirrors iOS PageSpeed URL identity: HTTPS only, lowercase origin, no credentials/fragment. */
        fun normalizeSiteUrl(rawValue: String): URI {
            val value = rawValue.trim()
            require(value.isNotEmpty() && value.length <= MAX_SITE_URL_CHARACTERS) {
                "Enter a complete HTTPS site URL."
            }
            require(value.none { it == '\r' || it == '\n' || it == '\u0000' }) {
                "Enter a complete HTTPS site URL."
            }
            val parsed = try {
                URI(value)
            } catch (_: URISyntaxException) {
                throw IllegalArgumentException("Enter a complete HTTPS site URL.")
            }
            require(!parsed.isOpaque && parsed.scheme.equals("https", ignoreCase = true)) {
                "Enter a complete HTTPS site URL."
            }
            require(!parsed.host.isNullOrBlank() && parsed.userInfo == null) {
                "Enter a complete HTTPS site URL."
            }
            require(parsed.port == -1 || parsed.port in 1..65_535) {
                "Enter a complete HTTPS site URL."
            }

            val normalizedHost = parsed.host.lowercase(Locale.ROOT).let { host ->
                if (':' in host) "[$host]" else host
            }
            val normalizedPort = parsed.port.takeIf { it != -1 && it != 443 }
                ?.let { ":$it" }
                .orEmpty()
            val normalized = URI(
                buildString {
                    append("https://")
                    append(normalizedHost)
                    append(normalizedPort)
                    append(parsed.rawPath.orEmpty())
                    parsed.rawQuery?.let {
                        append('?')
                        append(it)
                    }
                },
            ).normalize()
            require(normalized.rawPath.orEmpty().split('/').none { it == ".." }) {
                "Enter a complete HTTPS site URL."
            }
            return normalized
        }

        private const val MAX_SITE_URL_CHARACTERS = 4_096
        private const val MAX_API_KEY_CHARACTERS = 4_096
    }
}
