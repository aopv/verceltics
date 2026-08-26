package com.apoorvdarshan.verceltics.data.vercel

data class VercelUser(
    val id: String,
    val username: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?,
)

data class VercelProject(
    val id: String,
    val name: String,
    val framework: String?,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val teamId: String? = null,
)

data class VercelProjectsPage(
    val projects: List<VercelProject>,
    val nextCursor: String?,
)

data class VercelTeam(
    val id: String,
    val slug: String,
    val name: String?,
    val membershipConfirmed: Boolean?,
) {
    val displayName: String
        get() = name?.trim()?.takeIf(String::isNotEmpty) ?: slug

    val isConfirmedMember: Boolean
        get() = membershipConfirmed != false
}

data class VercelTeamsPage(
    val teams: List<VercelTeam>,
    val nextCursor: String?,
)

data class VercelAnalyticsOverview(
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: Double?,
)

data class VercelAnalyticsPoint(
    val key: String,
    val pageViews: Long,
    val visitors: Long,
    val bounceRate: Double?,
)

data class VercelAnalyticsTimeseries(
    val groups: Map<String, List<VercelAnalyticsPoint>>,
)

class VercelApiException(
    val statusCode: Int,
    val errorCode: String?,
    message: String,
) : Exception(message) {
    override fun toString(): String =
        "VercelApiException(statusCode=$statusCode, errorCode=$errorCode, message=$message)"
}

class VercelResponseFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
