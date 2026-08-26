package com.apoorvdarshan.verceltics.domain

enum class CredentialField {
    TOKEN,
    EMAIL,
    USERNAME,
    API_KEY,
    API_SECRET,
    CLIENT_IP,
    ORGANIZATION,
    PROJECT_ID,
    ACCESS_KEY_ID,
    SECRET_ACCESS_KEY,
    REGION,
    SESSION_TOKEN,
    SITE_URL,
    PROJECT_NAME,
    SITE_ID,
    BASE_URL,
}

data class AuthenticationModeMetadata(
    val id: String,
    val displayName: String,
    val requiredFields: Set<CredentialField>,
    val optionalFields: Set<CredentialField> = emptySet(),
    val notes: String? = null,
)

data class IntegrationProvider(
    val id: String,
    val workspace: Workspace,
    val displayName: String,
    val description: String,
    /** A packed, non-premultiplied ARGB color such as `0xFFF26B14`. */
    val accentColor: Long,
    val authenticationModes: List<AuthenticationModeMetadata>,
    val searchAliases: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank()) { "Provider id cannot be blank." }
        require(displayName.isNotBlank()) { "Provider display name cannot be blank." }
        require(description.isNotBlank()) { "Provider description cannot be blank." }
        require(authenticationModes.isNotEmpty()) { "$displayName needs at least one authentication mode." }
    }
}
