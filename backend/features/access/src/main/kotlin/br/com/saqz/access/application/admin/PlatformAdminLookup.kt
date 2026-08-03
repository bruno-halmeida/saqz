package br.com.saqz.access.application.admin

import java.util.UUID

data class PlatformAdminView(
    val userId: UUID,
    val email: String?,
    val displayName: String?,
)

/** Lista explícita de admins de plataforma (adm-web); null quando o sujeito não é admin. */
fun interface PlatformAdminLookup {
    fun findBySubject(subject: String): PlatformAdminView?
}
