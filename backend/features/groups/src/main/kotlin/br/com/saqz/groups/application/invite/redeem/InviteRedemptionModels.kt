package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.invite.InviteKind
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.util.UUID

data class InviteAttemptWindow(
    val windowStartedAt: Instant,
    val invalidCount: Int,
) {
    init {
        require(invalidCount in 0..10) { "Invalid invite count must be between zero and ten" }
    }
}

data class RedeemableInvite(
    val groupId: UUID,
    val expiresAt: Instant?,
    val groupDeleted: Boolean = false,
    val entryRequiresApproval: Boolean = false,
    val kind: InviteKind = InviteKind.PUBLIC,
)

data class RecordInvalidInviteAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

data class RedeemMembershipCommand(
    val groupId: UUID,
    val userId: UUID,
    val membershipType: AthleteMembershipType = AthleteMembershipType.AVULSO,
)

data class CreateEntryRequestCommand(
    val groupId: UUID,
    val userId: UUID,
    val requestedAt: Instant,
)

data class GroupAthleteOccupancy(
    val ownerUserId: UUID,
    val memberIds: Set<UUID>,
)

sealed interface RedeemInviteResult {
    /** [membershipType] só vem no link de mensalistas (campo aditivo da resposta). */
    data class Success(
        val groupId: UUID,
        val role: GroupRole,
        val membershipType: AthleteMembershipType? = null,
    ) : RedeemInviteResult

    data class Pending(val groupId: UUID) : RedeemInviteResult

    data class AttemptLimit(val retryAfterSeconds: Int) : RedeemInviteResult

    data object InvalidOrExpired : RedeemInviteResult

    data object GroupDeleted : RedeemInviteResult

    data object AthleteLimitExceeded : RedeemInviteResult
}

interface InviteRedemptionRepository {
    fun lockAttemptWindow(userId: UUID, initializedAt: Instant): InviteAttemptWindow

    fun findInvite(digest: InviteTokenDigest): RedeemableInvite?

    fun recordInvalidAttempt(command: RecordInvalidInviteAttempt)

    fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy?

    fun findMembershipRole(groupId: UUID, userId: UUID): GroupRole?

    fun createEntryRequest(command: CreateEntryRequestCommand)

    /** Saiu do grupo ou foi removido depois da marca de DEB-027: só volta com aprovação (saídas antigas não têm a marca). */
    fun mustRequestReturn(groupId: UUID, userId: UUID): Boolean

    fun redeemMembership(command: RedeemMembershipCommand): GroupRole

    /** Tipo do membro no grupo; nulo para o dono sem linha de membro ou para quem não é membro. */
    fun findMembershipType(groupId: UUID, userId: UUID): AthleteMembershipType?

    /** True se o grupo tem valor e dia de vencimento padrão da mensalidade (MEM-003). */
    fun hasMonthlyFeeDefault(groupId: UUID): Boolean

    /** Avulso vira mensalista com a mensalidade do grupo; o gatilho grava `mensalista_since`. */
    fun promoteToMensalista(groupId: UUID, userId: UUID)

    /** SAI-007: o convite do dono vale como consentimento; a marca de "volta só com pedido" sai. */
    fun clearReturnMark(groupId: UUID, userId: UUID)

    /** Apaga a linha de convidado cujo telefone casa com o da conta de quem é membro (CONV-010). */
    fun deleteMatchingInvitee(groupId: UUID, userId: UUID)
}
