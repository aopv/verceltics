package com.apoorvdarshan.verceltics.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.verceltics.domain.AuthenticationModeMetadata
import com.apoorvdarshan.verceltics.domain.CredentialField
import com.apoorvdarshan.verceltics.domain.IntegrationProvider
import com.apoorvdarshan.verceltics.ui.VercelDashboardUi
import com.apoorvdarshan.verceltics.ui.VercelProjectUi
import com.apoorvdarshan.verceltics.ui.VercelUiGateway
import com.apoorvdarshan.verceltics.ui.components.LabelChip
import com.apoorvdarshan.verceltics.ui.components.OffsetPanel
import com.apoorvdarshan.verceltics.ui.components.ProviderMark
import com.apoorvdarshan.verceltics.ui.components.ControlSearchField
import com.apoorvdarshan.verceltics.ui.components.SectionHeading
import com.apoorvdarshan.verceltics.ui.components.StatusPill
import com.apoorvdarshan.verceltics.ui.components.contrastingContentColor
import com.apoorvdarshan.verceltics.ui.theme.VercelticsColors
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun ProviderDetailScreen(
    provider: IntegrationProvider,
    vercelGateway: VercelUiGateway,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag("providerDetail.${provider.id}"),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            DetailHeader(provider = provider, onBack = onBack)
        }
        item(key = "hero") {
            ProviderHero(provider)
        }
        if (provider.id == "vercel") {
            item(key = "vercel") {
                VercelConnectionPanel(gateway = vercelGateway)
            }
        } else {
            item(key = "placeholder") {
                ProviderConnectPlaceholder(provider)
            }
        }
        item(key = "authHeading") {
            SectionHeading(
                eyebrow = "Secure connection",
                title = "Authentication",
            )
        }
        items(provider.authenticationModes, key = AuthenticationModeMetadata::id) { mode ->
            AuthenticationCard(mode)
        }
    }
}

@Composable
private fun DetailHeader(
    provider: IntegrationProvider,
    onBack: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onBack()
            },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("providerDetail.back"),
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            Spacer(Modifier.width(7.dp))
            Text("BACK", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = provider.workspace.displayName.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProviderHero(provider: IntegrationProvider) {
    val accent = Color(provider.accentColor)
    val accentContent = contrastingContentColor(accent)
    OffsetPanel(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 188.dp),
        color = accent,
        testTag = "providerDetail.hero",
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                ProviderMark(
                    provider = provider,
                    icon = providerIcon(provider),
                    size = 68.dp,
                )
                LabelChip(
                    text = "Native Android",
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                )
            }
            Column {
                Text(
                    text = provider.displayName,
                    style = MaterialTheme.typography.displayMedium,
                    color = accentContent,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = accentContent.copy(alpha = 0.82f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProviderConnectPlaceholder(provider: IntegrationProvider) {
    OffsetPanel(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 176.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        testTag = "providerDetail.connectPlaceholder",
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Connection coming next", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${provider.displayName} stays read-only during this migration slice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text("CONNECT ${provider.displayName.uppercase()}")
            }
        }
    }
}

@Composable
private fun AuthenticationCard(mode: AuthenticationModeMetadata) {
    OffsetPanel(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(mode.displayName, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = when {
                    mode.requiredFields.isEmpty() -> "Provider sign-in; no credential is pasted into the app."
                    else -> "Required: ${mode.requiredFields.joinToString { credentialName(it) }}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mode.optionalFields.isNotEmpty()) {
                Text(
                    text = "Optional: ${mode.optionalFields.joinToString { credentialName(it) }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            mode.notes?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private data class VercelPanelState(
    val loading: Boolean = true,
    val dashboard: VercelDashboardUi? = null,
    val error: String? = null,
)

@Composable
private fun VercelConnectionPanel(gateway: VercelUiGateway) {
    ProtectCredentialWindow()
    var state by remember(gateway) { mutableStateOf(VercelPanelState()) }
    // Credentials must never be serialized into Android saved instance state.
    var token by remember { mutableStateOf("") }
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var showDisconnectConfirmation by rememberSaveable { mutableStateOf(false) }
    var projectQuery by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    fun messageOf(throwable: Throwable): String =
        throwable.message?.takeIf(String::isNotBlank) ?: "The request could not be completed."

    LaunchedEffect(gateway) {
        state = VercelPanelState(loading = true)
        gateway.restore().fold(
            onSuccess = { state = VercelPanelState(loading = false, dashboard = it) },
            onFailure = { state = VercelPanelState(loading = false, error = messageOf(it)) },
        )
    }

    if (showDisconnectConfirmation) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirmation = false },
            shape = RoundedCornerShape(7.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            title = { Text("Disconnect Vercel?", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("The saved token will be removed from this Android device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDisconnectConfirmation = false
                        projectQuery = ""
                        state = state.copy(loading = true, error = null)
                        scope.launch {
                            gateway.disconnect().fold(
                                onSuccess = { state = VercelPanelState(loading = false) },
                                onFailure = {
                                    state = state.copy(loading = false, error = messageOf(it))
                                },
                            )
                        }
                    },
                ) { Text("DISCONNECT") }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    showDisconnectConfirmation = false
                }) { Text("KEEP ACCOUNT") }
            },
        )
    }

    OffsetPanel(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        testTag = "vercel.connection",
    ) {
        Column(Modifier.padding(18.dp)) {
            when {
                state.dashboard != null -> ConnectedVercelContent(
                    state = state,
                    projectQuery = projectQuery,
                    onProjectQueryChange = { projectQuery = it },
                    onRefresh = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        state = state.copy(loading = true, error = null)
                        scope.launch {
                            gateway.refresh().fold(
                                onSuccess = {
                                    state = VercelPanelState(loading = false, dashboard = it)
                                },
                                onFailure = {
                                    state = state.copy(loading = false, error = messageOf(it))
                                },
                            )
                        }
                    },
                    onDisconnect = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        showDisconnectConfirmation = true
                    },
                )

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Connect Vercel", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Use a personal access token. It never appears again after saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vercel.token"),
                        enabled = !state.loading,
                        singleLine = true,
                        label = { Text("Personal access token") },
                        visualTransformation = if (tokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                tokenVisible = !tokenVisible
                                haptic.performHapticFeedback(
                                    if (tokenVisible) {
                                        HapticFeedbackType.ToggleOn
                                    } else {
                                        HapticFeedbackType.ToggleOff
                                    },
                                )
                            }) {
                                Icon(
                                    if (tokenVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (tokenVisible) "Hide token" else "Show token",
                                )
                            }
                        },
                        shape = RoundedCornerShape(5.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (token.isBlank()) {
                                state = state.copy(error = "Enter a Vercel personal access token.")
                            } else {
                                val pendingToken = token.trim()
                                state = state.copy(loading = true, error = null)
                                scope.launch {
                                    gateway.connect(pendingToken).fold(
                                        onSuccess = {
                                            token = ""
                                            state = VercelPanelState(loading = false, dashboard = it)
                                        },
                                        onFailure = {
                                            state = state.copy(loading = false, error = messageOf(it))
                                        },
                                    )
                                }
                            }
                        },
                        enabled = !state.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("vercel.connect"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(9.dp))
                        }
                        Text(if (state.loading) "CHECKING TOKEN" else "CONNECT SECURELY")
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                ErrorNotice(it)
            }
        }
    }
}

@Composable
private fun ConnectedVercelContent(
    state: VercelPanelState,
    projectQuery: String,
    onProjectQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val dashboard = requireNotNull(state.dashboard)
    val visibleProjects = remember(dashboard.projects, projectQuery) {
        val normalizedQuery = projectQuery.trim()
        if (normalizedQuery.isEmpty()) {
            dashboard.projects
        } else {
            dashboard.projects.filter { project ->
                project.name.contains(normalizedQuery, ignoreCase = true) ||
                    project.framework?.contains(normalizedQuery, ignoreCase = true) == true
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            StatusPill(text = "Connected", color = VercelticsColors.success)
            Spacer(Modifier.height(10.dp))
            Text(
                text = dashboard.account.displayName,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            dashboard.account.email?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(
            onClick = onRefresh,
            enabled = !state.loading,
            modifier = Modifier.testTag("vercel.refresh"),
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Vercel projects")
        }
    }

    if (state.loading) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(18.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("PROJECTS", style = MaterialTheme.typography.labelMedium)
        LabelChip(text = "${visibleProjects.size}/${dashboard.projects.size}")
    }
    Spacer(Modifier.height(8.dp))
    if (dashboard.projects.size > 1) {
        ControlSearchField(
            value = projectQuery,
            onValueChange = onProjectQueryChange,
            placeholder = "Search Vercel projects",
            modifier = Modifier.fillMaxWidth(),
            testTag = "vercel.projects.search",
        )
        Spacer(Modifier.height(10.dp))
    }
    if (dashboard.projects.isEmpty()) {
        Text(
            text = "No projects were returned for this account.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else if (visibleProjects.isEmpty()) {
        Text(
            text = "No Vercel project matches “$projectQuery”.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            visibleProjects.forEach { project ->
                VercelProjectRow(project)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    OutlinedButton(
        onClick = onDisconnect,
        enabled = !state.loading,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vercel.disconnect"),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text("DISCONNECT ACCOUNT")
    }
}

@Composable
private fun VercelProjectRow(project: VercelProjectUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = VercelticsColors.success,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = project.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    project.framework,
                    project.updatedAtMillis?.let(::formattedDate),
                ).joinToString(" · ").ifBlank { "Project ${project.id.take(8)}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorNotice(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Assertive }
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "!",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ProtectCredentialWindow() {
    val context = LocalView.current.context
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun credentialName(field: CredentialField): String = field.name
    .lowercase()
    .replace('_', ' ')

private fun formattedDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
