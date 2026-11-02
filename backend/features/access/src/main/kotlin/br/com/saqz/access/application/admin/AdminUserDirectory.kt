package br.com.saqz.access.application.admin

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** O grupo pelo qual o dono é lembrado na fila de pós-teste: o mais antigo ativo; sem ativo, o mais antigo apagado. */
data class AdminUserOwnedGroup(
    val groupId: UUID,
    val name: String,
    val deleted: Boolean,
    val frozen: Boolean,
    val members: Long,
    val gamesPlayed: Long,
)

data class AdminUserContactSummary(val result: String, val at: Instant)

data class AdminUserSummary(
    val userId: UUID,
    val displayName: String?,
    val email: String?,
    val city: String?,
    val plan: String?,
    val planSource: String? = null,
    val planEndsAt: Instant? = null,
    val suspended: Boolean,
    val memberships: Long,
    val ownedGroups: Long,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val phone: String? = null,
    val phoneVerified: Boolean = false,
    val trialEndsAt: Instant? = null,
    val ownedGroup: AdminUserOwnedGroup? = null,
    val lastContact: AdminUserContactSummary? = null,
    val doNotContact: Boolean = false,
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
    val gamesPlayed: Long = 0,
    val frozen: Boolean = false,
    val athlete: AdminAthleteProfile? = null,
)

/** Perfil de atleta no grupo (MEM-004): cada grupo tem o seu. */
data class AdminAthleteProfile(
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
)

data class AdminUserSubscription(
    val plan: String,
    val cycle: String,
    val status: String,
    val since: Instant,
)

/** Cortesia de plano ativa (DEB-097); preenchida pelo controller, que fala com o módulo de assinaturas. */
data class AdminUserCourtesy(
    val plan: String,
    val endsAt: Instant?,
    val reason: String,
    val createdAt: Instant,
)

/** Última versão do app vista por plataforma (V128). */
data class AdminUserApp(
    val platform: String,
    val appVersion: String?,
    val appBuild: String?,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
)

data class AdminUserTrial(
    val startedAt: Instant,
    val endsAt: Instant,
    /** Avisos de fim de teste já enviados, de 0 a 3 (V117). */
    val noticesSent: Long,
)

/** Primeiro cupom usado (ASS-008); ausente = "Sem cupom". */
data class AdminUserOrigin(
    val couponType: String,
    val couponCode: String?,
    val campaign: String?,
    val firstUsedAt: Instant,
)

data class AdminUserContact(
    val id: UUID,
    val result: String,
    val note: String?,
    val authorName: String?,
    val createdAt: Instant,
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
    val courtesy: AdminUserCourtesy? = null,
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    val phoneVisibility: String? = null,
    val hasPhoto: Boolean = false,
    val platformAdmin: Boolean = false,
    val onboardingCompletedAt: Instant? = null,
    val plan: String? = null,
    val planSource: String? = null,
    val planEndsAt: Instant? = null,
    val trial: AdminUserTrial? = null,
    val origin: AdminUserOrigin? = null,
    val apps: List<AdminUserApp> = emptyList(),
    val contacts: List<AdminUserContact> = emptyList(),
    val doNotContact: Boolean = false,
)

/**
 * Diretório administrativo de usuários (adm-web). Busca por nome, e-mail ou telefone, filtro por
 * plano (nome do plano real ou "FREE") e status ("active" | "suspended"); página 1-based.
 * [trial] separa a fila de pós-teste: "active" é quem está em teste, sem outro plano; "ended" é quem terminou
 * o teste, não tem plano e nunca pagou. Quem assinou e cancelou depois não é "não conversão" e fica de fora.
 * Ordenação opcional por uma das [SORT_KEYS] ("asc" | "desc"), antes da paginação; sem ela, mais novos primeiro,
 * ou o fim do teste mais perto de hoje na fila.
 */
interface AdminUserDirectory {
    fun list(
        query: String?,
        plan: String?,
        status: String?,
        page: Int,
        size: Int,
        sort: String? = null,
        descending: Boolean = false,
        trial: String? = null,
    ): AdminUserPage

    fun find(userId: UUID): AdminUserDetail?

    /** Registra um contato da equipe; null quando a conta não existe. */
    fun addContact(userId: UUID, authorId: UUID, result: String, note: String?): AdminUserContact?

    /** true quando o usuário existe (idempotente). */
    fun suspend(userId: UUID): Boolean

    fun reactivate(userId: UUID): Boolean

    companion object {
        val SORT_KEYS = setOf("name", "city", "groups", "plan", "status", "lastSeen", "createdAt")
        val CONTACT_RESULTS = setOf("NO_ANSWER", "WILL_SUBSCRIBE", "TOO_EXPENSIVE", "DID_NOT_USE", "OTHER", "DO_NOT_CONTACT")
        const val CONTACT_NOTE_MAX = 1000
        const val DO_NOT_CONTACT = "DO_NOT_CONTACT"
    }
}
