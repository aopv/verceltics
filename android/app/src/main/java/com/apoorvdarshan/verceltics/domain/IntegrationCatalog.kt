package com.apoorvdarshan.verceltics.domain

import java.text.Normalizer
import java.util.Locale

object IntegrationCatalog {
    val all: List<IntegrationProvider> = listOf(
        // Hosting
        provider(
            id = "vercel",
            workspace = Workspace.HOSTING,
            displayName = "Vercel",
            description = "Projects, deployments and Web Analytics",
            accentColor = 0xFF0A0A0A,
            authMode(
                id = "personal-access-token",
                displayName = "Personal access token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
        ),
        provider(
            id = "cloudflare",
            workspace = Workspace.HOSTING,
            displayName = "Cloudflare",
            description = "Zones, Pages, Workers, DNS and analytics",
            accentColor = 0xFFF26B14,
            authMode(
                id = "api-token",
                displayName = "Scoped API token",
                requiredFields = setOf(CredentialField.TOKEN),
                notes = "Preferred mode; permissions are limited by the token's scopes.",
            ),
            authMode(
                id = "global-api-key",
                displayName = "Email and Global API Key",
                requiredFields = setOf(CredentialField.EMAIL, CredentialField.API_KEY),
            ),
        ),
        provider(
            id = "netlify",
            workspace = Workspace.HOSTING,
            displayName = "Netlify",
            description = "Sites, deploys, domains and build controls",
            accentColor = 0xFF2ED1C7,
            authMode(
                id = "personal-access-token",
                displayName = "Personal access token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
        ),
        provider(
            id = "railway",
            workspace = Workspace.HOSTING,
            displayName = "Railway",
            description = "Projects, services, environments and logs",
            accentColor = 0xFFAB7AFA,
            authMode(
                id = "account-workspace-token",
                displayName = "Account or workspace token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
            authMode(
                id = "project-token",
                displayName = "Project token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
        ),
        provider(
            id = "render",
            workspace = Workspace.HOSTING,
            displayName = "Render",
            description = "Services, deploys, jobs and environments",
            accentColor = 0xFF6178FF,
            authMode(
                id = "api-key",
                displayName = "API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
        ),
        provider(
            id = "digitalOcean",
            workspace = Workspace.HOSTING,
            displayName = "DigitalOcean",
            description = "Apps, deployments, logs and bandwidth",
            accentColor = 0xFF0075F2,
            authMode(
                id = "personal-access-token",
                displayName = "Personal access token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
            searchAliases = setOf("Digital Ocean"),
        ),
        provider(
            id = "heroku",
            workspace = Workspace.HOSTING,
            displayName = "Heroku",
            description = "Apps, releases, dynos, domains and logs",
            accentColor = 0xFF8C57D6,
            authMode(
                id = "api-token",
                displayName = "API token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
        ),
        provider(
            id = "fly",
            workspace = Workspace.HOSTING,
            displayName = "Fly.io",
            description = "Apps, Machines, regions and volumes",
            accentColor = 0xFF87A3FF,
            authMode(
                id = "access-token",
                displayName = "Access token and organization",
                requiredFields = setOf(CredentialField.TOKEN, CredentialField.ORGANIZATION),
            ),
            searchAliases = setOf("Flyio", "Fly"),
        ),
        provider(
            id = "firebase",
            workspace = Workspace.HOSTING,
            displayName = "Firebase Hosting",
            description = "Hosting sites, channels, versions and releases",
            accentColor = 0xFFFFAD1F,
            authMode(
                id = "google-oauth",
                displayName = "Google OAuth",
                requiredFields = setOf(CredentialField.PROJECT_ID),
                notes = "Uses a Google account with access to the Firebase project.",
            ),
            searchAliases = setOf("Firebase", "Google Firebase"),
        ),
        provider(
            id = "awsAmplify",
            workspace = Workspace.HOSTING,
            displayName = "AWS Amplify",
            description = "Apps, branches, jobs and domains",
            accentColor = 0xFFFF991F,
            authMode(
                id = "aws-access-key",
                displayName = "AWS access key",
                requiredFields = setOf(
                    CredentialField.ACCESS_KEY_ID,
                    CredentialField.SECRET_ACCESS_KEY,
                    CredentialField.REGION,
                ),
                optionalFields = setOf(CredentialField.SESSION_TOKEN),
            ),
            searchAliases = setOf("Amazon Web Services Amplify"),
        ),

        // Registrars
        provider(
            id = "nameDotCom",
            workspace = Workspace.REGISTRARS,
            displayName = "Name.com",
            description = "Domains, DNS, renewals, transfers and privacy",
            accentColor = 0xFF298CF5,
            authMode(
                id = "username-api-token",
                displayName = "API username and token",
                requiredFields = setOf(CredentialField.USERNAME, CredentialField.TOKEN),
                notes = "API Access must be enabled when two-step verification is active.",
            ),
            searchAliases = setOf("Name dot com", "Namecom"),
        ),
        provider(
            id = "namecheap",
            workspace = Workspace.REGISTRARS,
            displayName = "Namecheap",
            description = "Domains, DNS, contacts, renewals and transfers",
            accentColor = 0xFFFF5E1F,
            authMode(
                id = "username-api-key-client-ip",
                displayName = "Username, API key and client IP",
                requiredFields = setOf(
                    CredentialField.USERNAME,
                    CredentialField.API_KEY,
                    CredentialField.CLIENT_IP,
                ),
                notes = "The public IPv4 address must also be allowlisted with Namecheap.",
            ),
        ),
        provider(
            id = "porkbun",
            workspace = Workspace.REGISTRARS,
            displayName = "Porkbun",
            description = "Domains, DNS, SSL, forwarding and marketplace",
            accentColor = 0xFFF25E87,
            authMode(
                id = "api-key-secret",
                displayName = "API key and secret",
                requiredFields = setOf(CredentialField.API_KEY, CredentialField.API_SECRET),
            ),
        ),
        provider(
            id = "spaceship",
            workspace = Workspace.REGISTRARS,
            displayName = "Spaceship",
            description = "Domains, contacts, DNS and nameservers",
            accentColor = 0xFF806BFA,
            authMode(
                id = "api-key-secret",
                displayName = "API key and secret",
                requiredFields = setOf(CredentialField.API_KEY, CredentialField.API_SECRET),
            ),
        ),
        provider(
            id = "dynadot",
            workspace = Workspace.REGISTRARS,
            displayName = "Dynadot",
            description = "Domains, DNS, renewals, auctions and aftermarket",
            accentColor = 0xFF33B3EB,
            authMode(
                id = "api-key",
                displayName = "API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
        ),
        provider(
            id = "nameSilo",
            workspace = Workspace.REGISTRARS,
            displayName = "NameSilo",
            description = "Domains, DNS, renewals, contacts and transfers",
            accentColor = 0xFF24B88C,
            authMode(
                id = "api-key",
                displayName = "API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
            searchAliases = setOf("Name Silo"),
        ),
        provider(
            id = "gandi",
            workspace = Workspace.REGISTRARS,
            displayName = "Gandi",
            description = "Domains, LiveDNS, certificates, mail and billing",
            accentColor = 0xFF6B61EB,
            authMode(
                id = "personal-access-token",
                displayName = "Personal access token",
                requiredFields = setOf(CredentialField.TOKEN),
                optionalFields = setOf(CredentialField.ORGANIZATION),
            ),
            searchAliases = setOf("Gandi LiveDNS"),
        ),
        provider(
            id = "goDaddy",
            workspace = Workspace.REGISTRARS,
            displayName = "GoDaddy",
            description = "Domains, DNS, renewals, privacy and transfers",
            accentColor = 0xFF1FB8A1,
            authMode(
                id = "api-key-secret",
                displayName = "API key and secret",
                requiredFields = setOf(CredentialField.API_KEY, CredentialField.API_SECRET),
            ),
            searchAliases = setOf("Go Daddy"),
        ),

        // Site services
        provider(
            id = "googleSearchConsole",
            workspace = Workspace.SITES,
            displayName = "Google Search Console",
            description = "Search performance, indexing, sitemaps and URL inspection",
            accentColor = 0xFF4085F5,
            authMode(
                id = "google-oauth",
                displayName = "Google OAuth",
                requiredFields = emptySet(),
                notes = "Requests the documented read-only Search Console scopes.",
            ),
            searchAliases = setOf("GSC", "Search Console"),
        ),
        provider(
            id = "googleAnalytics",
            workspace = Workspace.SITES,
            displayName = "Google Analytics",
            description = "GA4 visitors, sessions, traffic, events and realtime",
            accentColor = 0xFFF5911F,
            authMode(
                id = "google-oauth",
                displayName = "Google OAuth",
                requiredFields = emptySet(),
                notes = "Requests the documented read-only Google Analytics scopes.",
            ),
            searchAliases = setOf("GA4", "Analytics"),
        ),
        provider(
            id = "pageSpeed",
            workspace = Workspace.SITES,
            displayName = "PageSpeed & CrUX",
            description = "Lighthouse audits and Chrome UX field data",
            accentColor = 0xFF4FBD7A,
            authMode(
                id = "google-cloud-api-key",
                displayName = "Google Cloud API key",
                requiredFields = setOf(CredentialField.SITE_URL, CredentialField.API_KEY),
            ),
            searchAliases = setOf("Page Speed", "Chrome UX Report", "Lighthouse"),
        ),
        provider(
            id = "bingWebmaster",
            workspace = Workspace.SITES,
            displayName = "Bing Webmaster",
            description = "Bing search traffic, crawling and verified sites",
            accentColor = 0xFF00ADAD,
            authMode(
                id = "api-key",
                displayName = "API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
            searchAliases = setOf("Bing Webmaster Tools"),
        ),
        provider(
            id = "clarity",
            workspace = Workspace.SITES,
            displayName = "Microsoft Clarity",
            description = "Behavioral insights, sessions and interaction signals",
            accentColor = 0xFF3385F2,
            authMode(
                id = "bearer-token",
                displayName = "Export bearer token",
                requiredFields = setOf(CredentialField.PROJECT_NAME, CredentialField.TOKEN),
                optionalFields = setOf(CredentialField.SITE_URL),
            ),
            searchAliases = setOf("Clarity"),
        ),
        provider(
            id = "plausible",
            workspace = Workspace.SITES,
            displayName = "Plausible",
            description = "Privacy-friendly visitors, visits, views and engagement",
            accentColor = 0xFF6B5CE6,
            authMode(
                id = "stats-api-key",
                displayName = "Stats API key",
                requiredFields = setOf(CredentialField.SITE_ID, CredentialField.API_KEY),
            ),
            searchAliases = setOf("Plausible Analytics"),
        ),
        provider(
            id = "umami",
            workspace = Workspace.SITES,
            displayName = "Umami",
            description = "30-day traffic across Cloud or self-hosted sites",
            accentColor = 0xFF9473F0,
            authMode(
                id = "cloud-api-key",
                displayName = "Umami Cloud API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
            authMode(
                id = "self-hosted-bearer-token",
                displayName = "Self-hosted bearer token",
                requiredFields = setOf(CredentialField.BASE_URL, CredentialField.TOKEN),
                notes = "The base URL must use HTTPS.",
            ),
            searchAliases = setOf("Umami Cloud", "Self hosted analytics"),
        ),
        provider(
            id = "uptimeRobot",
            workspace = Workspace.SITES,
            displayName = "UptimeRobot",
            description = "Monitor state, uptime ratios and response time",
            accentColor = 0xFF30C294,
            authMode(
                id = "read-only-api-key",
                displayName = "Read-only API key",
                requiredFields = setOf(CredentialField.API_KEY),
            ),
            searchAliases = setOf("Uptime Robot", "Uptime monitoring"),
        ),
        provider(
            id = "betterStack",
            workspace = Workspace.SITES,
            displayName = "Better Stack",
            description = "Monitor state, check cadence and availability",
            accentColor = 0xFFFA574D,
            authMode(
                id = "api-token",
                displayName = "API token",
                requiredFields = setOf(CredentialField.TOKEN),
            ),
            searchAliases = setOf("BetterStack", "Better Uptime"),
        ),
    )

    private val providersById: Map<String, IntegrationProvider> = all.associateBy { it.id }
    private val providersByNormalizedId: Map<String, IntegrationProvider> =
        all.associateBy { it.id.normalizeForSearch() }

    init {
        require(providersById.size == all.size) { "Integration provider ids must be unique." }
    }

    fun providers(workspace: Workspace): List<IntegrationProvider> =
        all.filter { it.workspace == workspace }

    fun provider(id: String): IntegrationProvider? = providersByNormalizedId[id.normalizeForSearch()]

    fun search(
        query: String,
        workspace: Workspace? = null,
    ): List<IntegrationProvider> {
        val candidates = workspace?.let(::providers) ?: all
        val terms = query.normalizeForSearch().split(' ').filter(String::isNotEmpty)
        if (terms.isEmpty()) return candidates

        return candidates.filter { provider ->
            val searchableText = buildString {
                append(provider.id)
                append(' ')
                append(provider.workspace.id)
                append(' ')
                append(provider.workspace.displayName)
                append(' ')
                append(provider.displayName)
                append(' ')
                append(provider.description)
                provider.searchAliases.forEach {
                    append(' ')
                    append(it)
                }
                provider.authenticationModes.forEach {
                    append(' ')
                    append(it.id)
                    append(' ')
                    append(it.displayName)
                }
            }.normalizeForSearch()

            terms.all(searchableText::contains)
        }
    }

    internal fun normalizeSearchText(value: String): String = value.normalizeForSearch()

    private fun provider(
        id: String,
        workspace: Workspace,
        displayName: String,
        description: String,
        accentColor: Long,
        vararg authenticationModes: AuthenticationModeMetadata,
        searchAliases: Set<String> = emptySet(),
    ) = IntegrationProvider(
        id = id,
        workspace = workspace,
        displayName = displayName,
        description = description,
        accentColor = accentColor,
        authenticationModes = authenticationModes.toList(),
        searchAliases = searchAliases,
    )

    private fun authMode(
        id: String,
        displayName: String,
        requiredFields: Set<CredentialField>,
        optionalFields: Set<CredentialField> = emptySet(),
        notes: String? = null,
    ) = AuthenticationModeMetadata(
        id = id,
        displayName = displayName,
        requiredFields = requiredFields,
        optionalFields = optionalFields,
        notes = notes,
    )
}

private val nonSearchCharacter = Regex("[^\\p{L}\\p{N}]+")
private val combiningMark = Regex("\\p{M}+")
private val repeatedWhitespace = Regex("\\s+")

private fun String.normalizeForSearch(): String = Normalizer
    .normalize(replace("&", " and "), Normalizer.Form.NFKD)
    .replace(combiningMark, "")
    .lowercase(Locale.ROOT)
    .replace(nonSearchCharacter, " ")
    .replace(repeatedWhitespace, " ")
    .trim()
