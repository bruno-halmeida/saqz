package br.com.saqz.access.application.membership

import br.com.saqz.access.application.group.create.TransactionRunner
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.application.group.read.GroupReadRepository
import br.com.saqz.access.application.group.read.GroupReadSnapshot
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupAccessPolicy
import br.com.saqz.access.domain.GroupRole
import br.com.saqz.access.domain.IanaTimeZone
import br.com.saqz.access.domain.PersistedMembershipRole
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OwnerRoleAdministrationTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val owner = member(UUID.randomUUID(), "Owner Person", GroupRole.OWNER)
    private val admin = member(UUID.randomUUID(), "Admin Person", GroupRole.ADMIN)
    private val athlete = member(UUID.randomUUID(), "Athlete Person", GroupRole.ATHLETE)

    @Test
    fun `owner lists exact minimal memberships`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.list.execute(actor, groupId)

        assertEquals(ListAccessMembershipsResult.Success(listOf(owner, admin, athlete)), result)
        assertEquals(1, fixture.memberships.listCalls)
    }

    @Test
    fun `admin cannot list access memberships`() {
        assertListForbidden(GroupRole.ADMIN)
    }

    @Test
    fun `athlete cannot list access memberships`() {
        assertListForbidden(GroupRole.ATHLETE)
    }

    @Test
    fun `nonmember list returns group not found`() {
        val fixture = fixture(role = null)

        assertSame(ListAccessMembershipsResult.GroupNotFound, fixture.list.execute(actor, groupId))
        assertEquals(0, fixture.memberships.listCalls)
    }

    @Test
    fun `owner promotes athlete to admin with exact command`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.change.execute(actor, groupId, athlete.userId, PersistedMembershipRole.ADMIN)

        assertEquals(ChangeMemberRoleResult.Success(athlete.copy(role = GroupRole.ADMIN)), result)
        assertEquals(
            listOf(ChangeMemberRoleCommand(groupId, athlete.userId, PersistedMembershipRole.ADMIN)),
            fixture.memberships.commands,
        )
    }

    @Test
    fun `owner demotes admin to athlete`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.change.execute(actor, groupId, admin.userId, PersistedMembershipRole.ATHLETE)

        assertEquals(ChangeMemberRoleResult.Success(admin.copy(role = GroupRole.ATHLETE)), result)
    }

    @Test
    fun `repeating the current role is idempotent without write`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.change.execute(actor, groupId, admin.userId, PersistedMembershipRole.ADMIN)

        assertEquals(ChangeMemberRoleResult.Success(admin), result)
        assertTrue(fixture.memberships.commands.isEmpty())
    }

    @Test
    fun `owner membership is immutable`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.change.execute(actor, groupId, owner.userId, PersistedMembershipRole.ATHLETE)

        assertSame(ChangeMemberRoleResult.OwnerImmutable, result)
        assertTrue(fixture.memberships.commands.isEmpty())
    }

    @Test
    fun `missing target returns group not found without write`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.change.execute(actor, groupId, UUID.randomUUID(), PersistedMembershipRole.ADMIN)

        assertSame(ChangeMemberRoleResult.GroupNotFound, result)
        assertTrue(fixture.memberships.commands.isEmpty())
    }

    @Test
    fun `admin cannot promote an athlete`() {
        assertChangeForbidden(GroupRole.ADMIN, athlete, PersistedMembershipRole.ADMIN)
    }

    @Test
    fun `admin cannot demote another admin`() {
        assertChangeForbidden(GroupRole.ADMIN, admin, PersistedMembershipRole.ATHLETE)
    }

    @Test
    fun `athlete cannot change a role`() {
        assertChangeForbidden(GroupRole.ATHLETE, admin, PersistedMembershipRole.ATHLETE)
    }

    @Test
    fun `nonmember change returns group not found`() {
        val fixture = fixture(role = null)

        val result = fixture.change.execute(actor, groupId, athlete.userId, PersistedMembershipRole.ADMIN)

        assertSame(ChangeMemberRoleResult.GroupNotFound, result)
        assertTrue(fixture.memberships.commands.isEmpty())
    }

    @Test
    fun `changing one admin leaves other admins untouched`() {
        val secondAdmin = member(UUID.randomUUID(), "Second Admin", GroupRole.ADMIN)
        val fixture = fixture(GroupRole.OWNER, listOf(owner, admin, secondAdmin, athlete))

        fixture.change.execute(actor, groupId, admin.userId, PersistedMembershipRole.ATHLETE)

        assertEquals(GroupRole.ATHLETE, fixture.memberships.members.getValue(admin.userId).role)
        assertEquals(GroupRole.ADMIN, fixture.memberships.members.getValue(secondAdmin.userId).role)
        assertEquals(listOf(admin.userId), fixture.memberships.commands.map { it.userId })
    }

    private fun assertListForbidden(role: GroupRole) {
        val fixture = fixture(role)
        assertSame(ListAccessMembershipsResult.AccessForbidden, fixture.list.execute(actor, groupId))
        assertEquals(0, fixture.memberships.listCalls)
    }

    private fun assertChangeForbidden(
        actorRole: GroupRole,
        target: AccessMembership,
        role: PersistedMembershipRole,
    ) {
        val fixture = fixture(actorRole)
        val result = fixture.change.execute(actor, groupId, target.userId, role)
        assertSame(ChangeMemberRoleResult.AccessForbidden, result)
        assertTrue(fixture.memberships.commands.isEmpty())
    }

    private fun fixture(
        role: GroupRole?,
        members: List<AccessMembership> = listOf(owner, admin, athlete),
    ): Fixture {
        val read = FixedGroupReadRepository(role)
        val memberships = RecordingMembershipRepository(members)
        val policy = GroupAccessPolicy()
        return Fixture(
            ListAccessMemberships(read, memberships, policy),
            ChangeMemberRole(DirectTransactionRunner(), read, memberships, policy),
            memberships,
        )
    }

    private fun member(id: UUID, name: String, role: GroupRole) =
        AccessMembership(id, AccessName.from(name), role)

    private data class Fixture(
        val list: ListAccessMemberships,
        val change: ChangeMemberRole,
        val memberships: RecordingMembershipRepository,
    )

    private inner class FixedGroupReadRepository(private val role: GroupRole?) : GroupReadRepository {
        override fun find(key: GroupReadKey) = GroupReadSnapshot(
            groupId,
            AccessName.from("Training Group"),
            IanaTimeZone.from("UTC"),
            role,
            1,
        )
    }

    private class DirectTransactionRunner : TransactionRunner {
        override fun <T> inTransaction(block: () -> T): T = block()
    }

    private class RecordingMembershipRepository(initial: List<AccessMembership>) : MembershipRepository {
        val members = initial.associateByTo(linkedMapOf(), AccessMembership::userId)
        val commands = mutableListOf<ChangeMemberRoleCommand>()
        var listCalls = 0

        override fun list(groupId: UUID): List<AccessMembership> {
            listCalls += 1
            return members.values.toList()
        }

        override fun find(groupId: UUID, userId: UUID): AccessMembership? = members[userId]

        override fun change(command: ChangeMemberRoleCommand): AccessMembership {
            commands += command
            val current = members.getValue(command.userId)
            val changed = current.copy(role = GroupRole.valueOf(command.role.name))
            members[command.userId] = changed
            return changed
        }
    }
}
