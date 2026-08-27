package com.apoorvdarshan.verceltics

import android.app.Application
import com.apoorvdarshan.verceltics.ui.NativeVercelUiGateway
import com.apoorvdarshan.verceltics.ui.cloudflare.NativeCloudflareUiGateway
import com.apoorvdarshan.verceltics.ui.netlify.NativeNetlifyUiGateway
import com.apoorvdarshan.verceltics.ui.pagespeed.NativePageSpeedUiGateway

class VercelticsApplication : Application() {
    val vercelGateway: NativeVercelUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativeVercelUiGateway.create(this)
    }

    val pageSpeedGateway: NativePageSpeedUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativePageSpeedUiGateway.create(this)
    }

    val netlifyGateway: NativeNetlifyUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativeNetlifyUiGateway.create(this)
    }

    val cloudflareGateway: NativeCloudflareUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativeCloudflareUiGateway.create(this)
    }
}
