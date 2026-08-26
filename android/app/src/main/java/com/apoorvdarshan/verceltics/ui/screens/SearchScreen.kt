package com.apoorvdarshan.verceltics.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.apoorvdarshan.verceltics.domain.IntegrationCatalog
import com.apoorvdarshan.verceltics.domain.IntegrationProvider
import com.apoorvdarshan.verceltics.ui.components.ControlSearchField
import com.apoorvdarshan.verceltics.ui.components.OffsetPanel
import com.apoorvdarshan.verceltics.ui.components.SectionHeading

@Composable
fun SearchScreen(
    onProviderClick: (IntegrationProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val results = IntegrationCatalog.search(query)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LazyColumn(
        modifier = modifier.testTag("globalSearch"),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "heading") {
            SectionHeading(
                eyebrow = "All 27 integrations",
                title = "Find anything",
            )
        }
        item(key = "search") {
            ControlSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search providers or capabilities",
                modifier = Modifier.fillMaxWidth(),
                testTag = "globalSearch.field",
                focusRequester = focusRequester,
            )
        }
        item(key = "resultHeading") {
            Text(
                text = when {
                    query.isBlank() -> "ALL PROVIDERS"
                    results.size == 1 -> "1 RESULT"
                    else -> "${results.size} RESULTS"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (results.isEmpty()) {
            item(key = "empty") {
                OffsetPanel(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "No integration found",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Try a provider, feature, or credential type.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            items(results, key = IntegrationProvider::id) { provider ->
                ProviderCard(provider = provider, onClick = { onProviderClick(provider) })
            }
        }
    }
}
