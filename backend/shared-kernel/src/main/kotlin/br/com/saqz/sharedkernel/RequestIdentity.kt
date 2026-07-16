package br.com.saqz.sharedkernel

data class RequestIdentity(
    val subject: String,
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val displayName: String? = null,
)
