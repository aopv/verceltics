package com.apoorvdarshan.verceltics.data.searchconsole

import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.network.HttpResponse
import com.apoorvdarshan.verceltics.data.network.map
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal interface SearchConsoleReadApi {
    fun newListVerifiedPropertiesCall(
        credential: SearchConsoleOAuthCredential,
    ): CancelableCall<SearchConsolePropertyList>

    fun newAnalyticsPageCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        query: SearchConsoleAnalyticsQuery,
    ): CancelableCall<SearchConsoleAnalyticsResponse>

    fun newListSitemapsCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        sitemapIndex: String? = null,
    ): CancelableCall<List<SearchConsoleSitemap>>

    fun newGetSitemapCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        feedPath: String,
    ): CancelableCall<SearchConsoleSitemap>

    fun newInspectUrlCall(
        credential: SearchConsoleOAuthCredential,
        inspectionUrl: String,
        siteUrl: String,
        languageCode: String = "en-US",
    ): CancelableCall<SearchConsoleUrlInspectionResult>
}

internal interface SearchConsoleOAuthApi {
    fun newExchangeAuthorizationCodeCall(
        authorizationCode: SecretValue,
        codeVerifier: SecretValue,
        clientId: String,
        redirectUri: String,
        requestedScopes: List<String>,
    ): CancelableCall<SearchConsoleOAuthCredential>

    fun newIdentityCall(
        credential: SearchConsoleOAuthCredential,
    ): CancelableCall<SearchConsoleGoogleIdentity>

    fun newRefreshCredentialCall(
        credential: SearchConsoleOAuthCredential,
        clientId: String,
    ): CancelableCall<SearchConsoleOAuthCredential>
}

internal class SearchConsoleApiException(
    val failure: SearchConsoleFailure,
    val googleReason: String? = null,
) : Exception(failure.message) {
    override fun toString(): String =
        "SearchConsoleApiException(failure=$failure, googleReason=" +
            "${if (googleReason == null) "null" else "<redacted>"})"
}

/** Native read-only Search Console surface matching the existing iOS detail APIs. */
internal class SearchConsoleApi(
    private val transport: SearchConsoleHttpTransport = SecureSearchConsoleHttpTransport(),
    private val parser: SearchConsoleJsonParser = AndroidSearchConsoleJsonParser(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SearchConsoleReadApi, SearchConsoleOAuthApi {
    override fun newListVerifiedPropertiesCall(
        credential: SearchConsoleOAuthCredential,
    ): CancelableCall<SearchConsolePropertyList> = newReadCall(
        credential,
        SearchConsoleHttpRequest(
            SearchConsoleGoogleOrigin.WEBMASTERS,
            "GET",
            listOf("webmasters", "v3", "sites"),
            bearerToken = credential.accessToken,
        ),
        "load Search Console properties",
        parser::parseProperties,
    ).map { parsed -> parsed.copy(properties = parsed.properties.filter(SearchConsoleProperty::isVerified)) }

    override fun newAnalyticsPageCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        query: SearchConsoleAnalyticsQuery,
    ): CancelableCall<SearchConsoleAnalyticsResponse> {
        val site = validatedSiteUrl(siteUrl)
        validateQuery(query)
        val body = SearchConsoleJsonBody.analyticsQuery(query)
        return try {
            newReadCall(
                credential,
                SearchConsoleHttpRequest(
                    SearchConsoleGoogleOrigin.WEBMASTERS,
                    "POST",
                    listOf("webmasters", "v3", "sites", site, "searchAnalytics", "query"),
                    requestBody = body,
                    contentType = JSON_CONTENT_TYPE,
                    bearerToken = credential.accessToken,
                ),
                "load Search Console analytics",
                parser::parseAnalytics,
            ).map { response ->
                if (response.rows.size > query.rowLimit) {
                    throw SearchConsoleResponseFormatException(
                        "Google returned more Search Analytics rows than requested.",
                    )
                }
                response
            }
        } finally {
            body.fill(0)
        }
    }

    override fun newListSitemapsCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        sitemapIndex: String?,
    ): CancelableCall<List<SearchConsoleSitemap>> {
        val site = validatedSiteUrl(siteUrl)
        val query = sitemapIndex?.let {
            listOf("sitemapIndex" to validatedHttpUrl(it, "Sitemap index"))
        }.orEmpty()
        return newReadCall(
            credential,
            SearchConsoleHttpRequest(
                SearchConsoleGoogleOrigin.WEBMASTERS,
                "GET",
                listOf("webmasters", "v3", "sites", site, "sitemaps"),
                queryParameters = query,
                bearerToken = credential.accessToken,
            ),
            "load Search Console sitemaps",
            parser::parseSitemaps,
        )
    }

    override fun newGetSitemapCall(
        credential: SearchConsoleOAuthCredential,
        siteUrl: String,
        feedPath: String,
    ): CancelableCall<SearchConsoleSitemap> {
        val site = validatedSiteUrl(siteUrl)
        val sitemap = validatedHttpUrl(feedPath, "Sitemap URL")
        return newReadCall(
            credential,
            SearchConsoleHttpRequest(
                SearchConsoleGoogleOrigin.WEBMASTERS,
                "GET",
                listOf("webmasters", "v3", "sites", site, "sitemaps", sitemap),
                bearerToken = credential.accessToken,
            ),
            "load the Search Console sitemap",
            parser::parseSitemap,
        )
    }

    override fun newInspectUrlCall(
        credential: SearchConsoleOAuthCredential,
        inspectionUrl: String,
        siteUrl: String,
        languageCode: String,
    ): CancelableCall<SearchConsoleUrlInspectionResult> {
        val inspected = validatedHttpUrl(inspectionUrl, "Inspection URL")
        val site = validatedSiteUrl(siteUrl)
        val language = languageCode.trim()
        require(language.length in 2..35 && BCP_47.matches(language)) {
            "Language code must be a valid BCP-47 language tag."
        }
        val body = SearchConsoleJsonBody.inspection(inspected, site, language)
        return try {
            newReadCall(
                credential,
                SearchConsoleHttpRequest(
                    SearchConsoleGoogleOrigin.INSPECTION,
                    "POST",
                    listOf("v1", "urlInspection", "index:inspect"),
                    literalColonInLastSegment = true,
                    requestBody = body,
                    contentType = JSON_CONTENT_TYPE,
                    bearerToken = credential.accessToken,
                ),
                "inspect the Search Console URL",
                parser::parseInspection,
            )
        } finally {
            body.fill(0)
        }
    }

    override fun newExchangeAuthorizationCodeCall(
        authorizationCode: SecretValue,
        codeVerifier: SecretValue,
        clientId: String,
        redirectUri: String,
        requestedScopes: List<String>,
    ): CancelableCall<SearchConsoleOAuthCredential> {
        val safeClientId = validatedClientId(clientId)
        val safeRedirectUri = validatedRedirectUri(redirectUri)
        require(requestedScopes == requestedScopes.distinct() &&
            requestedScopes.all { it.isNotBlank() && it.length <= MAX_SCOPE_CHARACTERS }) {
            "Invalid requested Google OAuth scopes."
        }
        require(SearchConsoleOAuthCredential.READ_ONLY_SCOPE in requestedScopes) {
            "Search Console read-only scope is required."
        }
        val body = authorizationCode.use { code ->
            codeVerifier.use { verifier ->
                formBody(
                    listOf(
                        "client_id" to safeClientId,
                        "code" to code,
                        "code_verifier" to verifier,
                        "grant_type" to "authorization_code",
                        "redirect_uri" to safeRedirectUri,
                    ),
                )
            }
        }
        val request = SearchConsoleHttpRequest(
            SearchConsoleGoogleOrigin.OAUTH_TOKEN,
            "POST",
            listOf("token"),
            requestBody = body,
            contentType = FORM_CONTENT_TYPE,
        )
        body.fill(0)
        return transport.newCall(request).map { response ->
            requireSuccessful(response, "exchange the Google authorization code")
            val tokenResponse = response.useBody(parser::parseTokenResponse)
            val credential = SearchConsoleOAuthCredential(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                tokenType = tokenResponse.tokenType ?: "Bearer",
                scopes = tokenResponse.scopes ?: requestedScopes,
                expiresAtMillis = safeExpiry(nowMillis(), tokenResponse.expiresInSeconds),
                subject = null,
                email = null,
            )
            if (!credential.hasReadOnlyScope()) throw SearchConsoleApiException(
                SearchConsoleFailure(
                    SearchConsoleFailureKind.AUTHORIZATION,
                    "Google did not grant Search Console read access.",
                ),
            )
            if (credential.refreshToken == null) throw SearchConsoleApiException(
                SearchConsoleFailure(
                    SearchConsoleFailureKind.CONFIGURATION,
                    "Google did not return offline access. Reconnect and grant consent.",
                ),
            )
            credential
        }
    }

    override fun newIdentityCall(
        credential: SearchConsoleOAuthCredential,
    ): CancelableCall<SearchConsoleGoogleIdentity> {
        validateReadCredential(credential)
        return transport.newCall(
            SearchConsoleHttpRequest(
                SearchConsoleGoogleOrigin.OPENID,
                "GET",
                listOf("v1", "userinfo"),
                bearerToken = credential.accessToken,
            ),
        ).map { response ->
            requireSuccessful(response, "load the Google account identity")
            response.useBody(parser::parseIdentity)
        }
    }

    override fun newRefreshCredentialCall(
        credential: SearchConsoleOAuthCredential,
        clientId: String,
    ): CancelableCall<SearchConsoleOAuthCredential> {
        val safeClientId = validatedClientId(clientId)
        val refreshToken = credential.refreshToken ?: throw SearchConsoleApiException(
            SearchConsoleFailure(
                SearchConsoleFailureKind.CONFIGURATION,
                "The saved Google credential has no refresh token. Reconnect the account.",
            ),
        )
        val body = refreshToken.use { token ->
            formBody(
                listOf(
                    "client_id" to safeClientId,
                    "refresh_token" to token,
                    "grant_type" to "refresh_token",
                ),
            )
        }
        val request = SearchConsoleHttpRequest(
            SearchConsoleGoogleOrigin.OAUTH_TOKEN,
            "POST",
            listOf("token"),
            requestBody = body,
            contentType = FORM_CONTENT_TYPE,
        )
        body.fill(0)
        return transport.newCall(request).map { response ->
            requireSuccessful(response, "refresh the Google credential")
            val tokenResponse = response.useBody(parser::parseTokenResponse)
            SearchConsoleOAuthCredential(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken ?: refreshToken,
                tokenType = tokenResponse.tokenType ?: credential.tokenType,
                scopes = tokenResponse.scopes ?: credential.scopes,
                expiresAtMillis = safeExpiry(nowMillis(), tokenResponse.expiresInSeconds),
                subject = credential.subject,
                email = credential.email,
            )
        }
    }

    private fun <T> newReadCall(
        credential: SearchConsoleOAuthCredential,
        request: SearchConsoleHttpRequest,
        operation: String,
        parse: (ByteArray) -> T,
    ): CancelableCall<T> {
        validateReadCredential(credential)
        return transport.newCall(request).map { response ->
            requireSuccessful(response, operation)
            response.useBody(parse)
        }
    }

    private fun validateReadCredential(credential: SearchConsoleOAuthCredential) {
        if (!credential.hasReadOnlyScope()) throw SearchConsoleApiException(
            SearchConsoleFailure(
                SearchConsoleFailureKind.AUTHORIZATION,
                "The Google credential does not grant Search Console read access.",
            ),
        )
        if (credential.needsRefresh(nowMillis())) throw SearchConsoleApiException(
            SearchConsoleFailure(
                SearchConsoleFailureKind.EXPIRED_CREDENTIAL,
                "The Google access token has expired. Refresh or reconnect the account.",
            ),
        )
    }

    private fun requireSuccessful(response: HttpResponse, operation: String) {
        if (response.statusCode in 200..299) return
        val reason = response.useBody { bytes ->
            runCatching { parser.parseErrorReason(bytes) }.getOrNull()?.takeIf(SAFE_REASON::matches)
        }
        val failure = when (response.statusCode) {
            400 -> SearchConsoleFailure(
                SearchConsoleFailureKind.INVALID_REQUEST,
                "Google rejected the Search Console request.",
                400,
            )
            401 -> SearchConsoleFailure(
                SearchConsoleFailureKind.AUTHENTICATION,
                "Google rejected this access token. Refresh or reconnect the account.",
                401,
            )
            403 -> SearchConsoleFailure(
                SearchConsoleFailureKind.AUTHORIZATION,
                "This Google account is not allowed to read the requested Search Console resource.",
                403,
            )
            404 -> SearchConsoleFailure(
                SearchConsoleFailureKind.NOT_FOUND,
                "Google could not find the requested Search Console resource.",
                404,
            )
            408 -> SearchConsoleFailure(
                SearchConsoleFailureKind.NETWORK,
                "Google timed out while trying to $operation.",
                408,
            )
            429 -> SearchConsoleFailure(
                SearchConsoleFailureKind.RATE_LIMITED,
                "Google is rate limiting Search Console requests. Try again shortly.",
                429,
            )
            in 500..599 -> SearchConsoleFailure(
                SearchConsoleFailureKind.TEMPORARY,
                "Google Search Console is temporarily unavailable.",
                response.statusCode,
            )
            else -> SearchConsoleFailure(
                SearchConsoleFailureKind.TEMPORARY,
                "Unable to $operation (HTTP ${response.statusCode}).",
                response.statusCode,
            )
        }
        throw SearchConsoleApiException(failure, reason)
    }

    private inline fun <T> HttpResponse.useBody(block: (ByteArray) -> T): T {
        val body = takeBody()
        return try {
            block(body)
        } finally {
            body.fill(0)
        }
    }

    companion object {
        const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        const val MAXIMUM_ALL_ROWS = MAX_ANALYTICS_AGGREGATE_ROWS
        private const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"
        private const val FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8"
        private val BCP_47 = Regex("[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*")
        private val OAUTH_CLIENT_ID = Regex("[A-Za-z0-9._:-]+")
        private val SAFE_REASON = Regex("[A-Za-z0-9_.:-]{1,256}")

        fun validateQuery(query: SearchConsoleAnalyticsQuery) {
            val start = parseGoogleDate(query.dateRange.startDate)
            val end = parseGoogleDate(query.dateRange.endDate)
            require(!start.isAfter(end)) { "Search Analytics start date must not be after the end date." }
            require(query.rowLimit in 1..MAX_ANALYTICS_ROWS_PER_PAGE) {
                "Search Analytics row limit must be between 1 and 25,000."
            }
            require(query.startRow >= 0) { "Search Analytics start row must be zero or greater." }
            require(query.startRow <= Int.MAX_VALUE - query.rowLimit) {
                "Search Analytics start row is too large."
            }
            require(query.dimensions.distinct().size == query.dimensions.size) {
                "A Search Analytics dimension cannot be selected more than once."
            }
            require(query.dimensionFilterGroups.size <= 32) { "Too many Search Analytics filter groups." }
            require(query.dimensionFilterGroups.all { it.filters.isNotEmpty() && it.filters.size <= 32 }) {
                "Search Analytics filter groups cannot be empty or unbounded."
            }
            val filters = query.dimensionFilterGroups.flatMap { it.filters }
            require(filters.all { it.expression.isNotEmpty() && it.expression.length <= 4_096 }) {
                "Every Search Analytics filter needs an expression of at most 4,096 characters."
            }
            require(SearchConsoleDimension.HOUR !in query.dimensions || query.dataState == SearchConsoleDataState.HOURLY_ALL) {
                "Hourly results require the hourly_all data state."
            }
            val usesPage = SearchConsoleDimension.PAGE in query.dimensions ||
                filters.any { it.dimension == SearchConsoleFilterDimension.PAGE }
            require(!(usesPage && query.aggregationType == SearchConsoleAggregationType.BY_PROPERTY)) {
                "Page grouping or filtering cannot use by-property aggregation."
            }
            if (query.aggregationType == SearchConsoleAggregationType.BY_NEWS_SHOWCASE_PANEL) {
                val supportedType = query.searchType == SearchConsoleSearchType.DISCOVER ||
                    query.searchType == SearchConsoleSearchType.GOOGLE_NEWS
                val filtersShowcase = filters.any {
                    it.dimension == SearchConsoleFilterDimension.SEARCH_APPEARANCE &&
                        it.expression == "NEWS_SHOWCASE"
                }
                require(supportedType && filtersShowcase && !usesPage) {
                    "News Showcase aggregation needs Discover or Google News, its appearance filter, and no page grouping."
                }
            }
        }

        private fun parseGoogleDate(value: String): LocalDate = try {
            require(DATE_FORMAT.matches(value)) { "Search Analytics dates must use YYYY-MM-DD format." }
            LocalDate.parse(value)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException("Search Analytics dates must use YYYY-MM-DD format.", error)
        }

        private val DATE_FORMAT = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

        internal fun validatedSiteUrl(value: String): String {
            val site = value.trim()
            require(site.isNotEmpty() && '\r' !in site && '\n' !in site) {
                "Enter a Search Console property URL."
            }
            if (site.startsWith("sc-domain:")) {
                val domain = site.removePrefix("sc-domain:")
                require(domain.isNotEmpty() && domain.none { it == '/' || it == ':' || it.isWhitespace() }) {
                    "Enter a valid domain property."
                }
                return site
            }
            return validatedHttpUrl(site, "Search Console property")
        }

        internal fun validatedHttpUrl(value: String, label: String): String {
            val normalized = value.trim()
            val uri = runCatching { URI(normalized) }.getOrNull()
            require(
                uri != null && (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) &&
                    !uri.host.isNullOrBlank() && uri.userInfo == null &&
                    '\r' !in normalized && '\n' !in normalized,
            ) { "$label must be a fully-qualified HTTP or HTTPS URL." }
            return normalized
        }

        private fun formBody(values: List<Pair<String, String>>): ByteArray = values.joinToString("&") {
            "${formEncode(it.first)}=${formEncode(it.second)}"
        }.toByteArray(StandardCharsets.UTF_8)

        private fun formEncode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun validatedClientId(value: String): String = value.trim().also { clientId ->
            require(clientId.isNotEmpty() && clientId.length <= 2_048 && OAUTH_CLIENT_ID.matches(clientId)) {
                "Invalid Google OAuth client id."
            }
        }

        private fun validatedRedirectUri(value: String): String = value.trim().also { redirect ->
            val parsed = runCatching { URI(redirect) }.getOrNull()
            require(
                parsed != null && parsed.scheme?.isNotBlank() == true && parsed.rawQuery == null &&
                    parsed.rawFragment == null && parsed.userInfo == null &&
                    parsed.path == "/oauthredirect" && '\r' !in redirect && '\n' !in redirect,
            ) { "Invalid Google OAuth redirect URI." }
        }

        private fun safeExpiry(now: Long, seconds: Long): Long {
            val millis = Math.multiplyExact(seconds, 1_000L)
            return Math.addExact(now, millis)
        }
    }
}

private object SearchConsoleJsonBody {
    fun analyticsQuery(query: SearchConsoleAnalyticsQuery): ByteArray = buildString {
        append('{')
        field("startDate", query.dateRange.startDate)
        append(',')
        field("endDate", query.dateRange.endDate)
        if (query.dimensions.isNotEmpty()) {
            append(",\"dimensions\":[")
            query.dimensions.forEachIndexed { index, dimension ->
                if (index > 0) append(',')
                quoted(dimension.wireValue)
            }
            append(']')
        }
        append(',')
        field("type", query.searchType.wireValue)
        if (query.dimensionFilterGroups.isNotEmpty()) {
            append(",\"dimensionFilterGroups\":[")
            query.dimensionFilterGroups.forEachIndexed { groupIndex, group ->
                if (groupIndex > 0) append(',')
                append("{\"groupType\":\"and\",\"filters\":[")
                group.filters.forEachIndexed { filterIndex, filter ->
                    if (filterIndex > 0) append(',')
                    append('{')
                    field("dimension", filter.dimension.wireValue)
                    append(',')
                    field("operator", filter.operator.wireValue)
                    append(',')
                    field("expression", filter.expression)
                    append('}')
                }
                append("]}")
            }
            append(']')
        }
        append(',')
        field("aggregationType", query.aggregationType.wireValue)
        append(",\"rowLimit\":${query.rowLimit},\"startRow\":${query.startRow},")
        field("dataState", query.dataState.wireValue)
        append('}')
    }.toByteArray(StandardCharsets.UTF_8)

    fun inspection(inspectionUrl: String, siteUrl: String, language: String): ByteArray = buildString {
        append('{')
        field("inspectionUrl", inspectionUrl)
        append(',')
        field("siteUrl", siteUrl)
        append(',')
        field("languageCode", language)
        append('}')
    }.toByteArray(StandardCharsets.UTF_8)

    private fun StringBuilder.field(name: String, value: String) {
        quoted(name)
        append(':')
        quoted(value)
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
