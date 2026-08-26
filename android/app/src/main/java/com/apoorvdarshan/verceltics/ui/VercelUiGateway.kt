package com.apoorvdarshan.verceltics.ui

/**
 * UI-facing boundary for the first Android provider slice.
 *
 * The Compose layer deliberately knows nothing about tokens at rest, HTTP clients, or JSON. A
 * native data adapter can implement this contract with Android Keystore-backed persistence and
 * the Vercel API without leaking those concerns into screen state.
 */
interface VercelUiGateway {
    suspend fun restore(): Result<VercelRestoreUi>

    suspend fun connect(personalToken: String): Result<VercelDashboardUi>

    suspend fun refresh(): Result<VercelDashboardUi>

    suspend fun disconnect(): Result<Unit>
}

sealed interface VercelRestoreUi {
    data object NoSavedAccount : VercelRestoreUi

    data class Available(val dashboard: VercelDashboardUi) : VercelRestoreUi

    /** The encrypted account is intact, but its live dashboard could not be refreshed. */
    data class DashboardUnavailable(
        val account: VercelAccountUi,
        val error: Throwable,
    ) : VercelRestoreUi
}

data class VercelDashboardUi(
    val account: VercelAccountUi,
    val projects: List<VercelProjectUi>,
)

data class VercelAccountUi(
    val displayName: String,
    val email: String?,
)

data class VercelProjectUi(
    val id: String,
    val name: String,
    val framework: String?,
    val updatedAtMillis: Long?,
)

/** Safe production fallback while the data adapter is being composed by the app entry point. */
object UnconfiguredVercelUiGateway : VercelUiGateway {
    override suspend fun restore(): Result<VercelRestoreUi> =
        Result.success(VercelRestoreUi.NoSavedAccount)

    override suspend fun connect(personalToken: String): Result<VercelDashboardUi> =
        Result.failure(IllegalStateException("The native Vercel connector is not available yet."))

    override suspend fun refresh(): Result<VercelDashboardUi> =
        Result.failure(IllegalStateException("Connect a Vercel account first."))

    override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
}
