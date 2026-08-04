package br.com.saqz.access.application.admin

import java.time.Instant
import java.util.UUID

data class AdminUserSummary(
    val userId: UUID,
    val displayName: String?,
    val email: String?,
    val city: String?,
    val plan: String?,
    val suspended: Boolean,
    val memberships: Long,
    val ownedGroups: Long,
    val createdAt: Instant,
    val lastSeenAt: Instant,
)

data class AdminUserPage(
    val items: List<AdminUserSummary>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class AdminUserGroup(
    val groupId: UUID,
    val name: String,
    val role: String,
    val members: Long,
)

data class AdminUserSubscription(
    val plan: String,
    val cycle: String,
    val status: String,
    val since: Instant,
)

data class AdminUserDetail(
    val userId: UUID,
    val displayName: String?,
    val email: String?,
    val nickname: String?,
    val phone: String?,
    val city: String?,
    val suspendedAt: Instant?,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val groups: List<AdminUserGroup>,
    val subscription: AdminUserSubscription?,
)

/**
 * Diretório administrativo de usuários (adm-web). Busca por nome/e-mail, filtro por
 * plano (nome do plano real ou "FREE") e status ("active" | "suspended"); página 1-based.
 */
interface AdminUserDirectory {
    fun list(query: String?, plan: String?, status: String?, page: Int, size: Int): AdminUserPage

    fun find(userId: UUID): AdminUserDetail?

    /** true quando o usuário existe (idempotente). */
    fun suspend(userId: UUID): Boolean

    fun reactivate(userId: UUID): Boolean
}
