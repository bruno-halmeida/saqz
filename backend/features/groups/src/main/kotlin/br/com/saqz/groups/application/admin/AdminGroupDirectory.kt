package br.com.saqz.groups.application.admin

import java.time.Instant
import java.time.LocalDate
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

/** Contato do organizador, para falar com ele sem sair da ficha do grupo. */
data class AdminGroupOwnerContact(
    val email: String?,
    val phone: String?,
    val phoneVerified: Boolean,
    val doNotContact: Boolean,
)

/** Cadastro do grupo como o organizador preencheu. Lista fechada: tokens e código de convite nunca entram aqui. */
data class AdminGroupProfile(
    val city: String?,
    val description: String?,
    val modality: String?,
    val gameSystem: String?,
    val composition: String?,
    val level: String?,
    val customLevel: String?,
    val playStyle: String?,
    val customPlayStyle: String?,
    val privacy: String,
    val entryRequiresApproval: Boolean,
    val profileStatus: String,
    val hasPhoto: Boolean,
    val defaultCapacity: Int?,
    val defaultDurationMinutes: Int?,
    val defaultConfirmationLeadMinutes: Int?,
    val schedulePaused: Boolean,
    val mensalistaPriority: Boolean,
    val promotionMode: String,
    val autoConfirmEnabled: Boolean,
    val attendanceUpdatesMode: String,
    val defaultGameFeeCents: Long?,
    val defaultNoGameFee: Boolean,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
    val monthlyLateJoinPolicy: String,
    val advancePaymentRequired: Boolean,
    val pixKey: String?,
    val pixLabel: String?,
    val openingBalanceCents: Long?,
    val currency: String,
)

data class AdminGroupVenue(val name: String, val address: String, val court: String?)

/** Horário regular: [weekday] de 1 (segunda) a 7 (domingo), no fuso do grupo. */
data class AdminGroupSlot(
    val weekday: Int,
    val startTime: String,
    val durationMinutes: Int,
    val venueName: String?,
)

data class AdminGroupWhatsApp(
    val name: String,
    val enabled: Boolean,
    val broken: Boolean,
    val chatId: String,
    val linkedAt: Instant,
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
    val frozen: Boolean = false,
    val lastGameAt: Instant? = null,
    val ownerContact: AdminGroupOwnerContact? = null,
    val profile: AdminGroupProfile? = null,
    val venue: AdminGroupVenue? = null,
    val slots: List<AdminGroupSlot> = emptyList(),
    val whatsApp: AdminGroupWhatsApp? = null,
)

/** Membro na ficha do grupo: contato e perfil de atleta daquele grupo. */
data class AdminGroupMember(
    val userId: UUID,
    val displayName: String?,
    val email: String?,
    val phone: String?,
    val phoneVerified: Boolean,
    val doNotContact: Boolean,
    val role: String,
    val membershipType: String,
    val active: Boolean,
    val nickname: String?,
    val position: String?,
    val secondaryPosition: String?,
    val level: String?,
    val preferredSide: String?,
    val heightCm: Int?,
    val monthlyFeeExempt: Boolean,
    val mensalistaSince: LocalDate?,
    val joinedAt: Instant,
    val lastSeenAt: Instant,
)

data class AdminGroupMemberPage(
    val items: List<AdminGroupMember>,
    val total: Long,
    val page: Int,
    val size: Int,
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

    /** Membros do grupo, com busca por nome ou telefone: dono, admins e depois por nome; null quando o grupo não existe. */
    fun members(groupId: UUID, query: String?, page: Int, size: Int): AdminGroupMemberPage?

    companion object {
        val SORT_KEYS = setOf("name", "owner", "ownerPlan", "members", "games", "status", "createdAt")
    }
}
