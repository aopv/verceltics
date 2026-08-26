package com.apoorvdarshan.verceltics

import android.app.Application
import com.apoorvdarshan.verceltics.ui.NativeVercelUiGateway

class VercelticsApplication : Application() {
    val vercelGateway: NativeVercelUiGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        NativeVercelUiGateway.create(this)
    }
}
