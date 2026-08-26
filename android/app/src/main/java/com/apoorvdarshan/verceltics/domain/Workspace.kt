package com.apoorvdarshan.verceltics.domain

enum class Workspace(
    val id: String,
    val displayName: String,
) {
    HOSTING(id = "hosting", displayName = "Hosting"),
    REGISTRARS(id = "registrars", displayName = "Registrars"),
    SITES(id = "sites", displayName = "Sites"),
}
