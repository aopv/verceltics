package com.apoorvdarshan.verceltics.ui

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.account.VercelAccount
import com.apoorvdarshan.verceltics.data.account.VercelAccountRepository
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.vercel.VercelApi
import com.apoorvdarshan.verceltics.data.vercel.VercelProject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Production bridge between Compose state and the native Android Vercel implementation.
 *
 * Blocking provider calls run outside the main thread and are explicitly cancelled when the
 * requesting coroutine goes away. Credentials only cross this boundary as [SecretValue] and are
 * persisted by the Android Keystore-backed repository.
 */
class NativeVercelUiGateway private constructor(
    private val applicationContext: Context,
    private val api: VercelApi,
    private val executor: ExecutorService,
) : VercelUiGateway {
    private val accountRepository: VercelAccountRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        VercelAccountRepository.create(applicationContext)
    }

    override suspend fun restore(): Result<VercelRestoreUi> = capture {
        val account = executeAwait(executor) { accountRepository.load() }
            ?: return@capture VercelRestoreUi.NoSavedAccount
        try {
            VercelRestoreUi.Available(dashboard(account))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            VercelRestoreUi.DashboardUnavailable(
                account = account.toUi(),
                error = error,
            )
        }
    }

    override suspend fun connect(personalToken: String): Result<VercelDashboardUi> = capture {
        val secret = SecretValue.of(personalToken.trim())
        val user = api.newValidatePersonalTokenCall(secret).executeAwait(executor)
        val projects = api.newListProjectsCall(secret).executeAwait(executor)
        val account = api.accountForValidatedUser(user = user, token = secret)
        executeAwait(executor) { accountRepository.save(account) }
        VercelDashboardUi(
            account = account.toUi(),
            projects = projects.projects.map(VercelProject::toUi),
        )
    }

    override suspend fun refresh(): Result<VercelDashboardUi> = capture {
        val account = executeAwait(executor) { accountRepository.load() }
            ?: throw IllegalStateException("Connect a Vercel account first.")
        dashboard(account)
    }

    override suspend fun disconnect(): Result<Unit> = capture {
        executeAwait(executor) { accountRepository.delete() }
    }

    private suspend fun dashboard(account: VercelAccount): VercelDashboardUi {
        val projects = api.newListProjectsCall(account.token).executeAwait(executor)
        return VercelDashboardUi(
            account = account.toUi(),
            projects = projects.projects.map(VercelProject::toUi),
        )
    }

    private suspend inline fun <T> capture(crossinline operation: suspend () -> T): Result<T> =
        try {
            Result.success(operation())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }

    companion object {
        fun create(context: Context): NativeVercelUiGateway {
            val executor = Executors.newFixedThreadPool(2) { work ->
                Thread(work, "verceltics-provider").apply { isDaemon = true }
            }
            return NativeVercelUiGateway(
                applicationContext = context.applicationContext,
                api = VercelApi(),
                executor = executor,
            )
        }
    }
}

private suspend fun <T> CancelableCall<T>.executeAwait(executor: ExecutorService): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        executor.execute {
            try {
                val value = execute()
                if (continuation.isActive) continuation.resume(value)
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

private suspend fun <T> executeAwait(
    executor: ExecutorService,
    operation: () -> T,
): T = suspendCancellableCoroutine { continuation ->
    executor.execute {
        try {
            val value = operation()
            if (continuation.isActive) continuation.resume(value)
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

private fun VercelAccount.toUi(): VercelAccountUi = VercelAccountUi(
    displayName = displayName,
    email = email,
)

private fun VercelProject.toUi(): VercelProjectUi = VercelProjectUi(
    id = id,
    name = name,
    framework = framework,
    updatedAtMillis = updatedAtMillis,
)
