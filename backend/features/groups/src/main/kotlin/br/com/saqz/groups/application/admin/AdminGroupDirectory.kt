package br.com.saqz.groups.application.admin

import java.time.Instant
import java.util.UUID

data class AdminGroupSummary(
    val groupId: UUID,
    val name: String,
    val ownerUserId: UUID,
    val ownerName: String?,
    val ownerPlan: String?,
    val ownerPlanSource: String? = null,
    val ownerPlanEndsAt: Instant? = null,
    val members: Long,
    val gamesPlayed: Long,
    val deleted: Boolean,
    val createdAt: Instant,
)

data class AdminGroupPage(
    val items: List<AdminGroupSummary>,
    val total: Long,
    val page: Int,
    val size: Int,
)

data class AdminGroupGame(
    val gameId: UUID,
    val title: String,
    val startsAt: Instant,
    val status: String,
    val confirmed: Long,
)

data class AdminGroupDetail(
    val groupId: UUID,
    val name: String,
    val timeZone: String,
    val ownerUserId: UUID,
    val ownerName: String?,
    val ownerPlan: String?,
    val ownerPlanSource: String? = null,
    val ownerPlanEndsAt: Instant? = null,
    val members: Long,
    val gamesPlayed: Long,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val lastGames: List<AdminGroupGame>,
)

/**
 * Diretório administrativo de grupos (adm-web). Busca por nome do grupo ou do dono;
 * filtro por status "active" | "deleted"; página 1-based.
 * Ordenação opcional por uma das [SORT_KEYS] ("asc" | "desc"), antes da paginação; sem ela, mais novos primeiro.
 */
interface AdminGroupDirectory {
    fun list(
        query: String?,
        status: String?,
        page: Int,
        size: Int,
        sort: String? = null,
        descending: Boolean = false,
    ): AdminGroupPage

    fun find(groupId: UUID): AdminGroupDetail?

    companion object {
        val SORT_KEYS = setOf("name", "owner", "ownerPlan", "members", "games", "status", "createdAt")
    }
}
