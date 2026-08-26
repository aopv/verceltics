package com.apoorvdarshan.verceltics.data.netlify

import com.apoorvdarshan.verceltics.data.account.SecretValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/** Plaintext exists only between this codec and authenticated encryption. */
internal object NetlifyConnectionPayloadCodec {
    private const val PAYLOAD_VERSION = 1
    private const val MAX_ID_BYTES = 2_048
    private const val MAX_NAME_BYTES = 4_096
    private const val MAX_OPTIONAL_TEXT_BYTES = 32_768
    private const val MAX_TOKEN_BYTES = 65_536
    private const val MAX_WARNING_BYTES = 8_192
    private const val MAX_WARNINGS = 32
    private const val MAX_CACHED_SITES = 128
    private const val MAX_PLAINTEXT_BYTES = 448 * 1024

    fun encode(connection: NetlifyStoredConnection): ByteArray {
        val bytes = WipingByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        return try {
            output.writeInt(PAYLOAD_VERSION)
            writeString(output, connection.account.providerId, MAX_ID_BYTES)
            writeString(output, connection.account.id, MAX_ID_BYTES)
            writeString(output, connection.account.displayName, MAX_NAME_BYTES)
            writeNullableString(output, connection.account.email, MAX_OPTIONAL_TEXT_BYTES)
            writeNullableString(output, connection.account.avatarUrl, MAX_OPTIONAL_TEXT_BYTES)
            val tokenBytes = connection.account.personalToken.utf8Bytes()
            try {
                writeBytes(output, tokenBytes, MAX_TOKEN_BYTES)
            } finally {
                tokenBytes.fill(0)
            }
            output.writeLong(connection.account.createdAtMillis)
            output.writeLong(connection.account.updatedAtMillis)
            output.writeBoolean(connection.cachedSnapshot != null)
            connection.cachedSnapshot?.let { snapshot ->
                require(snapshot.profile.id == connection.account.id) {
                    "The cached Netlify profile does not match its account."
                }
                output.writeLong(snapshot.fetchedAtMillis)
                output.writeBoolean(snapshot.sitesComplete)
                writeStrings(output, snapshot.warnings, MAX_WARNINGS, MAX_WARNING_BYTES)
                require(snapshot.sites.size <= MAX_CACHED_SITES) { "Too many cached Netlify sites." }
                output.writeInt(snapshot.sites.size)
                snapshot.sites.forEach { writeSite(output, it) }
            }
            output.flush()
            bytes.toByteArray().also {
                require(it.size <= MAX_PLAINTEXT_BYTES) { "The Netlify cache is too large to store safely." }
            }
        } finally {
            output.close()
        }
    }

    fun decode(bytes: ByteArray): NetlifyStoredConnection {
        require(bytes.size <= MAX_PLAINTEXT_BYTES) { "The Netlify payload is too large." }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == PAYLOAD_VERSION) { "Unsupported Netlify payload version." }
            require(readString(input, MAX_ID_BYTES) == NetlifyAccount.PROVIDER_ID) {
                "The Netlify provider does not match its storage slot."
            }
            val id = readString(input, MAX_ID_BYTES)
            val displayName = readString(input, MAX_NAME_BYTES)
            val email = readNullableString(input, MAX_OPTIONAL_TEXT_BYTES)
            val avatarUrl = readNullableString(input, MAX_OPTIONAL_TEXT_BYTES)
            val tokenBytes = readBytes(input, MAX_TOKEN_BYTES)
            val token = try {
                SecretValue.of(String(tokenBytes, StandardCharsets.UTF_8))
            } finally {
                tokenBytes.fill(0)
            }
            val account = NetlifyAccount(
                id = id,
                displayName = displayName,
                email = email,
                avatarUrl = avatarUrl,
                personalToken = token,
                createdAtMillis = input.readLong(),
                updatedAtMillis = input.readLong(),
            )
            val snapshot = if (input.readBoolean()) {
                val fetchedAtMillis = input.readLong()
                val sitesComplete = input.readBoolean()
                val warnings = readStrings(input, MAX_WARNINGS, MAX_WARNING_BYTES)
                val siteCount = readCount(input, MAX_CACHED_SITES, "site")
                val sites = List(siteCount) { readSite(input) }
                NetlifySnapshot(
                    profile = account.profile(),
                    sites = sites,
                    fetchedAtMillis = fetchedAtMillis,
                    sitesComplete = sitesComplete,
                    warnings = warnings,
                )
            } else {
                null
            }
            require(input.available() == 0) { "Unexpected trailing Netlify account data." }
            return NetlifyStoredConnection(account, snapshot)
        }
    }

    private fun writeSite(output: DataOutputStream, site: NetlifySite) {
        writeString(output, site.id, MAX_ID_BYTES)
        writeString(output, site.name, MAX_NAME_BYTES)
        writeNullableString(output, site.subtitle, MAX_OPTIONAL_TEXT_BYTES)
        writeNullableString(output, site.url, MAX_OPTIONAL_TEXT_BYTES)
        writeNullableString(output, site.status, MAX_NAME_BYTES)
        writeNullableLong(output, site.updatedAtMillis)
        writeNullableString(output, site.adminUrl, MAX_OPTIONAL_TEXT_BYTES)
    }

    private fun readSite(input: DataInputStream): NetlifySite = NetlifySite(
        id = readString(input, MAX_ID_BYTES),
        name = readString(input, MAX_NAME_BYTES),
        subtitle = readNullableString(input, MAX_OPTIONAL_TEXT_BYTES),
        url = readNullableString(input, MAX_OPTIONAL_TEXT_BYTES),
        status = readNullableString(input, MAX_NAME_BYTES),
        updatedAtMillis = readNullableLong(input),
        adminUrl = readNullableString(input, MAX_OPTIONAL_TEXT_BYTES),
    )

    private fun writeStrings(
        output: DataOutputStream,
        values: List<String>,
        maximumItems: Int,
        maximumBytes: Int,
    ) {
        require(values.size <= maximumItems) { "Too many Netlify cache values." }
        output.writeInt(values.size)
        values.forEach { writeString(output, it, maximumBytes) }
    }

    private fun readStrings(
        input: DataInputStream,
        maximumItems: Int,
        maximumBytes: Int,
    ): List<String> = List(readCount(input, maximumItems, "value")) {
        readString(input, maximumBytes)
    }

    private fun writeString(output: DataOutputStream, value: String, maximumBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            writeBytes(output, bytes, maximumBytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun writeNullableString(output: DataOutputStream, value: String?, maximumBytes: Int) {
        output.writeBoolean(value != null)
        if (value != null) writeString(output, value, maximumBytes)
    }

    private fun writeNullableLong(output: DataOutputStream, value: Long?) {
        output.writeBoolean(value != null)
        if (value != null) output.writeLong(value)
    }

    private fun writeBytes(output: DataOutputStream, value: ByteArray, maximumBytes: Int) {
        require(value.size <= maximumBytes) { "A Netlify account field is too large." }
        output.writeInt(value.size)
        output.write(value)
    }

    private fun readString(input: DataInputStream, maximumBytes: Int): String {
        val bytes = readBytes(input, maximumBytes)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun readNullableString(input: DataInputStream, maximumBytes: Int): String? =
        if (input.readBoolean()) readString(input, maximumBytes) else null

    private fun readNullableLong(input: DataInputStream): Long? =
        if (input.readBoolean()) input.readLong() else null

    private fun readBytes(input: DataInputStream, maximumBytes: Int): ByteArray {
        val length = input.readInt()
        require(length in 0..maximumBytes && length <= input.available()) {
            "Invalid Netlify account field length."
        }
        return ByteArray(length).also(input::readFully)
    }

    private fun readCount(input: DataInputStream, maximum: Int, label: String): Int =
        input.readInt().also { require(it in 0..maximum) { "Invalid Netlify $label count." } }
}

private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
    override fun close() {
        buf.fill(0)
        reset()
        super.close()
    }
}
