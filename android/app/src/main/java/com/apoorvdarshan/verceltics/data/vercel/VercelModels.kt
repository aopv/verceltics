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
)

data class VercelProjectsPage(
    val projects: List<VercelProject>,
    val nextCursor: String?,
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
