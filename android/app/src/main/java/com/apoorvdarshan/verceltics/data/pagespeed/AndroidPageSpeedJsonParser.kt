package com.apoorvdarshan.verceltics.data.pagespeed

import android.util.JsonReader
import android.util.JsonToken
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

interface PageSpeedJsonParser {
    fun parseInsights(bytes: ByteArray, strategy: PageSpeedStrategy): List<PageSpeedMetric>

    fun parseCrux(bytes: ByteArray): List<PageSpeedMetric>
}

class PageSpeedResponseFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** Strict bounded-response parsing using Android's streaming JSON reader. */
class AndroidPageSpeedJsonParser : PageSpeedJsonParser {
    override fun parseInsights(
        bytes: ByteArray,
        strategy: PageSpeedStrategy,
    ): List<PageSpeedMetric> {
        val root = parseObject(bytes)
        val lighthouse = root["lighthouseResult"].asObject()
        val categories = lighthouse["categories"].asObject()
        if (lighthouse.isEmpty() || categories.isEmpty()) {
            throw PageSpeedResponseFormatException(
                "PageSpeed Insights did not return a Lighthouse report.",
            )
        }
        val audits = lighthouse["audits"].asObject()
        val metrics = mutableListOf<PageSpeedMetric>()

        CATEGORY_DEFINITIONS.forEach { (sourceKey, label) ->
            val score = categories[sourceKey].asObject()["score"].asFiniteDouble() ?: return@forEach
            val value = score * 100
            metrics += PageSpeedMetric(
                key = "pagespeed.${strategy.wireValue}.$sourceKey",
                label = "${strategy.label} $label",
                value = value,
                unit = PageSpeedMetricUnit.SCORE,
                formattedValue = "%.0f".format(LocaleHolder.US, value),
            )
        }

        AUDIT_DEFINITIONS.forEach { definition ->
            val audit = audits[definition.sourceKey].asObject()
            val value = audit["numericValue"].asFiniteDouble() ?: return@forEach
            metrics += PageSpeedMetric(
                key = "pagespeed.${strategy.wireValue}.${definition.sourceKey}",
                label = "${strategy.label} ${definition.label}",
                value = value,
                unit = definition.unit,
                formattedValue = audit["displayValue"].asString(),
            )
        }
        return metrics
    }

    override fun parseCrux(bytes: ByteArray): List<PageSpeedMetric> {
        val root = parseObject(bytes)
        val record = root["record"].asObject()
        val values = record["metrics"].asObject()
        if (record.isEmpty() || values.isEmpty()) {
            throw PageSpeedResponseFormatException(
                "Chrome UX Report did not return field metrics for this page.",
            )
        }
        return CRUX_DEFINITIONS.mapNotNull { definition ->
            val value = values[definition.sourceKey]
                .asObject()["percentiles"]
                .asObject()["p75"]
                .asFiniteDouble()
                ?: return@mapNotNull null
            PageSpeedMetric(
                key = "crux.${definition.sourceKey}",
                label = definition.label,
                value = value,
                unit = definition.unit,
            )
        }
    }

    private fun parseObject(bytes: ByteArray): Map<String, Any?> = try {
        require(bytes.isNotEmpty()) { "The provider response was empty." }
        ByteArrayInputStream(bytes).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { text ->
                JsonReader(text).use { reader ->
                    reader.isLenient = false
                    val result = readValue(reader, depth = 0).asObject()
                    if (reader.peek() != JsonToken.END_DOCUMENT || result.isEmpty()) {
                        throw PageSpeedResponseFormatException("The provider response is not a JSON object.")
                    }
                    result
                }
            }
        }
    } catch (error: PageSpeedResponseFormatException) {
        throw error
    } catch (error: Exception) {
        throw PageSpeedResponseFormatException("Could not read the provider response.", error)
    }

    private fun readValue(reader: JsonReader, depth: Int): Any? {
        if (depth > MAX_JSON_DEPTH) {
            throw PageSpeedResponseFormatException("The provider response is nested too deeply.")
        }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> buildMap<String, Any?> {
                reader.beginObject()
                while (reader.hasNext()) put(reader.nextName(), readValue(reader, depth + 1))
                reader.endObject()
            }

            JsonToken.BEGIN_ARRAY -> buildList {
                reader.beginArray()
                while (reader.hasNext()) add(readValue(reader, depth + 1))
                reader.endArray()
            }

            JsonToken.STRING -> reader.nextString()
            JsonToken.NUMBER -> reader.nextString().toDoubleOrNull()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull().let { null }
            else -> throw PageSpeedResponseFormatException("The provider response contains invalid JSON.")
        }
    }

    private fun Any?.asObject(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

    private fun Any?.asFiniteDouble(): Double? {
        val value = when (this) {
            is Number -> toDouble()
            is String -> trim().toDoubleOrNull()
            else -> null
        }
        return value?.takeIf(Double::isFinite)
    }

    private fun Any?.asString(): String? = when (this) {
        is String -> this
        is Number -> toString()
        else -> null
    }

    private data class MetricDefinition(
        val sourceKey: String,
        val label: String,
        val unit: PageSpeedMetricUnit,
    )

    companion object {
        private const val MAX_JSON_DEPTH = 64
        private val CATEGORY_DEFINITIONS = listOf(
            "performance" to "Performance",
            "accessibility" to "Accessibility",
            "best-practices" to "Best Practices",
            "seo" to "SEO",
        )
        private val AUDIT_DEFINITIONS = listOf(
            MetricDefinition("largest-contentful-paint", "LCP (Lab)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("interaction-to-next-paint", "INP (Lab)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("cumulative-layout-shift", "CLS (Lab)", PageSpeedMetricUnit.RATIO),
            MetricDefinition("first-contentful-paint", "FCP (Lab)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("server-response-time", "Server Response", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("total-blocking-time", "Total Blocking Time", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("speed-index", "Speed Index", PageSpeedMetricUnit.MILLISECONDS),
        )
        private val CRUX_DEFINITIONS = listOf(
            MetricDefinition("largest_contentful_paint", "LCP (Page field p75)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("interaction_to_next_paint", "INP (Page field p75)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("cumulative_layout_shift", "CLS (Page field p75)", PageSpeedMetricUnit.RATIO),
            MetricDefinition("first_contentful_paint", "FCP (Page field p75)", PageSpeedMetricUnit.MILLISECONDS),
            MetricDefinition("experimental_time_to_first_byte", "TTFB (Page field p75)", PageSpeedMetricUnit.MILLISECONDS),
        )
    }
}

private object LocaleHolder {
    val US: java.util.Locale = java.util.Locale.US
}
