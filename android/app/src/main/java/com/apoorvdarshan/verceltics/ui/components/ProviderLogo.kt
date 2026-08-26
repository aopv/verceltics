package com.apoorvdarshan.verceltics.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import com.apoorvdarshan.verceltics.R
import com.apoorvdarshan.verceltics.domain.IntegrationProvider

/** Describes whether a canonical provider mark keeps its artwork or uses the provider accent. */
enum class ProviderLogoRendering {
    Original,
    ProviderTint,
}

@Immutable
data class ProviderLogoSpec(
    @param:DrawableRes @field:DrawableRes val drawableResource: Int,
    val rendering: ProviderLogoRendering,
)

private val ProviderLogoSpecs = mapOf(
    "vercel" to ProviderLogoSpec(R.drawable.provider_logo_vercel, ProviderLogoRendering.ProviderTint),
    "cloudflare" to ProviderLogoSpec(R.drawable.provider_logo_cloudflare, ProviderLogoRendering.Original),
    "netlify" to ProviderLogoSpec(R.drawable.provider_logo_netlify, ProviderLogoRendering.Original),
    "railway" to ProviderLogoSpec(R.drawable.provider_logo_railway, ProviderLogoRendering.ProviderTint),
    "render" to ProviderLogoSpec(R.drawable.provider_logo_render, ProviderLogoRendering.ProviderTint),
    "digitalOcean" to ProviderLogoSpec(R.drawable.provider_logo_digital_ocean, ProviderLogoRendering.Original),
    "heroku" to ProviderLogoSpec(R.drawable.provider_logo_heroku, ProviderLogoRendering.ProviderTint),
    "fly" to ProviderLogoSpec(R.drawable.provider_logo_fly, ProviderLogoRendering.ProviderTint),
    "firebase" to ProviderLogoSpec(R.drawable.provider_logo_firebase, ProviderLogoRendering.Original),
    "awsAmplify" to ProviderLogoSpec(R.drawable.provider_logo_aws_amplify, ProviderLogoRendering.Original),
    "nameDotCom" to ProviderLogoSpec(R.drawable.provider_logo_name_dot_com, ProviderLogoRendering.Original),
    "namecheap" to ProviderLogoSpec(R.drawable.provider_logo_namecheap, ProviderLogoRendering.Original),
    "porkbun" to ProviderLogoSpec(R.drawable.provider_logo_porkbun, ProviderLogoRendering.Original),
    "spaceship" to ProviderLogoSpec(R.drawable.provider_logo_spaceship, ProviderLogoRendering.Original),
    "dynadot" to ProviderLogoSpec(R.drawable.provider_logo_dynadot, ProviderLogoRendering.ProviderTint),
    "nameSilo" to ProviderLogoSpec(R.drawable.provider_logo_name_silo, ProviderLogoRendering.ProviderTint),
    "gandi" to ProviderLogoSpec(R.drawable.provider_logo_gandi, ProviderLogoRendering.Original),
    "goDaddy" to ProviderLogoSpec(R.drawable.provider_logo_go_daddy, ProviderLogoRendering.Original),
    "googleSearchConsole" to ProviderLogoSpec(
        R.drawable.provider_logo_google_search_console,
        ProviderLogoRendering.Original,
    ),
    "googleAnalytics" to ProviderLogoSpec(
        R.drawable.provider_logo_google_analytics,
        ProviderLogoRendering.Original,
    ),
    "pageSpeed" to ProviderLogoSpec(R.drawable.provider_logo_page_speed, ProviderLogoRendering.Original),
    "bingWebmaster" to ProviderLogoSpec(
        R.drawable.provider_logo_bing_webmaster,
        ProviderLogoRendering.ProviderTint,
    ),
    "clarity" to ProviderLogoSpec(R.drawable.provider_logo_microsoft_clarity, ProviderLogoRendering.Original),
    "plausible" to ProviderLogoSpec(R.drawable.provider_logo_plausible, ProviderLogoRendering.Original),
    "umami" to ProviderLogoSpec(R.drawable.provider_logo_umami, ProviderLogoRendering.ProviderTint),
    "uptimeRobot" to ProviderLogoSpec(R.drawable.provider_logo_uptime_robot, ProviderLogoRendering.Original),
    "betterStack" to ProviderLogoSpec(
        R.drawable.provider_logo_better_stack,
        ProviderLogoRendering.ProviderTint,
    ),
)

/** Every provider ID with a canonical Android mark, exposed for catalog coverage checks. */
val providerLogoProviderIds: Set<String> = ProviderLogoSpecs.keys

fun providerLogoSpec(providerId: String): ProviderLogoSpec? = ProviderLogoSpecs[providerId]

/**
 * Renders the canonical provider artwork copied from the iOS asset catalog.
 *
 * Keep [contentDescription] null when the provider name is already present next to the mark. Pass
 * a localized description when the image is the only accessible label. [monochrome] intentionally
 * tints every mark for compact navigation controls; otherwise only iOS-designated template marks
 * receive the provider accent. Vercel follows the current content color in dark mode instead of
 * disappearing as a black mark on a black surface.
 */
@Composable
fun ProviderLogo(
    provider: IntegrationProvider,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    monochrome: Boolean = false,
    tint: Color? = null,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val spec = providerLogoSpec(provider.id)
    if (spec == null) {
        ProviderLogoFallback(
            provider = provider,
            modifier = modifier,
            contentDescription = contentDescription,
        )
        return
    }

    val resolvedTint = when {
        tint != null -> tint
        monochrome -> MaterialTheme.colorScheme.onSurface
        spec.rendering == ProviderLogoRendering.ProviderTint && provider.id == "vercel" ->
            MaterialTheme.colorScheme.onSurface
        spec.rendering == ProviderLogoRendering.ProviderTint -> Color(provider.accentColor)
        else -> null
    }

    Image(
        painter = painterResource(spec.drawableResource),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        colorFilter = resolvedTint?.let(ColorFilter::tint),
    )
}

@Composable
private fun ProviderLogoFallback(
    provider: IntegrationProvider,
    modifier: Modifier,
    contentDescription: String?,
) {
    val semanticsModifier = if (contentDescription == null) {
        Modifier.clearAndSetSemantics { }
    } else {
        Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
    }
    Box(
        modifier = modifier.then(semanticsModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.displayName.take(1).uppercase(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Black,
        )
    }
}
