package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.communication.EntryRequestNotice
import br.com.saqz.groups.application.communication.FirstMemberNotice
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteKind
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.plan.PlanLimitPolicy
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RedeemInvite(
    private val transactionRunner: TransactionRunner,
    private val repository: InviteRedemptionRepository,
    private val subscriptionLimits: SubscriptionLimits,
    private val clock: Clock,
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
    private val firstMember: FirstMemberNotice = FirstMemberNotice { _, _ -> },
    private val entryRequest: EntryRequestNotice = EntryRequestNotice { _, _ -> },
    private val monthlyJoin: MonthlyJoinNotice = MonthlyJoinNotice { _, _, _ -> },
) {
    fun execute(actor: UUID, rawCode: String): RedeemInviteResult = transactionRunner.inTransaction {
        val now = clock.instant()
        val storedWindow = repository.lockAttemptWindow(actor, now)
        val currentWindow = storedWindow
            .takeUnless { now >= it.windowStartedAt.plus(WINDOW) }
            ?: InviteAttemptWindow(now, 0)
        if (currentWindow.invalidCount == MAX_INVALID_ATTEMPTS) {
            return@inTransaction RedeemInviteResult.AttemptLimit(retryAfterSeconds(now, currentWindow))
        }

        val code = runCatching { InviteCode.from(rawCode) }.getOrNull()
        val invite = code?.let { repository.findInvite(InviteTokenDigest.sha256(it)) }
        if (invite == null || invite.expiresAt?.let(now::isAfter) == true) {
            repository.recordInvalidAttempt(
                RecordInvalidInviteAttempt(
                    actor,
                    currentWindow.windowStartedAt,
                    currentWindow.invalidCount + 1,
                ),
            )
            return@inTransaction RedeemInviteResult.InvalidOrExpired
        }
        if (invite.groupDeleted) return@inTransaction RedeemInviteResult.GroupDeleted
        if (invite.kind == InviteKind.MONTHLY) return@inTransaction redeemMonthly(actor, invite.groupId)

        val existingRole = repository.findMembershipRole(invite.groupId, actor)
        if (existingRole != null) {
            return@inTransaction RedeemInviteResult.Success(invite.groupId, existingRole)
        }

        // Pending requests already occupy a slot; the quota check alone would allow them through.
        writeAccess.requireWrite(invite.groupId)

        val occupancy = repository.loadAthleteOccupancy(invite.groupId)
            ?: return@inTransaction RedeemInviteResult.InvalidOrExpired
        if (actor != occupancy.ownerUserId) {
            val athleteLimit = subscriptionLimits.athleteLimitFor(occupancy.ownerUserId)
            if (!PlanLimitPolicy.canEnterAsAthlete(occupancy.memberIds, actor, athleteLimit)) {
                return@inTransaction RedeemInviteResult.AthleteLimitExceeded
            }
        }

        // SAI-007: quem saiu ou foi removido não volta direto, nem por convite que normalmente entra na hora.
        if (invite.entryRequiresApproval || repository.mustRequestReturn(invite.groupId, actor)) {
            repository.createEntryRequest(CreateEntryRequestCommand(invite.groupId, actor, now))
            // Um aviso por pedido; o pedido repetido (ainda pendente) cai no mesmo request_id e não avisa de novo.
            entryRequest.requested(invite.groupId, actor)
            return@inTransaction RedeemInviteResult.Pending(invite.groupId)
        }

        val role = repository.redeemMembership(RedeemMembershipCommand(invite.groupId, actor))
        repository.deleteMatchingInvitee(invite.groupId, actor)
        // Entrada imediata por conta própria (convite ou link de presença); pedido pendente e entrada feita
        // pelo organizador não passam por aqui. Na mesma transação, sem poder desfazê-la.
        firstMember.joined(invite.groupId, actor)
        RedeemInviteResult.Success(invite.groupId, role)
    }

    /**
     * Link de mensalistas (CONV-011): entra sem aprovação e sem o "volta só com pedido", mas com a trava de escrita
     * e o limite do plano. A mensalidade padrão é conferida agora: com ela, mensalista; sem ela, avulso. Avulso
     * que já é membro vira mensalista (o papel não muda); mensalista que reabre não muda nada.
     */
    private fun redeemMonthly(actor: UUID, groupId: UUID): RedeemInviteResult {
        val existingRole = repository.findMembershipRole(groupId, actor)
        if (existingRole != null) return redeemMonthlyAsMember(actor, groupId, existingRole)

        writeAccess.requireWrite(groupId)
        val occupancy = repository.loadAthleteOccupancy(groupId) ?: return RedeemInviteResult.InvalidOrExpired
        if (actor != occupancy.ownerUserId) {
            val athleteLimit = subscriptionLimits.athleteLimitFor(occupancy.ownerUserId)
            if (!PlanLimitPolicy.canEnterAsAthlete(occupancy.memberIds, actor, athleteLimit)) {
                return RedeemInviteResult.AthleteLimitExceeded
            }
        }

        val type = if (repository.hasMonthlyFeeDefault(groupId)) AthleteMembershipType.MENSALISTA else AthleteMembershipType.AVULSO
        repository.clearReturnMark(groupId, actor)
        val role = repository.redeemMembership(RedeemMembershipCommand(groupId, actor, type))
        repository.deleteMatchingInvitee(groupId, actor)
        // Só o aviso do link: o de "Primeiro atleta" não repete para a mesma pessoa (NOT-004).
        monthlyJoin.joined(
            groupId,
            actor,
            if (type == AthleteMembershipType.MENSALISTA) {
                MonthlyJoinOutcome.JOINED_MENSALISTA
            } else {
                MonthlyJoinOutcome.JOINED_AVULSO_NO_DEFAULT_FEE
            },
        )
        return RedeemInviteResult.Success(groupId, role, type)
    }

    private fun redeemMonthlyAsMember(actor: UUID, groupId: UUID, role: GroupRole): RedeemInviteResult {
        val current = repository.findMembershipType(groupId, actor)
        repository.deleteMatchingInvitee(groupId, actor)
        if (current != AthleteMembershipType.AVULSO || !repository.hasMonthlyFeeDefault(groupId)) {
            return RedeemInviteResult.Success(groupId, role, current)
        }
        writeAccess.requireWrite(groupId)
        repository.promoteToMensalista(groupId, actor)
        monthlyJoin.joined(groupId, actor, MonthlyJoinOutcome.BECAME_MENSALISTA)
        return RedeemInviteResult.Success(groupId, role, AthleteMembershipType.MENSALISTA)
    }

    private fun retryAfterSeconds(now: Instant, window: InviteAttemptWindow): Int {
        val remainingMillis = Duration.between(now, window.windowStartedAt.plus(WINDOW)).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    private companion object {
        const val MAX_INVALID_ATTEMPTS = 10
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
