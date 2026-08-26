package com.apoorvdarshan.verceltics.data.netlify

import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

interface NetlifyJsonParser {
    fun parseUser(bytes: ByteArray): NetlifyUser

    fun parseSites(bytes: ByteArray): List<NetlifySite>

    fun parseSiteDetails(bytes: ByteArray, expectedSiteId: String): NetlifySiteDetails

    fun parseDeployments(bytes: ByteArray): List<NetlifyDeployment>

    fun parseBuilds(bytes: ByteArray): List<NetlifyBuild>

    fun parseBuild(bytes: ByteArray): NetlifyBuild

    fun parseErrorCode(bytes: ByteArray): String?
}

class NetlifyResponseFormatException(message: String) : RuntimeException(message)

/** Strict Android streaming parser. Unknown Netlify fields are deliberately ignored. */
class AndroidNetlifyJsonParser : NetlifyJsonParser {
    override fun parseUser(bytes: ByteArray): NetlifyUser = parse(bytes) { reader ->
        var id: String? = null
        var uid: String? = null
        var fullName: String? = null
        var name: String? = null
        var email: String? = null
        var avatarUrl: String? = null
        expect(reader, JsonToken.BEGIN_OBJECT, "Netlify returned an invalid user record.")
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.optionalString(MAX_ID_CHARACTERS)
                "uid" -> uid = reader.optionalString(MAX_ID_CHARACTERS)
                "full_name" -> fullName = reader.optionalString(MAX_NAME_CHARACTERS)
                "name" -> name = reader.optionalString(MAX_NAME_CHARACTERS)
                "email" -> email = reader.optionalString(MAX_URL_CHARACTERS)
                "avatar_url" -> avatarUrl = reader.optionalString(MAX_URL_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        NetlifyUser(id, uid, fullName, name, email, avatarUrl)
    }

    override fun parseSites(bytes: ByteArray): List<NetlifySite> = parse(bytes) { reader ->
        expect(reader, JsonToken.BEGIN_ARRAY, "Netlify returned no sites collection.")
        val sites = mutableListOf<NetlifySite>()
        reader.beginArray()
        while (reader.hasNext()) readSite(reader).summaryOrNull()?.let(sites::add)
        reader.endArray()
        sites
    }

    override fun parseSiteDetails(bytes: ByteArray, expectedSiteId: String): NetlifySiteDetails =
        parse(bytes) { reader ->
            requireSafeExpectedId(expectedSiteId)
            val raw = readSite(reader)
            val site = raw.summaryOrNull(expectedSiteId)
                ?: throw NetlifyResponseFormatException("Netlify returned an invalid site record.")
            NetlifySiteDetails(
                site = site,
                domains = raw.domains(),
                buildControls = raw.buildControls(),
                publishedDeployment = raw.publishedDeployment,
            )
        }

    override fun parseDeployments(bytes: ByteArray): List<NetlifyDeployment> = parse(bytes) { reader ->
        expect(reader, JsonToken.BEGIN_ARRAY, "Netlify returned no deploys collection.")
        val deployments = mutableListOf<NetlifyDeployment>()
        reader.beginArray()
        while (reader.hasNext()) deployments += readDeployment(reader)
        reader.endArray()
        deployments
    }

    override fun parseBuilds(bytes: ByteArray): List<NetlifyBuild> = parse(bytes) { reader ->
        expect(reader, JsonToken.BEGIN_ARRAY, "Netlify returned no builds collection.")
        val builds = mutableListOf<NetlifyBuild>()
        reader.beginArray()
        while (reader.hasNext()) builds += readBuild(reader)
        reader.endArray()
        builds
    }

    override fun parseBuild(bytes: ByteArray): NetlifyBuild = parse(bytes, ::readBuild)

    override fun parseErrorCode(bytes: ByteArray): String? = try {
        parse(bytes) { reader ->
            var code: String? = null
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                reader.skipValue()
                return@parse null
            }
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "code" -> code = reader.optionalString(MAX_STATUS_CHARACTERS)
                    "error" -> {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "code" -> code = reader.optionalString(MAX_STATUS_CHARACTERS)
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            code = reader.optionalString(MAX_STATUS_CHARACTERS)
                        }
                    }
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            code
        }
    } catch (_: NetlifyResponseFormatException) {
        null
    }

    private fun readSite(reader: JsonReader): RawSite {
        expect(reader, JsonToken.BEGIN_OBJECT, "Netlify returned an invalid site record.")
        var id: String? = null
        var name: String? = null
        var customDomain: String? = null
        var url: String? = null
        var sslUrl: String? = null
        var state: String? = null
        var updatedAtMillis: Long? = null
        var adminUrl: String? = null
        var aliases = emptyList<String>()
        var publishedBoolean = false
        var publishedDeployment: NetlifyDeployment? = null
        var buildsStopped: Boolean? = null
        var buildSettings: RawBuildSettings? = null

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.optionalString(MAX_ID_CHARACTERS)
                "name" -> name = reader.optionalString(MAX_NAME_CHARACTERS)
                "custom_domain" -> customDomain = reader.optionalString(MAX_DOMAIN_CHARACTERS)
                "url" -> url = reader.optionalString(MAX_URL_CHARACTERS)
                "ssl_url" -> sslUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "state" -> state = reader.optionalString(MAX_STATUS_CHARACTERS)
                "updated_at" -> updatedAtMillis = reader.optionalInstantMillis()
                "admin_url" -> adminUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "domain_aliases" -> aliases = reader.stringArray(MAX_NESTED_ITEMS, MAX_DOMAIN_CHARACTERS)
                "published_deploy" -> when (reader.peek()) {
                    JsonToken.BOOLEAN -> publishedBoolean = reader.nextBoolean()
                    JsonToken.BEGIN_OBJECT -> publishedDeployment = readDeployment(reader)
                    JsonToken.NULL -> reader.nextNull()
                    else -> reader.skipValue()
                }
                "stop_builds" -> buildsStopped = reader.optionalBoolean()
                "build_settings" -> buildSettings = readBuildSettings(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RawSite(
            id = id,
            name = name,
            customDomain = customDomain,
            url = url,
            sslUrl = sslUrl,
            state = state,
            updatedAtMillis = updatedAtMillis,
            adminUrl = adminUrl,
            aliases = aliases,
            publishedBoolean = publishedBoolean,
            publishedDeployment = publishedDeployment,
            buildsStopped = buildsStopped,
            buildSettings = buildSettings,
        )
    }

    private fun readBuildSettings(reader: JsonReader): RawBuildSettings? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue()
            return null
        }
        var repositoryUrl: String? = null
        var repositoryPath: String? = null
        var repositoryBranch: String? = null
        var baseDirectory: String? = null
        var publishDirectory: String? = null
        var functionsDirectory: String? = null
        var buildCommand: String? = null
        var allowedBranches = emptyList<String>()
        var provider: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "repo_url" -> repositoryUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "repo_path" -> repositoryPath = reader.optionalString(MAX_URL_CHARACTERS)
                "repo_branch" -> repositoryBranch = reader.optionalString(MAX_NAME_CHARACTERS)
                "base" -> baseDirectory = reader.optionalString(MAX_URL_CHARACTERS)
                "dir" -> publishDirectory = reader.optionalString(MAX_URL_CHARACTERS)
                "functions_dir" -> functionsDirectory = reader.optionalString(MAX_URL_CHARACTERS)
                "cmd" -> buildCommand = reader.optionalString(MAX_URL_CHARACTERS)
                "allowed_branches" -> allowedBranches =
                    reader.stringArray(MAX_NESTED_ITEMS, MAX_NAME_CHARACTERS)
                "provider" -> provider = reader.optionalString(MAX_NAME_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return RawBuildSettings(
            repositoryUrl,
            repositoryPath,
            repositoryBranch,
            baseDirectory,
            publishDirectory,
            functionsDirectory,
            buildCommand,
            allowedBranches,
            provider,
        )
    }

    private fun readDeployment(reader: JsonReader): NetlifyDeployment {
        expect(reader, JsonToken.BEGIN_OBJECT, "Netlify returned an invalid deploy record.")
        var id: String? = null
        var title: String? = null
        var context: String? = null
        var state: String? = null
        var createdAtMillis: Long? = null
        var sslUrl: String? = null
        var deploySslUrl: String? = null
        var url: String? = null
        var branch: String? = null
        var commitRef: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.optionalString(MAX_ID_CHARACTERS)
                "title" -> title = reader.optionalString(MAX_NAME_CHARACTERS)
                "context" -> context = reader.optionalString(MAX_NAME_CHARACTERS)
                "state" -> state = reader.optionalString(MAX_STATUS_CHARACTERS)
                "created_at", "published_at" -> {
                    val parsed = reader.optionalInstantMillis()
                    if (createdAtMillis == null) createdAtMillis = parsed
                }
                "ssl_url" -> sslUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "deploy_ssl_url" -> deploySslUrl = reader.optionalString(MAX_URL_CHARACTERS)
                "url" -> url = reader.optionalString(MAX_URL_CHARACTERS)
                "branch" -> branch = reader.optionalString(MAX_NAME_CHARACTERS)
                "commit_ref" -> commitRef = reader.optionalString(MAX_URL_CHARACTERS)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val resolvedTitle = firstNonBlank(title, context) ?: "Deploy"
        val resolvedUrl = firstNonBlank(sslUrl, deploySslUrl, url)
        val resolvedId = id?.takeIf(String::isNotBlank) ?: stableIdentifier(
            namespace = "netlify-deploy",
            values = listOf(resolvedTitle, state, createdAtMillis?.toString(), resolvedUrl, branch, commitRef),
        )
        return NetlifyDeployment(
            id = resolvedId,
            title = resolvedTitle,
            status = state?.takeIf(String::isNotBlank) ?: "unknown",
            createdAtMillis = createdAtMillis,
            url = resolvedUrl,
            branch = branch,
            commitMessage = firstNonBlank(title, commitRef),
        )
    }

    private fun readBuild(reader: JsonReader): NetlifyBuild {
        expect(reader, JsonToken.BEGIN_OBJECT, "Netlify returned an invalid build record.")
        var id: String? = null
        var deploymentId: String? = null
        var commitSha: String? = null
        var done: Boolean? = null
        var error: String? = null
        var createdAtMillis: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = reader.optionalString(MAX_ID_CHARACTERS)
                "deploy_id" -> deploymentId = reader.optionalString(MAX_ID_CHARACTERS)
                "sha" -> commitSha = reader.optionalString(MAX_ID_CHARACTERS)
                "done" -> done = reader.optionalBoolean()
                "error" -> error = reader.optionalString(MAX_URL_CHARACTERS)
                "created_at" -> createdAtMillis = reader.optionalInstantMillis()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return NetlifyBuild(
            id = id?.takeIf(String::isNotBlank) ?: stableIdentifier(
                namespace = "netlify-build",
                values = listOf(deploymentId, commitSha, createdAtMillis?.toString(), error),
            ),
            deploymentId = deploymentId,
            commitSha = commitSha,
            isDone = done,
            error = error,
            createdAtMillis = createdAtMillis,
        )
    }

    private inline fun <T> parse(bytes: ByteArray, block: (JsonReader) -> T): T {
        if (bytes.isEmpty()) throw NetlifyResponseFormatException("Netlify returned an empty response.")
        return try {
            JsonReader(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8)).use { reader ->
                reader.isLenient = false
                val value = block(reader)
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw NetlifyResponseFormatException("Netlify returned trailing response data.")
                }
                value
            }
        } catch (error: NetlifyResponseFormatException) {
            throw error
        } catch (_: Exception) {
            throw NetlifyResponseFormatException("Netlify returned an invalid JSON response.")
        }
    }

    private data class RawSite(
        val id: String?,
        val name: String?,
        val customDomain: String?,
        val url: String?,
        val sslUrl: String?,
        val state: String?,
        val updatedAtMillis: Long?,
        val adminUrl: String?,
        val aliases: List<String>,
        val publishedBoolean: Boolean,
        val publishedDeployment: NetlifyDeployment?,
        val buildsStopped: Boolean?,
        val buildSettings: RawBuildSettings?,
    ) {
        fun summaryOrNull(expectedId: String? = null): NetlifySite? {
            val resolvedId = id?.takeIf(String::isNotBlank)
                ?: expectedId?.takeIf(String::isNotBlank)
                ?: stableIdentifierOrNull(
                    namespace = "netlify-site",
                    values = listOf(firstNonBlank(name, customDomain), firstNonBlank(sslUrl, url)),
                )
                ?: return null
            return NetlifySite(
                id = resolvedId,
                name = firstNonBlank(name, customDomain) ?: "Untitled site",
                subtitle = firstNonBlank(customDomain, url),
                url = firstNonBlank(sslUrl, url),
                status = state?.takeIf(String::isNotBlank) ?: if (publishedBoolean) "Published" else null,
                updatedAtMillis = updatedAtMillis,
                adminUrl = adminUrl,
            )
        }

        fun domains(): List<NetlifyDomain> {
            val result = mutableListOf<NetlifyDomain>()
            val seen = mutableSetOf<String>()
            fun append(value: String?, kind: NetlifyDomainKind) {
                val domain = value?.trim()?.takeIf(String::isNotBlank) ?: return
                if (seen.add(domain.lowercase())) result += NetlifyDomain(domain, kind)
            }
            append(customDomain, NetlifyDomainKind.CUSTOM)
            aliases.forEach { append(it, NetlifyDomainKind.ALIAS) }
            val providerHost = firstNonBlank(sslUrl, url)?.let { value ->
                runCatching { URI(value).host }.getOrNull()
            }
            append(providerHost, NetlifyDomainKind.NETLIFY_SUBDOMAIN)
            return result
        }

        fun buildControls(): NetlifyBuildControls? {
            val settings = buildSettings
            if (buildsStopped == null && settings == null) return null
            return NetlifyBuildControls(
                buildsStopped = buildsStopped,
                repositoryUrl = settings?.repositoryUrl,
                repositoryPath = settings?.repositoryPath,
                repositoryBranch = settings?.repositoryBranch,
                baseDirectory = settings?.baseDirectory,
                publishDirectory = settings?.publishDirectory,
                functionsDirectory = settings?.functionsDirectory,
                buildCommand = settings?.buildCommand,
                allowedBranches = settings?.allowedBranches.orEmpty(),
                provider = settings?.provider,
            )
        }
    }

    private data class RawBuildSettings(
        val repositoryUrl: String?,
        val repositoryPath: String?,
        val repositoryBranch: String?,
        val baseDirectory: String?,
        val publishDirectory: String?,
        val functionsDirectory: String?,
        val buildCommand: String?,
        val allowedBranches: List<String>,
        val provider: String?,
    )

    private companion object {
        fun expect(reader: JsonReader, token: JsonToken, message: String) {
            if (reader.peek() != token) throw NetlifyResponseFormatException(message)
        }

        fun requireSafeExpectedId(value: String) {
            if (value.isBlank() || value.length > MAX_ID_CHARACTERS) {
                throw NetlifyResponseFormatException("The requested Netlify site id is invalid.")
            }
        }

        fun firstNonBlank(vararg values: String?): String? =
            values.firstOrNull { !it.isNullOrBlank() }

        fun stableIdentifier(namespace: String, values: List<String?>): String =
            stableIdentifierOrNull(namespace, values)
                ?: "$namespace-${sha256Hex(namespace).take(20)}"

        fun stableIdentifierOrNull(namespace: String, values: List<String?>): String? {
            val components = values.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            if (components.isEmpty()) return null
            return "$namespace-${sha256Hex(components.joinToString("\u001f")).take(20)}"
        }

        fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

private fun JsonReader.optionalString(maximumCharacters: Int): String? = when (peek()) {
    JsonToken.NULL -> nextNull().let { null }
    JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN -> nextString()
        .trim()
        .take(maximumCharacters)
        .takeIf(String::isNotBlank)
    else -> skipValue().let { null }
}

private fun JsonReader.optionalBoolean(): Boolean? = when (peek()) {
    JsonToken.NULL -> nextNull().let { null }
    JsonToken.BOOLEAN -> nextBoolean()
    JsonToken.STRING, JsonToken.NUMBER -> when (nextString().lowercase()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
    else -> skipValue().let { null }
}

private fun JsonReader.optionalInstantMillis(): Long? {
    val value = optionalString(128) ?: return null
    value.toDoubleOrNull()?.let { numeric ->
        if (!numeric.isFinite() || numeric < 0) return null
        return if (numeric > 10_000_000_000.0) numeric.toLong() else (numeric * 1_000.0).toLong()
    }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

private fun JsonReader.stringArray(maximumItems: Int, maximumCharacters: Int): List<String> {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return emptyList()
    }
    if (peek() != JsonToken.BEGIN_ARRAY) {
        skipValue()
        return emptyList()
    }
    val result = mutableListOf<String>()
    beginArray()
    while (hasNext()) {
        val value = optionalString(maximumCharacters)
        if (value != null && result.size < maximumItems) result += value
    }
    endArray()
    return result
}
