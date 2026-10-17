package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.communication.EntryApprovalNotice
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.membership.AccessMembership
import br.com.saqz.groups.application.membership.ChangeMemberRoleCommand
import br.com.saqz.groups.application.membership.MembershipRepository
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.PersistedMembershipRole
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `aprovar o pedido de volta limpa a marca de saida`() {
        val fixture = fixture(role = GroupRole.OWNER)
        fixture.entries.returnMarked += applicantId

        assertIs<ApproveEntryRequestResult.Success>(fixture.approve.execute(ownerId, groupId, applicantId))

        assertTrue(applicantId !in fixture.entries.returnMarked)
        assertEquals(listOf("change", "apply", "delete", "clear"), fixture.order)
    }

    @Test
    fun `recusar o pedido de volta mantem a marca de saida`() {
        val fixture = fixture(role = GroupRole.OWNER)
        fixture.entries.returnMarked += applicantId

        assertIs<RejectEntryRequestResult.Success>(fixture.reject.execute(ownerId, groupId, applicantId))

        assertTrue(applicantId in fixture.entries.returnMarked)
    }

    @Test
    fun `aprovar avisa a pessoa uma vez, depois do vinculo, com o instante do pedido`() {
        val notices = mutableListOf<Triple<UUID, UUID, Instant>>()
        val fixture = fixture(role = GroupRole.OWNER, approvalNotice = EntryApprovalNotice { g, u, at -> notices += Triple(g, u, at) })

        assertIs<ApproveEntryRequestResult.Success>(fixture.approve.execute(ownerId, groupId, applicantId))

        assertEquals(listOf(Triple(groupId, applicantId, now)), notices)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `aprovar quem ja e membro, pedido sumido, limite cheio ou sem permissao nao avisa`() {
        val notices = mutableListOf<UUID>()
        val notice = EntryApprovalNotice { _, u, _ -> notices += u }

        val member = fixture(role = GroupRole.OWNER, approvalNotice = notice)
        member.membership.memberships[applicantId] = AccessMembership(applicantId, AccessName.from("Applicant"), GroupRole.ATHLETE)
        member.approve.execute(ownerId, groupId, applicantId)

        val missing = fixture(role = GroupRole.OWNER, approvalNotice = notice)
        missing.entries.requests.clear()
        missing.approve.execute(ownerId, groupId, applicantId)

        val full = fixture(role = GroupRole.OWNER, athleteLimit = 1, approvalNotice = notice)
        full.entries.occupancy = occupancy(openMembers = setOf(UUID.randomUUID()))
        full.approve.execute(ownerId, groupId, applicantId)

        fixture(role = GroupRole.ATHLETE, approvalNotice = notice).approve.execute(athleteId, groupId, applicantId)

        assertTrue(notices.isEmpty())
    }

    @Test
    fun `plano vencido ou pausado bloqueia a aprovacao sem mexer no pedido, no vinculo nem avisar`() {
        val notices = mutableListOf<UUID>()
        val fixture = fixture(
            role = GroupRole.OWNER,
            approvalNotice = EntryApprovalNotice { _, u, _ -> notices += u },
            writeAccess = GroupWriteAccess { false },
        )
        fixture.entries.returnMarked += applicantId

        assertFailsWith<SubscriptionRequiredException> { fixture.approve.execute(ownerId, groupId, applicantId) }

        assertTrue(fixture.membership.changes.isEmpty())
        assertTrue(fixture.entries.deleted.isEmpty())
        assertTrue(applicantId in fixture.entries.requests)
        assertTrue(applicantId in fixture.entries.returnMarked)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun `plano vencido nao esconde a falta de permissao de quem nao pode aprovar`() {
        val fixture = fixture(role = GroupRole.ATHLETE, writeAccess = GroupWriteAccess { false })

        assertIs<ApproveEntryRequestResult.AccessForbidden>(fixture.approve.execute(athleteId, groupId, applicantId))
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

    @Test
    fun `applicant saves the athlete registration on the pending request`() {
        val fixture = fixture(role = null)
        val command = profile(nickname = "Bia", level = AthleteLevel.AVANCADO)

        val result = fixture.saveProfile.execute(applicantId, groupId, command)

        assertIs<SaveEntryRequestAthleteProfileResult.Success>(result)
        assertEquals(listOf(command), fixture.entries.savedProfiles)
        assertEquals(1, fixture.transaction.calls)
    }

    @Test
    fun `saving the registration validates like the member endpoint`() {
        val fixture = fixture(role = null)

        val result = fixture.saveProfile.execute(applicantId, groupId, profile(nickname = "a"))

        val invalid = assertIs<SaveEntryRequestAthleteProfileResult.Invalid>(result)
        assertEquals(listOf("nickname"), invalid.fieldErrors.keys.toList())
        assertTrue(fixture.entries.savedProfiles.isEmpty())
    }

    @Test
    fun `saving the registration without a pending request is not found`() {
        val fixture = fixture(role = null)
        fixture.entries.requests.clear()

        val result = fixture.saveProfile.execute(applicantId, groupId, profile(nickname = "Bia"))

        assertIs<SaveEntryRequestAthleteProfileResult.RequestNotFound>(result)
        assertTrue(fixture.entries.savedProfiles.isEmpty())
    }

    @Test
    fun `saving the registration for a missing group is not found`() {
        val fixture = fixture(role = null)

        val result = fixture.saveProfile.execute(applicantId, UUID.randomUUID(), profile(nickname = "Bia"))

        assertIs<SaveEntryRequestAthleteProfileResult.GroupNotFound>(result)
    }

    @Test
    fun `approval copies the stored registration to the membership before dropping the request`() {
        val fixture = fixture(role = GroupRole.OWNER)

        fixture.approve.execute(ownerId, groupId, applicantId)

        assertEquals(listOf(applicantId), fixture.entries.applied)
        assertEquals(listOf("change", "apply", "delete", "clear"), fixture.order)
    }

    @Test
    fun `approval without a stored registration succeeds as before`() {
        val fixture = fixture(role = GroupRole.OWNER)

        val result = fixture.approve.execute(ownerId, groupId, applicantId)

        assertIs<ApproveEntryRequestResult.Success>(result)
        assertTrue(fixture.entries.savedProfiles.isEmpty())
    }

    @Test
    fun `applicant reads its own pending request with the group context and the stored registration`() {
        val fixture = fixture(role = null)
        fixture.saveProfile.execute(applicantId, groupId, profile(nickname = "Bia", level = AthleteLevel.AVANCADO))

        val result = fixture.own.execute(applicantId, groupId)

        val request = assertIs<GetOwnEntryRequestResult.Success>(result).request
        assertEquals(groupId, request.groupId)
        assertEquals("Group", request.groupName)
        assertEquals(now, request.requestedAt)
        assertEquals("Bia", request.athleteProfile?.nickname)
        assertEquals(AthleteLevel.AVANCADO, request.athleteProfile?.level)
    }

    @Test
    fun `reading the own request has no registration until one is saved and is not found without a request`() {
        val fixture = fixture(role = null)

        assertEquals(null, assertIs<GetOwnEntryRequestResult.Success>(fixture.own.execute(applicantId, groupId)).request.athleteProfile)

        fixture.entries.requests.clear()
        assertIs<GetOwnEntryRequestResult.RequestNotFound>(fixture.own.execute(applicantId, groupId))
        assertIs<GetOwnEntryRequestResult.GroupNotFound>(fixture.own.execute(applicantId, UUID.randomUUID()))
    }

    private fun profile(nickname: String?, level: AthleteLevel? = null) = SaveEntryRequestAthleteProfileCommand(
        groupId = groupId,
        userId = applicantId,
        nickname = nickname,
        position = null,
        secondaryPosition = null,
        level = level,
        preferredSide = null,
        heightCm = null,
    )

    private fun fixture(
        role: GroupRole?,
        athleteLimit: Int? = null,
        approvalNotice: EntryApprovalNotice = EntryApprovalNotice { _, _, _ -> },
        writeAccess: GroupWriteAccess = GroupWriteAccess.Unrestricted,
    ): Fixture {
        val order = mutableListOf<String>()
        val entries = MemoryEntryRequestRepository(order)
        val membership = MemoryMembershipRepository(order)
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
            approvalNotice,
            writeAccess,
        )
        val list = ListEntryRequests(read, entries, br.com.saqz.groups.domain.GroupAccessPolicy())
        val reject = RejectEntryRequest(transaction, read, entries, br.com.saqz.groups.domain.GroupAccessPolicy())
        val saveProfile = SaveEntryRequestAthleteProfile(transaction, read, entries)
        return Fixture(list, approve, reject, saveProfile, GetOwnEntryRequest(read, entries), transaction, entries, membership, order)
    }

    private fun occupancy(openMembers: Set<UUID> = emptySet()) = GroupAthleteOccupancy(
        ownerUserId = ownerId,
        memberIds = openMembers,
    )

    private data class Fixture(
        val list: ListEntryRequests,
        val approve: ApproveEntryRequest,
        val reject: RejectEntryRequest,
        val saveProfile: SaveEntryRequestAthleteProfile,
        val own: GetOwnEntryRequest,
        val transaction: RecordingTransactionRunner,
        val entries: MemoryEntryRequestRepository,
        val membership: MemoryMembershipRepository,
        val order: List<String>,
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

    private inner class MemoryEntryRequestRepository(private val order: MutableList<String>) : EntryRequestRepository {
        val request = GroupEntryRequest(
            userId = applicantId,
            displayName = AccessName.from("Applicant"),
            requestedAt = now,
        )
        val requests = mutableMapOf(applicantId to request)
        val deletedUsers = mutableSetOf<UUID>()
        val deleted = mutableListOf<UUID>()
        val savedProfiles = mutableListOf<SaveEntryRequestAthleteProfileCommand>()
        val applied = mutableListOf<UUID>()
        val returnMarked = mutableSetOf<UUID>()
        var occupancy: GroupAthleteOccupancy? = GroupAthleteOccupancy(
            ownerUserId = ownerId,
            memberIds = emptySet(),
        )
        var occupancyWasLoaded = false

        override fun list(groupId: UUID): List<GroupEntryRequest> = requests.values.toList()

        override fun find(groupId: UUID, userId: UUID): GroupEntryRequest? =
            requests[userId].takeUnless { userId in deletedUsers }

        override fun findAthleteProfile(groupId: UUID, userId: UUID): EntryRequestAthleteProfile? =
            savedProfiles.lastOrNull { it.userId == userId }?.let {
                EntryRequestAthleteProfile(it.nickname, it.position, it.secondaryPosition, it.level, it.preferredSide, it.heightCm)
            }

        override fun saveAthleteProfile(command: SaveEntryRequestAthleteProfileCommand): Boolean {
            if (command.userId !in requests) return false
            savedProfiles += command
            return true
        }

        override fun applyAthleteProfileToMembership(groupId: UUID, userId: UUID) {
            order += "apply"
            applied += userId
        }

        override fun delete(groupId: UUID, userId: UUID) {
            if (requests.remove(userId) != null) {
                order += "delete"
                deleted += userId
            }
        }

        override fun clearReturnMark(groupId: UUID, userId: UUID) {
            order += "clear"
            returnMarked -= userId
        }

        override fun loadAthleteOccupancy(groupId: UUID): GroupAthleteOccupancy? {
            occupancyWasLoaded = true
            return occupancy
        }
    }

    private inner class MemoryMembershipRepository(private val order: MutableList<String>) : MembershipRepository {
        val memberships = mutableMapOf<UUID, AccessMembership>()
        val changes = mutableListOf<ChangeMemberRoleCommand>()

        override fun list(groupId: UUID): List<AccessMembership> = memberships.values.toList()

        override fun find(groupId: UUID, userId: UUID): AccessMembership? = memberships[userId]

        override fun change(command: ChangeMemberRoleCommand): AccessMembership {
            changes += command
            order += "change"
            return AccessMembership(
                userId = command.userId,
                displayName = AccessName.from("Applicant"),
                role = GroupRole.ATHLETE,
            ).also { memberships[command.userId] = it }
        }
    }
}
