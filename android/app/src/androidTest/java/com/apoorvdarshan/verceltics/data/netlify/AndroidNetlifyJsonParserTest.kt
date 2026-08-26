package com.apoorvdarshan.verceltics.data.netlify

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNetlifyJsonParserTest {
    private val parser = AndroidNetlifyJsonParser()

    @Test
    fun parsesUserAndIosCompatibleSiteAndDeployFallbacks() {
        val user = parser.parseUser(
            """{"uid":"uid-1","full_name":"Apoorv","email":"a@example.com"}"""
                .encodeToByteArray(),
        )
        assertEquals("uid-1", user.uid)
        assertEquals("Apoorv", user.fullName)

        val sites = parser.parseSites(
            """
            [
              {
                "name":"verceltics",
                "custom_domain":"verceltics.app",
                "ssl_url":"https://verceltics.netlify.app",
                "published_deploy":true,
                "updated_at":"2026-08-27T10:00:00Z",
                "admin_url":"https://app.netlify.com/sites/verceltics"
              },
              {}
            ]
            """.trimIndent().encodeToByteArray(),
        )
        assertEquals(1, sites.size)
        assertTrue(sites.single().id.startsWith("netlify-site-"))
        assertEquals("Published", sites.single().status)

        val deploy = parser.parseDeployments(
            """[{"context":"production","state":"ready","commit_ref":"abc"}]"""
                .encodeToByteArray(),
        ).single()
        assertTrue(deploy.id.startsWith("netlify-deploy-"))
        assertEquals("production", deploy.title)
        assertEquals("abc", deploy.commitMessage)
    }

    @Test
    fun siteDetailsExposeDomainsAndReadOnlyBuildConfiguration() {
        val details = parser.parseSiteDetails(
            """
            {
              "id":"site-1",
              "name":"verceltics",
              "custom_domain":"verceltics.app",
              "domain_aliases":["www.verceltics.app", "verceltics.app"],
              "ssl_url":"https://verceltics.netlify.app",
              "stop_builds":false,
              "build_settings":{
                "repo_url":"https://github.com/example/verceltics",
                "repo_branch":"main",
                "base":"web",
                "dir":"dist",
                "functions_dir":"functions",
                "cmd":"npm run build",
                "allowed_branches":["main", "preview"],
                "provider":"github"
              },
              "published_deploy":{
                "id":"deploy-1",
                "title":"Production",
                "state":"ready",
                "published_at":"2026-08-27T10:00:00Z"
              }
            }
            """.trimIndent().encodeToByteArray(),
            expectedSiteId = "site-1",
        )

        assertEquals(
            listOf("verceltics.app", "www.verceltics.app", "verceltics.netlify.app"),
            details.domains.map { it.name },
        )
        assertFalse(checkNotNull(details.buildControls).buildsStopped == true)
        assertEquals("npm run build", details.buildControls?.buildCommand)
        assertEquals(listOf("main", "preview"), details.buildControls?.allowedBranches)
        assertEquals("deploy-1", details.publishedDeployment?.id)
        // Preserve the first-class iOS rule: an object is not treated as the Boolean status fallback.
        assertNull(details.site.status)
    }

    @Test
    fun parsesReadOnlyBuildHistoryWithoutInventingState() {
        val builds = parser.parseBuilds(
            """
            [
              {"id":"build-1","deploy_id":"deploy-1","sha":"abc","done":true,
               "created_at":"2026-08-27T10:00:00Z"},
              {"deploy_id":"deploy-2","error":"build failed"}
            ]
            """.trimIndent().encodeToByteArray(),
        )

        assertEquals(2, builds.size)
        assertTrue(builds.first().isDone == true)
        assertTrue(builds.last().id.startsWith("netlify-build-"))
        assertNull(builds.last().isDone)
    }
}
