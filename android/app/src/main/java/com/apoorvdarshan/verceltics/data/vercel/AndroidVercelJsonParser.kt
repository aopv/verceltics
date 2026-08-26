package com.apoorvdarshan.verceltics.data.vercel

import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

interface VercelJsonParser {
    fun parseUser(bytes: ByteArray): VercelUser

    fun parseProjects(bytes: ByteArray): VercelProjectsPage

    fun parseErrorCode(bytes: ByteArray): String?
}

/** Strict streaming JSON parsing using the Android platform API. Unknown fields are ignored. */
class AndroidVercelJsonParser : VercelJsonParser {
    override fun parseUser(bytes: ByteArray): VercelUser = parse(bytes) { reader ->
        var user: VercelUser? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "user" -> user = readUser(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        user ?: throw VercelResponseFormatException("Vercel returned no user record.")
    }

    override fun parseProjects(bytes: ByteArray): VercelProjectsPage = parse(bytes) { reader ->
        val projects = mutableListOf<VercelProject>()
        var nextCursor: String? = null
        var sawProjects = false
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "projects" -> {
                    sawProjects = true
                    reader.beginArray()
                    while (reader.hasNext()) projects += readProject(reader)
                    reader.endArray()
                }

                "pagination" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "next" -> nextCursor = readStringOrNumber(reader)
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
        if (!sawProjects) throw VercelResponseFormatException("Vercel returned no projects collection.")
        VercelProjectsPage(projects = projects, nextCursor = nextCursor)
    }

    override fun parseErrorCode(bytes: ByteArray): String? = try {
        parse(bytes) { reader ->
            var errorCode: String? = null
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "error" -> {
                        if (reader.peek() == JsonToken.BEGIN_OBJECT) {
                            reader.beginObject()
                            while (reader.hasNext()) {
                                when (reader.nextName()) {
                                    "code" -> errorCode = readOptionalString(reader)
                                    else -> reader.skipValue()
                                }
                            }
                            reader.endObject()
                        } else {
                            reader.skipValue()
                        }
                    }

                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            errorCode?.take(MAX_ERROR_CODE_CHARACTERS)
        }
    } catch (_: VercelResponseFormatException) {
        null
    }

    private fun readUser(reader: JsonReader): VercelUser {
        var id: String? = null
        var username: String? = null
        var email: String? = null
        var name: String? = null
        var avatarUrl: String? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = readOptionalString(reader)
                "username" -> username = readOptionalString(reader)
                "email" -> email = readOptionalString(reader)
                "name" -> name = readOptionalString(reader)
                "avatar" -> avatarUrl = readOptionalString(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        val requiredId = id?.takeIf(String::isNotBlank)
            ?: throw VercelResponseFormatException("Vercel user id is missing.")
        val requiredUsername = username?.takeIf(String::isNotBlank)
            ?: email?.substringBefore('@')?.takeIf(String::isNotBlank)
            ?: throw VercelResponseFormatException("Vercel username is missing.")
        return VercelUser(
            id = requiredId,
            username = requiredUsername,
            email = email,
            name = name,
            avatarUrl = avatarUrl,
        )
    }

    private fun readProject(reader: JsonReader): VercelProject {
        var id: String? = null
        var name: String? = null
        var framework: String? = null
        var createdAtMillis: Long? = null
        var updatedAtMillis: Long? = null
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "id" -> id = readOptionalString(reader)
                "name" -> name = readOptionalString(reader)
                "framework" -> framework = readOptionalString(reader)
                "createdAt" -> createdAtMillis = readOptionalLong(reader)
                "updatedAt" -> updatedAtMillis = readOptionalLong(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return VercelProject(
            id = id?.takeIf(String::isNotBlank)
                ?: throw VercelResponseFormatException("Vercel project id is missing."),
            name = name?.takeIf(String::isNotBlank)
                ?: throw VercelResponseFormatException("Vercel project name is missing."),
            framework = framework,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis,
        )
    }

    private fun readOptionalString(reader: JsonReader): String? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }

        JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun readStringOrNumber(reader: JsonReader): String? = readOptionalString(reader)

    private fun readOptionalLong(reader: JsonReader): Long? = when (reader.peek()) {
        JsonToken.NULL -> {
            reader.nextNull()
            null
        }

        JsonToken.NUMBER, JsonToken.STRING -> reader.nextString().toLongOrNull()
        else -> {
            reader.skipValue()
            null
        }
    }

    private fun <T> parse(bytes: ByteArray, block: (JsonReader) -> T): T = try {
        ByteArrayInputStream(bytes).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { text ->
                JsonReader(text).use { reader ->
                    reader.isLenient = false
                    val result = block(reader)
                    if (reader.peek() != JsonToken.END_DOCUMENT) {
                        throw VercelResponseFormatException("Vercel returned trailing JSON data.")
                    }
                    result
                }
            }
        }
    } catch (error: VercelResponseFormatException) {
        throw error
    } catch (error: Exception) {
        throw VercelResponseFormatException("Vercel returned malformed JSON.", error)
    }

    companion object {
        private const val MAX_ERROR_CODE_CHARACTERS = 128
    }
}
