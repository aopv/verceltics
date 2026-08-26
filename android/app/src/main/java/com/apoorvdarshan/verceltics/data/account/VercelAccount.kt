package com.apoorvdarshan.verceltics.data.account

/** A connected Vercel personal-token account. Token contents are never printable. */
class VercelAccount(
    val id: String,
    val displayName: String,
    val email: String?,
    val token: SecretValue,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    val providerId: String = PROVIDER_ID

    init {
        require(id.isNotBlank() && id.length <= 256) { "Invalid account id." }
        require(displayName.isNotBlank() && displayName.length <= 256) { "Invalid display name." }
        require(email == null || email.length <= 512) { "Invalid email." }
        require(createdAtMillis >= 0L) { "Invalid creation timestamp." }
        require(updatedAtMillis >= createdAtMillis) { "Invalid update timestamp." }
    }

    fun withUpdatedToken(newToken: SecretValue, nowMillis: Long): VercelAccount = VercelAccount(
        id = id,
        displayName = displayName,
        email = email,
        token = newToken,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = nowMillis.coerceAtLeast(createdAtMillis),
    )

    override fun toString(): String =
        "VercelAccount(id=$id, providerId=$providerId, displayName=$displayName, " +
            "email=$email, token=<redacted>, createdAtMillis=$createdAtMillis, " +
            "updatedAtMillis=$updatedAtMillis)"

    companion object {
        const val PROVIDER_ID: String = "vercel"
    }
}
