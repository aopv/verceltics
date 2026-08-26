package com.apoorvdarshan.verceltics.data.vercel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVercelJsonParserTest {
    private val parser = AndroidVercelJsonParser()

    @Test
    fun parsesCurrentOverviewWithoutRequiringLegacyBounceRate() {
        val overview = parser.parseAnalyticsOverview(
            """{\"total\":12806,\"devices\":2104,\"extra\":\"ignored\"}""".encodeToByteArray(),
        )

        assertEquals(12_806L, overview.pageViews)
        assertEquals(2_104L, overview.visitors)
        assertNull(overview.bounceRate)
    }

    @Test
    fun parsesAllAndBreakdownTimeseriesGroups() {
        val timeseries = parser.parseAnalyticsTimeseries(
            """
            {
              "data": {
                "groups": {
                  "all": [
                    {"key":"2026-08-26","total":100,"devices":75,"bounceRate":41}
                  ],
                  "/": [
                    {"key":"2026-08-26","total":80,"devices":60}
                  ]
                }
              }
            }
            """.trimIndent().encodeToByteArray(),
        )

        assertEquals(100L, timeseries.groups.getValue("all").single().pageViews)
        assertEquals(60L, timeseries.groups.getValue("/").single().visitors)
    }

    @Test
    fun rejectsIncompleteAnalyticsInsteadOfInventingZeroMetrics() {
        assertThrows(VercelResponseFormatException::class.java) {
            parser.parseAnalyticsOverview("""{\"total\":20}""".encodeToByteArray())
        }
        assertThrows(VercelResponseFormatException::class.java) {
            parser.parseAnalyticsTimeseries("""{\"data\":{}}""".encodeToByteArray())
        }
    }

    @Test
    fun parsesTeamMembershipAndProjectAccountScope() {
        val teams = parser.parseTeams(
            """
            {
              "teams": [
                {
                  "id":"team_123",
                  "slug":"verceltics",
                  "name":"Verceltics",
                  "membership":{"confirmed":true,"role":"OWNER"}
                }
              ],
              "pagination":{"next":1234}
            }
            """.trimIndent().encodeToByteArray(),
        )
        val projects = parser.parseProjects(
            """
            {
              "projects": [
                {"id":"prj_123","name":"app","accountId":"team_123"}
              ],
              "pagination":{"next":null}
            }
            """.trimIndent().encodeToByteArray(),
        )

        assertTrue(teams.teams.single().isConfirmedMember)
        assertEquals("1234", teams.nextCursor)
        assertEquals("team_123", projects.projects.single().teamId)
    }
}
