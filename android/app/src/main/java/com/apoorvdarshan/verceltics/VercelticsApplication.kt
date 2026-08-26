package com.apoorvdarshan.verceltics

import android.app.Application
import com.apoorvdarshan.verceltics.ui.NativeVercelUiGateway
import com.apoorvdarshan.verceltics.ui.pagespeed.NativePageSpeedUiGateway

class VercelticsApplication : Application() {
    val vercelGateway: NativeVercelUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativeVercelUiGateway.create(this)
    }

    val pageSpeedGateway: NativePageSpeedUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativePageSpeedUiGateway.create(this)
    }
}
