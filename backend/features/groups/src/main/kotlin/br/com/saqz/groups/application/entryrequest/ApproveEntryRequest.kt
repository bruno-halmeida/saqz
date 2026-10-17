package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.communication.EntryApprovalNotice
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.membership.ChangeMemberRoleCommand
import br.com.saqz.groups.application.membership.MembershipRepository
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.domain.GroupAccessDecision
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupAction
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.groups.domain.plan.PlanLimitPolicy
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import java.time.Clock
import java.util.UUID

class ApproveEntryRequest(
    private val transactionRunner: TransactionRunner,
    private val groupReadRepository: GroupReadRepository,
    private val entryRequestRepository: EntryRequestRepository,
    private val membershipRepository: MembershipRepository,
    private val subscriptionLimits: SubscriptionLimits,
    private val accessPolicy: GroupAccessPolicy,
    private val clock: Clock,
    private val approvalNotice: EntryApprovalNotice = EntryApprovalNotice { _, _, _ -> },
    private val writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
) {
    fun execute(actor: UUID, groupId: UUID, userId: UUID): ApproveEntryRequestResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction ApproveEntryRequestResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction ApproveEntryRequestResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction ApproveEntryRequestResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }
        // CONV-007: plano do organizador vencido ou pausado não aprova pedido (folha de assinatura, como nas outras ações).
        writeAccess.requireWrite(groupId)

        val existingMembership = membershipRepository.find(groupId, userId)
        if (existingMembership != null) {
            entryRequestRepository.delete(groupId, userId)
            return@inTransaction ApproveEntryRequestResult.Success(existingMembership)
        }

        val request = entryRequestRepository.find(groupId, userId)
        if (request == null) {
            // The repository hides requests whose account was soft-deleted; clean that
            // orphan as well as handling an actually missing request idempotently.
            entryRequestRepository.delete(groupId, userId)
            return@inTransaction ApproveEntryRequestResult.RequestNotFound
        }

        val occupancy = entryRequestRepository.loadAthleteOccupancy(groupId)
            ?: return@inTransaction ApproveEntryRequestResult.GroupNotFound
        val currentlyOpen = occupancy.openMemberIds + occupancy.openWaitlistIds
        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = occupancy.openMemberIds,
            openWaitlistIds = occupancy.openWaitlistIds,
            closedOccupancies = occupancy.closedOccupancies,
            now = clock.instant(),
        )
        val athleteLimit = subscriptionLimits.athleteLimitFor(occupancy.ownerUserId)
        if (!PlanLimitPolicy.canEnterAsAthlete(currentlyOpen, occupying, userId, athleteLimit)) {
            return@inTransaction ApproveEntryRequestResult.AthleteLimitExceeded
        }

        val membership = membershipRepository.change(
            ChangeMemberRoleCommand(groupId, userId, PersistedMembershipRole.ATHLETE),
        )
        check(membership.role == GroupRole.ATHLETE) { "Entry approval must create an athlete membership" }
        entryRequestRepository.applyAthleteProfileToMembership(groupId, userId)
        entryRequestRepository.delete(groupId, userId)
        entryRequestRepository.clearReturnMark(groupId, userId)
        // Aviso à pessoa aprovada, na mesma transação; não repete o aviso quando o pedido já virou vínculo (retorno acima).
        approvalNotice.approved(groupId, userId, request.requestedAt)
        ApproveEntryRequestResult.Success(membership)
    }
}
