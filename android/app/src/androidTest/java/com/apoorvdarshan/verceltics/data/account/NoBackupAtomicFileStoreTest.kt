package com.apoorvdarshan.verceltics.data.account

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoBackupAtomicFileStoreTest {
    @Test
    fun repositoryInitializationIsIdempotent() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        repeat(4) {
            VercelAccountRepository.create(context)
        }
    }
}
