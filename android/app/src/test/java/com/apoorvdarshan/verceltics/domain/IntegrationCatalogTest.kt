package com.apoorvdarshan.verceltics.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationCatalogTest {
    @Test
    fun `catalog contains all twenty seven integrations`() {
        assertEquals(27, IntegrationCatalog.all.size)
        assertEquals(10, IntegrationCatalog.providers(Workspace.HOSTING).size)
        assertEquals(8, IntegrationCatalog.providers(Workspace.REGISTRARS).size)
        assertEquals(9, IntegrationCatalog.providers(Workspace.SITES).size)
    }

    @Test
    fun `provider ids and workspace ids are unique`() {
        val providerIds = IntegrationCatalog.all.map(IntegrationProvider::id)
        assertEquals(providerIds.size, providerIds.toSet().size)

        val workspaceIds = Workspace.entries.map(Workspace::id)
        assertEquals(workspaceIds.size, workspaceIds.toSet().size)
    }

    @Test
    fun `every provider is returned only by its workspace group`() {
        Workspace.entries.forEach { workspace ->
            val grouped = IntegrationCatalog.providers(workspace)
            assertTrue(grouped.isNotEmpty())
            assertTrue(grouped.all { it.workspace == workspace })
        }

        val groupedIds = Workspace.entries
            .flatMap(IntegrationCatalog::providers)
            .map(IntegrationProvider::id)

        assertEquals(IntegrationCatalog.all.map(IntegrationProvider::id), groupedIds)
    }

    @Test
    fun `normalized search ignores case punctuation accents and spacing`() {
        assertEquals(
            listOf("fly"),
            IntegrationCatalog.search("  FLY.IO  ").map(IntegrationProvider::id),
        )
        assertEquals(
            listOf("nameDotCom"),
            IntegrationCatalog.search("name com").map(IntegrationProvider::id),
        )
        assertEquals(
            listOf("pageSpeed"),
            IntegrationCatalog.search("PAGE SPEED & crux").map(IntegrationProvider::id),
        )
        assertEquals("cafe and dns", IntegrationCatalog.normalizeSearchText("  Café & DNS  "))
    }

    @Test
    fun `search uses descriptions aliases auth metadata and optional workspace scope`() {
        assertEquals(
            listOf("cloudflare"),
            IntegrationCatalog.search("global api key").map(IntegrationProvider::id),
        )
        assertEquals(
            listOf("googleAnalytics"),
            IntegrationCatalog.search("ga4 realtime", Workspace.SITES).map(IntegrationProvider::id),
        )
        assertEquals(
            listOf("uptimeRobot"),
            IntegrationCatalog.search("uptime monitoring", Workspace.SITES).map(IntegrationProvider::id),
        )
        assertTrue(IntegrationCatalog.search("ga4", Workspace.HOSTING).isEmpty())
    }

    @Test
    fun `provider lookup returns complete auth metadata`() {
        val cloudflare = IntegrationCatalog.provider("cloudflare")
        assertNotNull(cloudflare)
        assertEquals(setOf("api-token", "global-api-key"), cloudflare?.authenticationModes?.map { it.id }?.toSet())

        val namecheap = IntegrationCatalog.provider("namecheap")
        assertEquals(
            setOf(CredentialField.USERNAME, CredentialField.API_KEY, CredentialField.CLIENT_IP),
            namecheap?.authenticationModes?.single()?.requiredFields,
        )
    }
}
