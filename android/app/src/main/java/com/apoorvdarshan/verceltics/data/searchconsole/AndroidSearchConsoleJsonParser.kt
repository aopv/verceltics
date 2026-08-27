package com.apoorvdarshan.verceltics.data.searchconsole

import android.util.JsonReader
import android.util.JsonToken
import com.apoorvdarshan.verceltics.data.account.SecretValue
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

internal data class SearchConsoleTokenResponse(
    val accessToken: SecretValue,
    val refreshToken: SecretValue?,
    val tokenType: String?,
    val scopes: List<String>?,
    val expiresInSeconds: Long,
) {
    override fun toString(): String =
        "SearchConsoleTokenResponse(accessToken=<redacted>, refreshToken=" +
            "${if (refreshToken == null) "null" else "<redacted>"}, tokenType=$tokenType, " +
            "scopeCount=${scopes?.size}, expiresInSeconds=$expiresInSeconds)"
}

internal interface SearchConsoleJsonParser {
    fun parseProperties(bytes: ByteArray): SearchConsolePropertyList
    fun parseAnalytics(bytes: ByteArray): SearchConsoleAnalyticsResponse
    fun parseSitemaps(bytes: ByteArray): List<SearchConsoleSitemap>
    fun parseSitemap(bytes: ByteArray): SearchConsoleSitemap
    fun parseInspection(bytes: ByteArray): SearchConsoleUrlInspectionResult
    fun parseTokenResponse(bytes: ByteArray): SearchConsoleTokenResponse
    fun parseErrorReason(bytes: ByteArray): String?
}

internal class SearchConsoleResponseFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Direct streaming parser for bounded Google responses; no unbounded JSON object tree is built. */
internal class AndroidSearchConsoleJsonParser : SearchConsoleJsonParser {
    override fun parseProperties(bytes: ByteArray): SearchConsolePropertyList = parse(bytes) { reader ->
        var foundList = false
        var rootFields = 0
        var skipped = 0
        val properties = mutableListOf<SearchConsoleProperty>()
        reader.readObject { name ->
            rootFields += 1
            if (name == "siteEntry") {
                foundList = true
                reader.readArray(MAX_PROPERTIES_PER_RESPONSE) {
                    val property = readProperty(reader)
                    if (property == null) skipped += 1 else properties += property
                }
            } else {
                reader.skipValue()
            }
        }
        if (!foundList && rootFields > 0) {
            throw SearchConsoleResponseFormatException(
                "Search Console did not return a siteEntry list.",
            )
        }
        if (!foundList) {
            // Google returns {} for an account with no properties.
            SearchConsolePropertyList(emptyList())
        } else {
            SearchConsolePropertyList(properties.distinctBy { it.siteUrl }, skipped)
        }
    }

    override fun parseAnalytics(bytes: ByteArray): SearchConsoleAnalyticsResponse = parse(bytes) { reader ->
        val rows = mutableListOf<SearchConsoleAnalyticsRow>()
        var aggregation: String? = null
        var metadata: SearchConsoleAnalyticsMetadata? = null
        reader.readObject { name ->
            when (name) {
                "rows" -> reader.readArray(MAX_ANALYTICS_ROWS_PER_PAGE) {
                    rows += readAnalyticsRow(reader)
                }
                "responseAggregationType" -> aggregation = reader.optionalString(MAX_STATUS_CHARACTERS)
                "metadata" -> metadata = readAnalyticsMetadata(reader)
                else -> reader.skipValue()
            }
        }
        SearchConsoleAnalyticsResponse(rows, aggregation, metadata)
    }

    override fun parseSitemaps(bytes: ByteArray): List<SearchConsoleSitemap> = parse(bytes) { reader ->
        val sitemaps = mutableListOf<SearchConsoleSitemap>()
        reader.readObject { name ->
            if (name == "sitemap") {
                reader.readArray(MAX_SITEMAPS) { sitemaps += readSitemap(reader) }
            } else {
                reader.skipValue()
            }
        }
        sitemaps.distinctBy(SearchConsoleSitemap::path)
    }

    override fun parseSitemap(bytes: ByteArray): SearchConsoleSitemap = parse(bytes, ::readSitemap)

    override fun parseInspection(bytes: ByteArray): SearchConsoleUrlInspectionResult = parse(bytes) { reader ->
        var result: SearchConsoleUrlInspectionResult? = null
        val budget = InspectionBudget()
        reader.readObject { name ->
            if (name == "inspectionResult") {
                result = readInspectionResult(reader, budget)
            } else {
                reader.skipValue()
            }
        }
        result ?: throw SearchConsoleResponseFormatException(
            "Search Console did not return an inspection result.",
        )
    }

    override fun parseTokenResponse(bytes: ByteArray): SearchConsoleTokenResponse = parse(bytes) { reader ->
        var accessToken: SecretValue? = null
        var refreshToken: SecretValue? = null
        var tokenType: String? = null
        var scopes: List<String>? = null
        var expiresIn: Long? = null
        reader.readObject { name ->
            when (name) {
                "access_token" -> accessToken = reader.optionalString(MAX_SECRET_CHARACTERS)?.let(SecretValue::of)
                "refresh_token" -> refreshToken = reader.optionalString(MAX_SECRET_CHARACTERS)?.let(SecretValue::of)
                "token_type" -> tokenType = reader.optionalString(64)
                "scope" -> scopes = reader.optionalString(MAX_SCOPE_LIST_CHARACTERS)
                    ?.split(' ')
                    ?.filter(String::isNotBlank)
                    ?.also {
                        if (it.size > MAX_SCOPES) {
                            throw SearchConsoleResponseFormatException("Google returned too many OAuth scopes.")
                        }
                    }
                    ?.distinct()
                "expires_in" -> expiresIn = reader.flexibleLong()
                else -> reader.skipValue()
            }
        }
        SearchConsoleTokenResponse(
            accessToken ?: throw SearchConsoleResponseFormatException("Google omitted the access token."),
            refreshToken,
            tokenType,
            scopes,
            expiresIn?.takeIf { it in 1..MAX_TOKEN_LIFETIME_SECONDS }
                ?: throw SearchConsoleResponseFormatException("Google returned an invalid token lifetime."),
        )
    }

    override fun parseErrorReason(bytes: ByteArray): String? = parse(bytes) { reader ->
        var reason: String? = null
        reader.readObject { name ->
            if (name == "error") {
                when (reader.peek()) {
                    JsonToken.STRING -> reason = reader.optionalString(256)
                    JsonToken.BEGIN_OBJECT -> reader.readObject errorObject@{ errorName ->
                        when (errorName) {
                            "status" -> reason = reader.optionalString(256)
                            "errors" -> reader.readArray(16) {
                                if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                                    reader.readObject { detailName ->
                                        if (detailName == "reason" && reason == null) {
                                            reason = reader.optionalString(256)
                                        } else {
                                            reader.skipValue()
                                        }
                                    }
                                } else {
                                    reader.skipValue()
                                }
                            }
                            else -> reader.skipValue()
                        }
                    }
                    else -> reader.skipValue()
                }
            } else {
                reader.skipValue()
            }
        }
        reason
    }

    private fun readProperty(reader: JsonReader): SearchConsoleProperty? {
        var siteUrl: String? = null
        var permission: String? = null
        reader.readObject { name ->
            when (name) {
                "siteUrl" -> siteUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "permissionLevel" -> permission = reader.optionalString(MAX_STATUS_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        val site = siteUrl?.takeIf(String::isNotBlank) ?: return null
        val level = permission?.takeIf(String::isNotBlank) ?: "siteUnverifiedUser"
        return runCatching { SearchConsoleProperty(site, level) }.getOrNull()
    }

    private fun readAnalyticsRow(reader: JsonReader): SearchConsoleAnalyticsRow {
        var keys = emptyList<String>()
        var clicks: Double? = null
        var impressions: Double? = null
        var ctr: Double? = null
        var position: Double? = null
        reader.readObject { name ->
            when (name) {
                "keys" -> keys = reader.stringArray(MAX_ANALYTICS_KEYS, MAX_URL_CHARACTERS)
                "clicks" -> clicks = reader.finiteDouble()
                "impressions" -> impressions = reader.finiteDouble()
                "ctr" -> ctr = reader.finiteDouble()
                "position" -> position = reader.finiteDouble()
                else -> reader.skipValue()
            }
        }
        return SearchConsoleAnalyticsRow(
            keys,
            clicks ?: throw SearchConsoleResponseFormatException("Analytics row omitted clicks."),
            impressions ?: throw SearchConsoleResponseFormatException("Analytics row omitted impressions."),
            ctr ?: throw SearchConsoleResponseFormatException("Analytics row omitted CTR."),
            position ?: throw SearchConsoleResponseFormatException("Analytics row omitted position."),
        )
    }

    private fun readAnalyticsMetadata(reader: JsonReader): SearchConsoleAnalyticsMetadata {
        var date: String? = null
        var hour: String? = null
        reader.readObject { name ->
            when (name) {
                "first_incomplete_date" -> date = reader.optionalString(32)
                "first_incomplete_hour" -> hour = reader.optionalString(64)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleAnalyticsMetadata(date, hour)
    }

    private fun readSitemap(reader: JsonReader): SearchConsoleSitemap {
        var path: String? = null
        var lastSubmitted: String? = null
        var pending = false
        var index = false
        var type: String? = null
        var lastDownloaded: String? = null
        var warnings = 0L
        var errors = 0L
        var contents = emptyList<SearchConsoleSitemapContent>()
        reader.readObject { name ->
            when (name) {
                "path" -> path = reader.optionalString(MAX_URL_CHARACTERS)
                "lastSubmitted" -> lastSubmitted = reader.optionalString(128)
                "isPending" -> pending = reader.optionalBoolean() ?: false
                "isSitemapsIndex" -> index = reader.optionalBoolean() ?: false
                "type" -> type = reader.optionalString(MAX_STATUS_CHARACTERS)
                "lastDownloaded" -> lastDownloaded = reader.optionalString(128)
                "warnings" -> warnings = reader.flexibleLong() ?: 0L
                "errors" -> errors = reader.flexibleLong() ?: 0L
                "contents" -> {
                    val parsed = mutableListOf<SearchConsoleSitemapContent>()
                    reader.readArray(MAX_SITEMAP_CONTENTS) { parsed += readSitemapContent(reader) }
                    contents = parsed
                }
                else -> reader.skipValue()
            }
        }
        return SearchConsoleSitemap(
            path?.takeIf(String::isNotBlank)
                ?: throw SearchConsoleResponseFormatException("Sitemap omitted its path."),
            lastSubmitted, pending, index, type, lastDownloaded, warnings, errors, contents,
        )
    }

    private fun readSitemapContent(reader: JsonReader): SearchConsoleSitemapContent {
        var type: String? = null
        var submitted = 0L
        var indexed: Long? = null
        reader.readObject { name ->
            when (name) {
                "type" -> type = reader.optionalString(MAX_STATUS_CHARACTERS)
                "submitted" -> submitted = reader.flexibleLong() ?: 0L
                "indexed" -> indexed = reader.flexibleLong()
                else -> reader.skipValue()
            }
        }
        return SearchConsoleSitemapContent(
            type?.takeIf(String::isNotBlank)
                ?: throw SearchConsoleResponseFormatException("Sitemap content omitted its type."),
            submitted,
            indexed,
        )
    }

    private fun readInspectionResult(
        reader: JsonReader,
        budget: InspectionBudget,
    ): SearchConsoleUrlInspectionResult {
        var link: String? = null
        var index: SearchConsoleIndexStatus? = null
        var amp: SearchConsoleAmpResult? = null
        var mobile: SearchConsoleMobileUsabilityResult? = null
        var rich: SearchConsoleRichResultsResult? = null
        reader.readObject { name ->
            when (name) {
                "inspectionResultLink" -> link = reader.optionalString(MAX_URL_CHARACTERS)
                "indexStatusResult" -> index = readIndexStatus(reader, budget)
                "ampResult" -> amp = readAmpResult(reader, budget)
                "mobileUsabilityResult" -> mobile = readMobileResult(reader, budget)
                "richResultsResult" -> rich = readRichResults(reader, budget)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleUrlInspectionResult(link, index, amp, mobile, rich)
    }

    private fun readIndexStatus(reader: JsonReader, budget: InspectionBudget): SearchConsoleIndexStatus {
        var sitemaps = emptyList<String>()
        var referring = emptyList<String>()
        val values = mutableMapOf<String, String?>()
        reader.readObject { name ->
            when (name) {
                "sitemap" -> sitemaps = reader.inspectionStringArray(budget, MAX_URL_CHARACTERS)
                "referringUrls" -> referring = reader.inspectionStringArray(budget, MAX_URL_CHARACTERS)
                in INDEX_FIELDS -> values[name] = reader.optionalString(MAX_URL_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleIndexStatus(
            sitemaps, referring, values["verdict"], values["coverageState"],
            values["robotsTxtState"], values["indexingState"], values["lastCrawlTime"],
            values["pageFetchState"], values["googleCanonical"], values["userCanonical"],
            values["crawledAs"],
        )
    }

    private fun readAmpResult(reader: JsonReader, budget: InspectionBudget): SearchConsoleAmpResult {
        var issues = emptyList<SearchConsoleInspectionIssue>()
        val values = mutableMapOf<String, String?>()
        reader.readObject { name ->
            when (name) {
                "issues" -> issues = readIssues(reader, amp = true, budget = budget)
                in AMP_FIELDS -> values[name] = reader.optionalString(MAX_URL_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleAmpResult(
            issues, values["verdict"], values["ampUrl"], values["robotsTxtState"],
            values["indexingState"], values["ampIndexStatusVerdict"], values["lastCrawlTime"],
            values["pageFetchState"],
        )
    }

    private fun readMobileResult(
        reader: JsonReader,
        budget: InspectionBudget,
    ): SearchConsoleMobileUsabilityResult {
        var issues = emptyList<SearchConsoleInspectionIssue>()
        var verdict: String? = null
        reader.readObject { name ->
            when (name) {
                "issues" -> issues = readIssues(reader, amp = false, budget = budget)
                "verdict" -> verdict = reader.optionalString(MAX_STATUS_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleMobileUsabilityResult(issues, verdict)
    }

    private fun readRichResults(
        reader: JsonReader,
        budget: InspectionBudget,
    ): SearchConsoleRichResultsResult {
        var verdict: String? = null
        val detected = mutableListOf<SearchConsoleDetectedRichResult>()
        reader.readObject { name ->
            when (name) {
                "verdict" -> verdict = reader.optionalString(MAX_STATUS_CHARACTERS)
                "detectedItems" -> reader.readArray(MAX_INSPECTION_ITEMS) {
                    budget.consume()
                    detected += readDetectedRichResult(reader, budget)
                }
                else -> reader.skipValue()
            }
        }
        return SearchConsoleRichResultsResult(detected, verdict)
    }

    private fun readDetectedRichResult(
        reader: JsonReader,
        budget: InspectionBudget,
    ): SearchConsoleDetectedRichResult {
        var type: String? = null
        val items = mutableListOf<SearchConsoleRichResultItem>()
        reader.readObject { name ->
            when (name) {
                "richResultType" -> type = reader.optionalString(MAX_STATUS_CHARACTERS)
                "items" -> reader.readArray(MAX_INSPECTION_ITEMS) {
                    budget.consume()
                    items += readRichResultItem(reader, budget)
                }
                else -> reader.skipValue()
            }
        }
        return SearchConsoleDetectedRichResult(
            type?.takeIf(String::isNotBlank)
                ?: throw SearchConsoleResponseFormatException("Rich result omitted its type."),
            items,
        )
    }

    private fun readRichResultItem(
        reader: JsonReader,
        budget: InspectionBudget,
    ): SearchConsoleRichResultItem {
        var name: String? = null
        var issues = emptyList<SearchConsoleInspectionIssue>()
        reader.readObject { field ->
            when (field) {
                "name" -> name = reader.optionalString(MAX_STATUS_CHARACTERS)
                "issues" -> issues = readIssues(reader, amp = true, budget = budget)
                else -> reader.skipValue()
            }
        }
        return SearchConsoleRichResultItem(name, issues)
    }

    private fun readIssues(
        reader: JsonReader,
        amp: Boolean,
        budget: InspectionBudget,
    ): List<SearchConsoleInspectionIssue> {
        val issues = mutableListOf<SearchConsoleInspectionIssue>()
        reader.readArray(MAX_INSPECTION_ITEMS) {
            budget.consume()
            var type: String? = null
            var severity: String? = null
            var message: String? = null
            reader.readObject { name ->
                when (name) {
                    "issueType" -> type = reader.optionalString(MAX_STATUS_CHARACTERS)
                    "severity" -> severity = reader.optionalString(MAX_STATUS_CHARACTERS)
                    "message" -> message = reader.optionalString(MAX_URL_CHARACTERS)
                    "issueMessage" -> message = reader.optionalString(MAX_URL_CHARACTERS)
                    else -> reader.skipValue()
                }
            }
            issues += SearchConsoleInspectionIssue(if (amp) null else type, severity, message)
        }
        return issues
    }

    private inline fun <T> parse(bytes: ByteArray, block: (JsonReader) -> T): T = try {
        require(bytes.isNotEmpty()) { "Google returned an empty response." }
        ByteArrayInputStream(bytes).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { text ->
                JsonReader(text).use { reader ->
                    reader.isLenient = false
                    val result = block(reader)
                    if (reader.peek() != JsonToken.END_DOCUMENT) {
                        throw SearchConsoleResponseFormatException("Google returned trailing JSON data.")
                    }
                    result
                }
            }
        }
    } catch (error: SearchConsoleResponseFormatException) {
        throw error
    } catch (error: Exception) {
        throw SearchConsoleResponseFormatException("Could not read the Google response.", error)
    }

    private inline fun JsonReader.readObject(field: (String) -> Unit) {
        if (peek() != JsonToken.BEGIN_OBJECT) {
            throw SearchConsoleResponseFormatException("Expected a Google JSON object.")
        }
        beginObject()
        while (hasNext()) field(nextName())
        endObject()
    }

    private inline fun JsonReader.readArray(maximum: Int, item: () -> Unit) {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return
        }
        if (peek() != JsonToken.BEGIN_ARRAY) {
            throw SearchConsoleResponseFormatException("Expected a Google JSON array.")
        }
        beginArray()
        var count = 0
        while (hasNext()) {
            count += 1
            if (count > maximum) throw SearchConsoleResponseFormatException("Google returned too many items.")
            item()
        }
        endArray()
    }

    private fun JsonReader.optionalString(maximum: Int): String? {
        if (peek() == JsonToken.NULL) return nextNull().let { null }
        if (peek() != JsonToken.STRING && peek() != JsonToken.NUMBER) {
            throw SearchConsoleResponseFormatException("Expected a Google string.")
        }
        return nextString().also {
            if (it.length > maximum) throw SearchConsoleResponseFormatException("Google string is too long.")
        }
    }

    private fun JsonReader.optionalBoolean(): Boolean? = when (peek()) {
        JsonToken.NULL -> nextNull().let { null }
        JsonToken.BOOLEAN -> nextBoolean()
        else -> throw SearchConsoleResponseFormatException("Expected a Google boolean.")
    }

    private fun JsonReader.finiteDouble(): Double? {
        if (peek() == JsonToken.NULL) return nextNull().let { null }
        val value = nextString().toDoubleOrNull()
            ?: throw SearchConsoleResponseFormatException("Expected a Google number.")
        if (!value.isFinite()) throw SearchConsoleResponseFormatException("Google returned a non-finite number.")
        return value
    }

    private fun JsonReader.flexibleLong(): Long? {
        if (peek() == JsonToken.NULL) return nextNull().let { null }
        val value = nextString()
        return value.toLongOrNull() ?: value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let {
            if (it < Long.MIN_VALUE.toDouble() || it > Long.MAX_VALUE.toDouble()) null else it.toLong()
        } ?: throw SearchConsoleResponseFormatException("Expected a Google integer.")
    }

    private fun JsonReader.stringArray(maximum: Int, maximumCharacters: Int): List<String> {
        val values = mutableListOf<String>()
        readArray(maximum) {
            optionalString(maximumCharacters)?.let(values::add)
        }
        return values
    }

    private fun JsonReader.inspectionStringArray(
        budget: InspectionBudget,
        maximumCharacters: Int,
    ): List<String> {
        val values = mutableListOf<String>()
        readArray(MAX_INSPECTION_ITEMS) {
            budget.consume()
            optionalString(maximumCharacters)?.let(values::add)
        }
        return values
    }

    private class InspectionBudget {
        private var remaining = MAX_INSPECTION_ITEMS

        fun consume() {
            if (remaining == 0) {
                throw SearchConsoleResponseFormatException(
                    "Google returned too many URL inspection items.",
                )
            }
            remaining -= 1
        }
    }

    companion object {
        private const val MAX_SITEMAPS = 10_000
        private const val MAX_SITEMAP_CONTENTS = 256
        private const val MAX_SECRET_CHARACTERS = 16_384
        private const val MAX_SCOPE_LIST_CHARACTERS = 16_384
        private const val MAX_TOKEN_LIFETIME_SECONDS = 7 * 24 * 60 * 60L
        private val INDEX_FIELDS = setOf(
            "verdict", "coverageState", "robotsTxtState", "indexingState", "lastCrawlTime",
            "pageFetchState", "googleCanonical", "userCanonical", "crawledAs",
        )
        private val AMP_FIELDS = setOf(
            "verdict", "ampUrl", "robotsTxtState", "indexingState", "ampIndexStatusVerdict",
            "lastCrawlTime", "pageFetchState",
        )
    }
}
