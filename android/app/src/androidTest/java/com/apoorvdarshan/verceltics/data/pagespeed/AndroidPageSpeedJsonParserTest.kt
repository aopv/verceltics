package com.apoorvdarshan.verceltics.data.pagespeed

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidPageSpeedJsonParserTest {
    private val parser = AndroidPageSpeedJsonParser()

    @Test
    fun parsesCanonicalLighthouseAndCruxMetrics() {
        val insights = parser.parseInsights(
            """
            {
              "lighthouseResult": {
                "categories": {
                  "performance": {"score": 0.92},
                  "accessibility": {"score": 0.88},
                  "best-practices": {"score": 1},
                  "seo": {"score": "0.95"}
                },
                "audits": {
                  "largest-contentful-paint": {
                    "numericValue": 1850.5,
                    "displayValue": "1.9 s"
                  },
                  "cumulative-layout-shift": {"numericValue": 0.04}
                }
              }
            }
            """.trimIndent().encodeToByteArray(),
            PageSpeedStrategy.MOBILE,
        )
        val crux = parser.parseCrux(
            """
            {
              "record": {
                "metrics": {
                  "largest_contentful_paint": {"percentiles": {"p75": 2100}},
                  "interaction_to_next_paint": {"percentiles": {"p75": "180"}},
                  "cumulative_layout_shift": {"percentiles": {"p75": 0.07}}
                }
              }
            }
            """.trimIndent().encodeToByteArray(),
        )

        assertEquals(6, insights.size)
        assertEquals(92.0, insights.first { it.key == "pagespeed.mobile.performance" }.value, 0.001)
        assertEquals("1.9 s", insights.first { it.key.endsWith("largest-contentful-paint") }.formattedValue)
        assertEquals(3, crux.size)
        assertEquals(180.0, crux.first { it.key.endsWith("interaction_to_next_paint") }.value, 0.001)
    }

    @Test
    fun missingCanonicalPayloadsAreRejected() {
        assertThrows(PageSpeedResponseFormatException::class.java) {
            parser.parseInsights("{}".encodeToByteArray(), PageSpeedStrategy.MOBILE)
        }
        assertThrows(PageSpeedResponseFormatException::class.java) {
            parser.parseCrux("{\"record\":{}}".encodeToByteArray())
        }
    }
}
