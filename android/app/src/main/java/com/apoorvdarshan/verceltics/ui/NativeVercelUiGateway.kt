package com.apoorvdarshan.verceltics.ui

import android.content.Context
import com.apoorvdarshan.verceltics.data.account.SecretValue
import com.apoorvdarshan.verceltics.data.account.VercelAccount
import com.apoorvdarshan.verceltics.data.account.VercelAccountRepository
import com.apoorvdarshan.verceltics.data.network.CancelableCall
import com.apoorvdarshan.verceltics.data.vercel.VercelApi
import com.apoorvdarshan.verceltics.data.vercel.VercelApiException
import com.apoorvdarshan.verceltics.data.vercel.VercelAnalyticsOverview
import com.apoorvdarshan.verceltics.data.vercel.VercelAnalyticsPoint
import com.apoorvdarshan.verceltics.data.vercel.VercelAnalyticsTimeseries
import com.apoorvdarshan.verceltics.data.vercel.VercelProject
import com.apoorvdarshan.verceltics.data.vercel.VercelTeam
import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val account = api.accountForValidatedUser(user = user, token = secret)
        val connectedDashboard = dashboard(account)
        executeAwait(executor) { accountRepository.save(account) }
        connectedDashboard
    }

    override suspend fun refresh(): Result<VercelDashboardUi> = capture {
        val account = executeAwait(executor) { accountRepository.load() }
            ?: throw IllegalStateException("Connect a Vercel account first.")
        dashboard(account)
    }

    override suspend fun loadProjectAnalytics(
        project: VercelProjectUi,
        range: VercelAnalyticsRange,
        environment: VercelAnalyticsEnvironment,
    ): Result<VercelAnalyticsLoadUi> = capture {
        val account = executeAwait(executor) { accountRepository.load() }
            ?: throw IllegalStateException("Connect a Vercel account first.")
        try {
            VercelAnalyticsLoadUi.Available(
                analytics(
                    token = account.token,
                    project = project,
                    range = range,
                    environment = environment,
                ),
            )
        } catch (error: VercelApiException) {
            if (error.statusCode == 401 || error.statusCode == 403) throw error
            VercelAnalyticsLoadUi.Unavailable(
                message = if (error.statusCode == 400 || error.statusCode == 404) {
                    "Vercel Web Analytics is not available through token access right now. " +
                        "Project details are still available."
                } else {
                    "Vercel Web Analytics returned HTTP ${error.statusCode}. " +
                        "Project details are still available."
                },
            )
        }
    }

    override suspend fun disconnect(): Result<Unit> = capture {
        executeAwait(executor) { accountRepository.delete() }
    }

    private suspend fun dashboard(account: VercelAccount): VercelDashboardUi {
        val loaded = loadAllProjects(account.token)
        return VercelDashboardUi(
            account = account.toUi(),
            projects = loaded.projects.map(VercelProject::toUi),
            warning = loaded.warning,
        )
    }

    private suspend fun loadAllProjects(token: SecretValue): ProjectLoadResult = coroutineScope {
        val personalProjects = fetchAllProjects(token = token, teamId = null)
        val teams = try {
            fetchAllTeams(token).filter(VercelTeam::isConfirmedMember)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return@coroutineScope ProjectLoadResult(
                projects = personalProjects,
                warning = "Personal projects loaded, but the Vercel team list could not be refreshed.",
            )
        }

        val teamResults = teams.map { team ->
            async {
                try {
                    TeamProjectLoad.Success(
                        projects = fetchAllProjects(token, team.id).map { project ->
                            if (project.teamId == team.id) project else project.copy(teamId = team.id)
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    TeamProjectLoad.Failure(team.displayName)
                }
            }
        }.awaitAll()

        val failedTeams = teamResults.filterIsInstance<TeamProjectLoad.Failure>()
            .map(TeamProjectLoad.Failure::teamName)
            .sorted()
        val allProjects = buildList {
            addAll(personalProjects)
            teamResults.filterIsInstance<TeamProjectLoad.Success>().forEach { addAll(it.projects) }
        }.distinctBy(VercelProject::id)
        val warning = failedTeams.takeIf(List<String>::isNotEmpty)?.let { names ->
            val visibleNames = names.take(3).joinToString(", ")
            val remainder = if (names.size > 3) " and ${names.size - 3} more" else ""
            "Some Vercel teams could not be refreshed: $visibleNames$remainder."
        }
        ProjectLoadResult(projects = allProjects, warning = warning)
    }

    private suspend fun fetchAllProjects(token: SecretValue, teamId: String?): List<VercelProject> {
        val projects = mutableListOf<VercelProject>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageCount = 0
        do {
            val page = api.newListProjectsCall(
                token = token,
                limit = PAGE_LIMIT,
                until = cursor,
                teamId = teamId,
            ).executeAwait(executor)
            projects += page.projects
            cursor = page.nextCursor
            pageCount += 1
            if (cursor != null && !seenCursors.add(cursor)) {
                throw IllegalStateException("Vercel project pagination repeated a cursor.")
            }
            if (cursor != null && pageCount >= MAXIMUM_PAGES) {
                throw IllegalStateException("Vercel project pagination exceeded $MAXIMUM_PAGES pages.")
            }
        } while (cursor != null)
        return projects.distinctBy(VercelProject::id)
    }

    private suspend fun fetchAllTeams(token: SecretValue): List<VercelTeam> {
        val teams = mutableListOf<VercelTeam>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        var pageCount = 0
        do {
            val page = api.newListTeamsCall(
                token = token,
                limit = PAGE_LIMIT,
                until = cursor,
            ).executeAwait(executor)
            teams += page.teams
            cursor = page.nextCursor
            pageCount += 1
            if (cursor != null && !seenCursors.add(cursor)) {
                throw IllegalStateException("Vercel team pagination repeated a cursor.")
            }
            if (cursor != null && pageCount >= MAXIMUM_PAGES) {
                throw IllegalStateException("Vercel team pagination exceeded $MAXIMUM_PAGES pages.")
            }
        } while (cursor != null)
        return teams.distinctBy(VercelTeam::id)
    }

    private suspend fun analytics(
        token: SecretValue,
        project: VercelProjectUi,
        range: VercelAnalyticsRange,
        environment: VercelAnalyticsEnvironment,
    ): VercelAnalyticsDataUi = coroutineScope {
        val nowMillis = System.currentTimeMillis()
        val fromMillis = nowMillis - range.durationMillis
        val previousFromMillis = fromMillis - range.durationMillis
        val from = Instant.ofEpochMilli(fromMillis).toString()
        val to = Instant.ofEpochMilli(nowMillis).toString()
        val previousFrom = Instant.ofEpochMilli(previousFromMillis).toString()
        val previousTo = from
        val environmentQuery = environment.queryValue

        val overview = async {
            api.newAnalyticsOverviewCall(
                token = token,
                projectId = project.id,
                teamId = project.teamId,
                from = from,
                to = to,
                environment = environmentQuery,
            ).executeAwait(executor)
        }
        val previousOverview = async {
            optionalAnalytics {
                api.newAnalyticsOverviewCall(
                    token = token,
                    projectId = project.id,
                    teamId = project.teamId,
                    from = previousFrom,
                    to = previousTo,
                    environment = environmentQuery,
                ).executeAwait(executor)
            }
        }
        val timeseries = async {
            analyticsTimeseries(token, project, from, to, environmentQuery, groupBy = null)
        }
        val pages = async {
            analyticsTimeseries(token, project, from, to, environmentQuery, groupBy = "path")
        }
        val referrers = async {
            analyticsTimeseries(token, project, from, to, environmentQuery, groupBy = "referrer")
        }
        val countries = async {
            analyticsTimeseries(token, project, from, to, environmentQuery, groupBy = "country")
        }
        val devices = optionalBreakdown(token, project, from, to, environmentQuery, "device_type")
        val browsers = optionalBreakdown(token, project, from, to, environmentQuery, "client_name")
        val operatingSystems = optionalBreakdown(token, project, from, to, environmentQuery, "os_name")
        val utmSources = optionalBreakdown(token, project, from, to, environmentQuery, "utm")
        val routes = optionalBreakdown(token, project, from, to, environmentQuery, "route")
        val hostnames = optionalBreakdown(token, project, from, to, environmentQuery, "hostname")
        val events = optionalBreakdown(token, project, from, to, environmentQuery, "event_name")
        val flags = optionalBreakdown(token, project, from, to, environmentQuery, "flags")
        val queryParameters = optionalBreakdown(
            token,
            project,
            from,
            to,
            environmentQuery,
            "query_params",
        )

        VercelAnalyticsDataUi(
            overview = overview.await().toUi(),
            previousOverview = previousOverview.await()?.toUi(),
            timeseries = timeseries.await().groups["all"].orEmpty().map(VercelAnalyticsPoint::toUi),
            pages = pages.await().toBreakdownUi(),
            referrers = referrers.await().toBreakdownUi(),
            countries = countries.await().toBreakdownUi(),
            devices = devices.await(),
            browsers = browsers.await(),
            operatingSystems = operatingSystems.await(),
            utmSources = utmSources.await(),
            routes = routes.await(),
            hostnames = hostnames.await(),
            events = events.await(),
            flags = flags.await(),
            queryParameters = queryParameters.await(),
        )
    }

    private fun kotlinx.coroutines.CoroutineScope.optionalBreakdown(
        token: SecretValue,
        project: VercelProjectUi,
        from: String,
        to: String,
        environment: String?,
        groupBy: String,
    ) = async {
        optionalAnalytics {
            analyticsTimeseries(token, project, from, to, environment, groupBy).toBreakdownUi()
        }.orEmpty()
    }

    private suspend fun analyticsTimeseries(
        token: SecretValue,
        project: VercelProjectUi,
        from: String,
        to: String,
        environment: String?,
        groupBy: String?,
    ): VercelAnalyticsTimeseries = api.newAnalyticsTimeseriesCall(
        token = token,
        projectId = project.id,
        teamId = project.teamId,
        from = from,
        to = to,
        environment = environment,
        groupBy = groupBy,
    ).executeAwait(executor)

    private suspend fun <T> optionalAnalytics(operation: suspend () -> T): T? = try {
        operation()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
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
        private const val PAGE_LIMIT = 100
        private const val MAXIMUM_PAGES = 200

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

    private data class ProjectLoadResult(
        val projects: List<VercelProject>,
        val warning: String?,
    )

    private sealed interface TeamProjectLoad {
        data class Success(val projects: List<VercelProject>) : TeamProjectLoad
        data class Failure(val teamName: String) : TeamProjectLoad
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
    teamId = teamId,
)

private fun VercelAnalyticsOverview.toUi(): VercelAnalyticsOverviewUi =
    VercelAnalyticsOverviewUi(
        pageViews = pageViews,
        visitors = visitors,
        bounceRate = bounceRate,
    )

private fun VercelAnalyticsPoint.toUi(): VercelAnalyticsPointUi = VercelAnalyticsPointUi(
    key = key,
    pageViews = pageViews,
    visitors = visitors,
)

private fun VercelAnalyticsTimeseries.toBreakdownUi(): List<VercelAnalyticsBreakdownUi> = groups
    .asSequence()
    .filter { (key, _) -> key != "all" }
    .map { (key, points) ->
        VercelAnalyticsBreakdownUi(
            key = key,
            pageViews = points.sumOf(VercelAnalyticsPoint::pageViews),
            visitors = points.sumOf(VercelAnalyticsPoint::visitors),
        )
    }
    .sortedByDescending(VercelAnalyticsBreakdownUi::visitors)
    .toList()
