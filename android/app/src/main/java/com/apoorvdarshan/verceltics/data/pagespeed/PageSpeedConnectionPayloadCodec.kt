package com.apoorvdarshan.verceltics.data.pagespeed

import com.apoorvdarshan.verceltics.data.account.SecretValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/** Provider-specific plaintext format used only directly beside authenticated encryption. */
object PageSpeedConnectionPayloadCodec {
    private const val PAYLOAD_VERSION = 1
    private const val PROVIDER_ID = "pagespeed-crux"
    private const val MAX_ID_BYTES = 1_024
    private const val MAX_URL_BYTES = 8_192
    private const val MAX_API_KEY_BYTES = 65_536
    private const val MAX_LABEL_BYTES = 4_096
    private const val MAX_FORMATTED_VALUE_BYTES = 4_096
    private const val MAX_METRICS = 128
    private const val MAX_WARNINGS = 32

    fun encode(connection: PageSpeedStoredConnection): ByteArray {
        val bytes = WipingByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        return try {
            output.writeInt(PAYLOAD_VERSION)
            writeString(output, PROVIDER_ID, MAX_ID_BYTES)
            writeString(output, connection.id, MAX_ID_BYTES)
            writeString(output, connection.credentials.siteUrl.toASCIIString(), MAX_URL_BYTES)
            val keyBytes = connection.credentials.apiKey.utf8Bytes()
            try {
                writeBytes(output, keyBytes, MAX_API_KEY_BYTES)
            } finally {
                keyBytes.fill(0)
            }
            output.writeLong(connection.createdAtMillis)
            output.writeLong(connection.updatedAtMillis)
            output.writeBoolean(connection.cachedSnapshot != null)
            connection.cachedSnapshot?.let { writeSnapshot(output, it) }
            output.flush()
            bytes.toByteArray()
        } finally {
            output.close()
        }
    }

    fun decode(bytes: ByteArray): PageSpeedStoredConnection {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == PAYLOAD_VERSION) {
                "Unsupported PageSpeed connection payload version."
            }
            require(readString(input, MAX_ID_BYTES) == PROVIDER_ID) {
                "The PageSpeed connection provider does not match its storage slot."
            }
            val id = readString(input, MAX_ID_BYTES)
            val siteUrl = PageSpeedCredentials.normalizeSiteUrl(readString(input, MAX_URL_BYTES))
            val keyBytes = readBytes(input, MAX_API_KEY_BYTES)
            val key = try {
                SecretValue.of(String(keyBytes, StandardCharsets.UTF_8))
            } finally {
                keyBytes.fill(0)
            }
            val createdAtMillis = input.readLong()
            val updatedAtMillis = input.readLong()
            val snapshot = if (input.readBoolean()) readSnapshot(input) else null
            require(input.available() == 0) { "Unexpected trailing PageSpeed connection data." }
            return PageSpeedStoredConnection(
                id = id,
                credentials = PageSpeedCredentials.restored(key, siteUrl),
                createdAtMillis = createdAtMillis,
                updatedAtMillis = updatedAtMillis,
                cachedSnapshot = snapshot,
            )
        }
    }

    private fun writeSnapshot(output: DataOutputStream, snapshot: PageSpeedSnapshot) {
        writeString(output, snapshot.siteUrl.toASCIIString(), MAX_URL_BYTES)
        writeString(output, snapshot.siteName, MAX_LABEL_BYTES)
        writeString(output, snapshot.status, MAX_LABEL_BYTES)
        output.writeLong(snapshot.fetchedAtMillis)
        output.writeByte(snapshot.availability.desktop.ordinal)
        output.writeByte(snapshot.availability.crux.ordinal)
        require(snapshot.metrics.size <= MAX_METRICS) { "Too many PageSpeed metrics." }
        output.writeInt(snapshot.metrics.size)
        snapshot.metrics.forEach { metric ->
            writeString(output, metric.key, MAX_LABEL_BYTES)
            writeString(output, metric.label, MAX_LABEL_BYTES)
            output.writeDouble(metric.value)
            output.writeByte(metric.unit.ordinal)
            output.writeBoolean(metric.formattedValue != null)
            metric.formattedValue?.let {
                writeString(output, it, MAX_FORMATTED_VALUE_BYTES)
            }
        }
        require(snapshot.warnings.size <= MAX_WARNINGS) { "Too many PageSpeed warnings." }
        output.writeInt(snapshot.warnings.size)
        snapshot.warnings.forEach { writeString(output, it, MAX_LABEL_BYTES) }
    }

    private fun readSnapshot(input: DataInputStream): PageSpeedSnapshot {
        val siteUrl = PageSpeedCredentials.normalizeSiteUrl(readString(input, MAX_URL_BYTES))
        val siteName = readString(input, MAX_LABEL_BYTES)
        val status = readString(input, MAX_LABEL_BYTES)
        val fetchedAtMillis = input.readLong()
        val desktop = readEnum<PageSpeedSourceState>(input.readUnsignedByte(), "source state")
        val crux = readEnum<PageSpeedSourceState>(input.readUnsignedByte(), "source state")
        val metricCount = readCount(input, MAX_METRICS, "metric")
        val metrics = List(metricCount) {
            PageSpeedMetric(
                key = readString(input, MAX_LABEL_BYTES),
                label = readString(input, MAX_LABEL_BYTES),
                value = input.readDouble(),
                unit = readEnum(input.readUnsignedByte(), "metric unit"),
                formattedValue = if (input.readBoolean()) {
                    readString(input, MAX_FORMATTED_VALUE_BYTES)
                } else {
                    null
                },
            )
        }
        val warningCount = readCount(input, MAX_WARNINGS, "warning")
        val warnings = List(warningCount) { readString(input, MAX_LABEL_BYTES) }
        return PageSpeedSnapshot(
            siteUrl = siteUrl,
            siteName = siteName,
            status = status,
            metrics = metrics,
            fetchedAtMillis = fetchedAtMillis,
            availability = PageSpeedSourceAvailability(desktop = desktop, crux = crux),
            warnings = warnings,
        )
    }

    private inline fun <reified T : Enum<T>> readEnum(ordinal: Int, label: String): T =
        enumValues<T>().getOrNull(ordinal)
            ?: throw IllegalArgumentException("Invalid PageSpeed $label.")

    private fun readCount(input: DataInputStream, maximum: Int, label: String): Int {
        val count = input.readInt()
        require(count in 0..maximum) { "Invalid PageSpeed $label count." }
        return count
    }

    private fun writeString(output: DataOutputStream, value: String, maximumBytes: Int) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        try {
            writeBytes(output, bytes, maximumBytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun writeBytes(output: DataOutputStream, bytes: ByteArray, maximumBytes: Int) {
        require(bytes.size <= maximumBytes) { "PageSpeed connection field is too large." }
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun readString(input: DataInputStream, maximumBytes: Int): String {
        val bytes = readBytes(input, maximumBytes)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            bytes.fill(0)
        }
    }

    private fun readBytes(input: DataInputStream, maximumBytes: Int): ByteArray {
        val length = input.readInt()
        require(length in 0..maximumBytes && length <= input.available()) {
            "Invalid PageSpeed connection field length."
        }
        return ByteArray(length).also(input::readFully)
    }
}

private class WipingByteArrayOutputStream : ByteArrayOutputStream() {
    override fun close() {
        buf.fill(0)
        reset()
        super.close()
    }
}
