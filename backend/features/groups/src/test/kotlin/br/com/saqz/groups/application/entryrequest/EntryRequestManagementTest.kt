package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.application.membership.ChangeMemberRoleCommand
import br.com.saqz.groups.application.membership.MembershipRepository
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EntryRequestManagementTest {
    private val groupId = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val athleteId = UUID.randomUUID()
    private val applicantId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-02T10:00:00Z")

    @Test
    fun `owner lists pending requests with identity and requested time`() {
        val fixture = fixture(role = GroupRole.OWNER)

        val result = fixture.list.execute(ownerId, groupId)

        val requests = assertIs<ListEntryRequestsResult.Success>(result).requests
        assertEquals(listOf(fixture.entries.request), requests)
    }

    @Test
    fun `athlete is forbidden and nonmember remains hidden`() {
        val athlete = fixture(role = GroupRole.ATHLETE)
        assertIs<ListEntryRequestsResult.AccessForbidden>(athlete.list.execute(athleteId, groupId))

        val nonmember = fixture(role = null)
        assertIs<ListEntryRequestsResult.GroupNotFound>(nonmember.list.execute(UUID.randomUUID(), groupId))
    }

    @Test
    fun `admin approval creates athlete membership and removes request in transaction`() {
        val fixture = fixture(role = GroupRole.ADMIN, athleteLimit = 2)
        fixture.entries.occupancy = occupancy(openMembers = setOf(UUID.randomUUID()))

        val result = fixture.approve.execute(adminId, groupId, applicantId)

        val membership = assertIs<ApproveEntryRequestResult.Success>(result).membership
        assertEquals(applicantId, membership.userId)
        assertEquals(GroupRole.ATHLETE, membership.role)
        assertEquals(listOf(applicantId), fixture.membership.changes.map(ChangeMemberRoleCommand::userId))
        assertEquals(listOf(applicantId), fixture.entries.deleted)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `approval rechecks plan limit before creating membership`() {
        val fixture = fixture(role = GroupRole.OWNER, athleteLimit = 1)
        fixture.entries.occupancy = occupancy(openMembers = setOf(UUID.randomUUID()))

        val result = fixture.approve.execute(ownerId, groupId, applicantId)

        assertIs<ApproveEntryRequestResult.AthleteLimitExceeded>(result)
        assertTrue(fixture.membership.changes.isEmpty())
        assertTrue(fixture.entries.deleted.isEmpty())
    }

    @Test
    fun `approval is idempotent for an existing member and removes orphan request`() {
        val fixture = fixture(role = GroupRole.OWNER, athleteLimit = 0)
        val existing = AccessMembership(applicantId, AccessName.from("Applicant"), GroupRole.ATHLETE)
        fixture.membership.memberships[applicantId] = existing

        val result = fixture.approve.execute(ownerId, groupId, applicantId)

        assertEquals(ApproveEntryRequestResult.Success(existing), result)
        assertEquals(listOf(applicantId), fixture.entries.deleted)
        assertTrue(fixture.membership.changes.isEmpty())
        assertTrue(fixture.entries.occupancyWasLoaded.not())
    }

    @Test
    fun `approval of missing request returns request not found without mutation`() {
        val fixture = fixture(role = GroupRole.OWNER)
        fixture.entries.requests.clear()

        val result = fixture.approve.execute(ownerId, groupId, applicantId)

        assertIs<ApproveEntryRequestResult.RequestNotFound>(result)
        assertTrue(fixture.membership.changes.isEmpty())
        assertTrue(fixture.entries.deleted.isEmpty())
    }

    @Test
    fun `approval of a soft-deleted requester removes the hidden request`() {
        val fixture = fixture(role = GroupRole.OWNER)
        fixture.entries.deletedUsers += applicantId

        val result = fixture.approve.execute(ownerId, groupId, applicantId)

        assertIs<ApproveEntryRequestResult.RequestNotFound>(result)
        assertEquals(listOf(applicantId), fixture.entries.deleted)
        assertTrue(fixture.membership.changes.isEmpty())
        assertTrue(applicantId !in fixture.entries.requests)
    }

    @Test
    fun `rejection is idempotent and allows a later request`() {
        val fixture = fixture(role = GroupRole.ADMIN)

        assertIs<RejectEntryRequestResult.Success>(fixture.reject.execute(adminId, groupId, applicantId))
        assertEquals(listOf(applicantId), fixture.entries.deleted)
        fixture.entries.requests[applicantId] = fixture.entries.request
        assertIs<RejectEntryRequestResult.Success>(fixture.reject.execute(adminId, groupId, applicantId))
        assertEquals(listOf(applicantId, applicantId), fixture.entries.deleted)
    }

    private fun fixture(role: GroupRole?, athleteLimit: Int? = null): Fixture {
        val entries = MemoryEntryRequestRepository()
        val membership = MemoryMembershipRepository()
        val transaction = RecordingTransactionRunner()
        val read = MemoryGroupReadRepository(role)
        val approve = ApproveEntryRequest(
            transaction,
            read,
            entries,
            membership,
            FixedSubscriptionLimits(athleteLimit),
            br.com.saqz.groups.domain.GroupAccessPolicy(),
            Clock.fixed(now, ZoneOffset.UTC),
        )
        val list = ListEntryRequests(read, entries, br.com.saqz.groups.domain.GroupAccessPolicy())
        val reject = RejectEntryRequest(transaction, read, entries, br.com.saqz.groups.domain.GroupAccessPolicy())
        return Fixture(list, approve, reject, transaction, entries, membership)
    }

    private fun occupancy(openMembers: Set<UUID> = emptySet()) = GroupAthleteOccupancy(
        ownerUserId = ownerId,
        openMemberIds = openMembers,
        openWaitlistIds = emptySet(),
        closedOccupancies = emptyList(),
    )

    private data class Fixture(
        val list: ListEntryRequests,
        val approve: ApproveEntryRequest,
        val reject: RejectEntryRequest,
        val transaction: RecordingTransactionRunner,
        val entries: MemoryEntryRequestRepository,
        val membership: MemoryMembershipRepository,
    )

    private class FixedSubscriptionLimits(private val athleteLimit: Int?) : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null

        override fun athleteLimitFor(ownerId: UUID): Int? = athleteLimit
    }

    private class RecordingTransactionRunner : TransactionRunner {
        var calls = 0

        override fun <T> inTransaction(block: () -> T): T {
            calls += 1
            return block()
        }
    }

    private inner class MemoryGroupReadRepository(private val role: GroupRole?) : GroupReadRepository {
        override fun find(key: GroupReadKey): GroupReadSnapshot? = if (key.groupId == groupId) {
            GroupReadSnapshot(
                id = groupId,
                name = AccessName.from("Group"),
                timeZone = IanaTimeZone.from("UTC"),
                role = role,
                version = 1,
            )
        } else {
            null
        }

    }

    private inner class MemoryEntryRequestRepository : EntryRequestRepository {
        val request = GroupEntryRequest(
            userId = applicantId,
            displayName = AccessName.from("Applicant"),
            requestedAt = now,
        )
        val requests = mutableMapOf(applicantId to request)
        val deletedUsers = mutableSetOf<UUID>()
        val deleted = mutableListOf<UUID>()
        var occupancy: GroupAthleteOccupancy? = GroupAthleteOccupancy(
            ownerUserId = ownerId,
            openMemberIds = emptySet(),
            openWaitlistIds = emptySet(),
            closedOccupancies = emptyList(),
        )
        var occupancyWasLoaded = false

        override fun list(groupId: UUID): List<GroupEntryRequest> = requests.values.toList()

        override fun find(groupId: UUID, userId: UUID): GroupEntryRequest? =
            requests[userId].takeUnless { userId in deletedUsers }

        override fun delete(groupId: UUID, userId: UUID) {
            if (requests.remove(userId) != null) deleted += userId
        }

        override fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy? {
            occupancyWasLoaded = true
            return occupancy
        }
    }

    private inner class MemoryMembershipRepository : MembershipRepository {
        val memberships = mutableMapOf<UUID, AccessMembership>()
        val changes = mutableListOf<ChangeMemberRoleCommand>()

        override fun list(groupId: UUID): List<AccessMembership> = memberships.values.toList()

        override fun find(groupId: UUID, userId: UUID): AccessMembership? = memberships[userId]

        override fun change(command: ChangeMemberRoleCommand): AccessMembership {
            changes += command
            return AccessMembership(
                userId = command.userId,
                displayName = AccessName.from("Applicant"),
                role = GroupRole.ATHLETE,
            ).also { memberships[command.userId] = it }
        }
    }
}
