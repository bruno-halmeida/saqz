package br.com.saqz.groups.application.entryrequest

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
) {
    fun execute(actor: UUID, groupId: UUID, userId: UUID): ApproveEntryRequestResult = transactionRunner.inTransaction {
        val group = groupReadRepository.find(GroupReadKey(actor, groupId))
            ?: return@inTransaction ApproveEntryRequestResult.GroupNotFound
        when (accessPolicy.authorize(group.role, GroupAction.MANAGE_ATHLETES)) {
            GroupAccessDecision.GroupNotFound -> return@inTransaction ApproveEntryRequestResult.GroupNotFound
            GroupAccessDecision.Forbidden -> return@inTransaction ApproveEntryRequestResult.AccessForbidden
            GroupAccessDecision.Allowed -> Unit
        }

        val existingMembership = membershipRepository.find(groupId, userId)
        if (existingMembership != null) {
            entryRequestRepository.delete(groupId, userId)
            return@inTransaction ApproveEntryRequestResult.Success(existingMembership)
        }

        if (entryRequestRepository.find(groupId, userId) == null) {
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
        entryRequestRepository.delete(groupId, userId)
        ApproveEntryRequestResult.Success(membership)
    }
}
