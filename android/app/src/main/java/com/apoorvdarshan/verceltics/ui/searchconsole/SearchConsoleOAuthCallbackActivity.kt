package com.apoorvdarshan.verceltics.ui.searchconsole

import android.app.Activity
import android.os.Bundle
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleOAuthCallbackBroker
import com.apoorvdarshan.verceltics.data.searchconsole.SearchConsoleOAuthClientConfiguration
import com.apoorvdarshan.verceltics.data.searchconsole.matchesOAuthRedirect
import java.net.URI

/** Receives only the one-use Google redirect and immediately returns it to the active PKCE flow. */
class SearchConsoleOAuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val expected = SearchConsoleOAuthClientConfiguration.current()
            ?.redirectUri
            ?.let(::URI)
        intent?.data?.toString()?.let { raw ->
            runCatching { URI(raw) }.getOrNull()
                ?.takeIf { callback -> expected != null && callback.matchesOAuthRedirect(expected) }
                ?.let(SearchConsoleOAuthCallbackBroker::deliver)
        }
        finish()
    }
}
