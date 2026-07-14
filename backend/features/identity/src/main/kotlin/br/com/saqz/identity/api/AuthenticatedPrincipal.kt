package br.com.saqz.identity.api

data class AuthenticatedPrincipal(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean?,
)
