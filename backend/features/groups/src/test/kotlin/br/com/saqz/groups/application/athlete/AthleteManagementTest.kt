package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AthleteManagementTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val owner = athlete(UUID.randomUUID(), "Owner Person", GroupRole.OWNER, AthleteMembershipType.MENSALISTA)
    private val admin = athlete(UUID.randomUUID(), "Admin Person", GroupRole.ADMIN, AthleteMembershipType.AVULSO)
    private val member = athlete(UUID.randomUUID(), "Athlete Person", GroupRole.ATHLETE, AthleteMembershipType.AVULSO)

    // UpdateOwnAthleteProfile

    @Test
    fun `athlete sets own position`() {
        val self = member.copy(userId = actor)
        val fixture = fixture(GroupRole.ATHLETE, listOf(owner, admin, self))

        val result = fixture.updateOwn.execute(actor, groupId, AthletePosition.PONTA)

        assertEquals(
            UpdateOwnAthleteProfileResult.Success(self.copy(position = AthletePosition.PONTA)),
            result,
        )
        val expected: List<Pair<UUID, AthletePosition?>> = listOf(actor to AthletePosition.PONTA)
        assertEquals(expected, fixture.athletes.positionUpdates)
    }

    @Test
    fun `athlete clears own position`() {
        val self = member.copy(userId = actor, position = AthletePosition.CENTRAL)
        val fixture = fixture(GroupRole.ATHLETE, listOf(owner, admin, self))

        val result = fixture.updateOwn.execute(actor, groupId, null)

        assertNull((result as UpdateOwnAthleteProfileResult.Success).athlete.position)
    }

    @Test
    fun `owner sets own position touching only owner row`() {
        val self = owner.copy(userId = actor)
        val fixture = fixture(GroupRole.OWNER, listOf(self, admin, member))

        val result = fixture.updateOwn.execute(actor, groupId, AthletePosition.LIBERO)

        assertEquals(UpdateOwnAthleteProfileResult.Success(self.copy(position = AthletePosition.LIBERO)), result)
        val expected: List<Pair<UUID, AthletePosition?>> = listOf(actor to AthletePosition.LIBERO)
        assertEquals(expected, fixture.athletes.positionUpdates)
    }

    @Test
    fun `nonmember self update returns group not found without mutation`() {
        val fixture = fixture(role = null)

        val result = fixture.updateOwn.execute(actor, groupId, AthletePosition.OPOSTO)

        assertSame(UpdateOwnAthleteProfileResult.GroupNotFound, result)
        assertTrue(fixture.athletes.positionUpdates.isEmpty())
    }

    // UpdateAthlete

    @Test
    fun `owner updates any athlete attributes`() {
        val fixture = fixture(GroupRole.OWNER)
        val command = UpdateAthleteCommand(groupId, member.userId, AthletePosition.LEVANTADOR, AthleteMembershipType.MENSALISTA, active = false)

        val result = fixture.update.execute(actor, command)

        assertEquals(
            UpdateAthleteResult.Success(
                member.copy(position = AthletePosition.LEVANTADOR, membershipType = AthleteMembershipType.MENSALISTA, active = false),
            ),
            result,
        )
        assertEquals(listOf(command), fixture.athletes.updateCommands)
    }

    @Test
    fun `admin updates the owner row without changing role`() {
        val fixture = fixture(GroupRole.ADMIN)
        val command = UpdateAthleteCommand(groupId, owner.userId, AthletePosition.CENTRAL, AthleteMembershipType.AVULSO, active = true)

        val result = fixture.update.execute(actor, command)

        assertEquals(GroupRole.OWNER, (result as UpdateAthleteResult.Success).athlete.role)
        assertEquals(AthletePosition.CENTRAL, result.athlete.position)
    }

    @Test
    fun `athlete cannot update another athlete`() {
        val fixture = fixture(GroupRole.ATHLETE)
        val command = UpdateAthleteCommand(groupId, admin.userId, null, AthleteMembershipType.AVULSO, active = true)

        val result = fixture.update.execute(actor, command)

        assertSame(UpdateAthleteResult.AccessForbidden, result)
        assertTrue(fixture.athletes.updateCommands.isEmpty())
    }

    @Test
    fun `update of missing target returns group not found without write`() {
        val fixture = fixture(GroupRole.OWNER)
        val command = UpdateAthleteCommand(groupId, UUID.randomUUID(), null, AthleteMembershipType.AVULSO, active = true)

        val result = fixture.update.execute(actor, command)

        assertSame(UpdateAthleteResult.GroupNotFound, result)
        assertTrue(fixture.athletes.updateCommands.isEmpty())
    }

    @Test
    fun `nonmember update returns group not found`() {
        val fixture = fixture(role = null)
        val command = UpdateAthleteCommand(groupId, member.userId, null, AthleteMembershipType.AVULSO, active = true)

        val result = fixture.update.execute(actor, command)

        assertSame(UpdateAthleteResult.GroupNotFound, result)
        assertTrue(fixture.athletes.updateCommands.isEmpty())
    }

    // RemoveAthlete

    @Test
    fun `owner removes an athlete`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.remove.execute(actor, groupId, member.userId)

        assertSame(RemoveAthleteResult.Success, result)
        assertEquals(listOf(member.userId), fixture.athletes.removedUserIds)
    }

    @Test
    fun `admin removes an athlete`() {
        val fixture = fixture(GroupRole.ADMIN)

        val result = fixture.remove.execute(actor, groupId, member.userId)

        assertSame(RemoveAthleteResult.Success, result)
        assertEquals(listOf(member.userId), fixture.athletes.removedUserIds)
    }

    @Test
    fun `removing the group owner is rejected`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.remove.execute(actor, groupId, owner.userId)

        assertSame(RemoveAthleteResult.OwnerImmutable, result)
        assertTrue(fixture.athletes.removedUserIds.isEmpty())
    }

    @Test
    fun `removing an absent row is an idempotent success`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.remove.execute(actor, groupId, UUID.randomUUID())

        assertSame(RemoveAthleteResult.Success, result)
        assertTrue(fixture.athletes.removedUserIds.isEmpty())
    }

    @Test
    fun `athlete cannot remove another athlete`() {
        val fixture = fixture(GroupRole.ATHLETE)

        val result = fixture.remove.execute(actor, groupId, admin.userId)

        assertSame(RemoveAthleteResult.AccessForbidden, result)
        assertTrue(fixture.athletes.removedUserIds.isEmpty())
    }

    @Test
    fun `nonmember remove returns group not found without mutation`() {
        val fixture = fixture(role = null)

        val result = fixture.remove.execute(actor, groupId, member.userId)

        assertSame(RemoveAthleteResult.GroupNotFound, result)
        assertTrue(fixture.athletes.removedUserIds.isEmpty())
    }

    private fun athlete(id: UUID, name: String, role: GroupRole, membershipType: AthleteMembershipType) =
        AthleteMembership(id, AccessName.from(name), role, position = null, membershipType = membershipType, active = true)

    private fun fixture(
        role: GroupRole?,
        athletes: List<AthleteMembership> = listOf(owner, admin, member),
    ): Fixture {
        val read = FixedGroupReadRepository(role)
        val repository = RecordingAthleteRepository(athletes)
        val policy = GroupAccessPolicy()
        val runner = DirectTransactionRunner()
        return Fixture(
            UpdateOwnAthleteProfile(runner, read, repository),
            UpdateAthlete(runner, read, repository, policy),
            RemoveAthlete(runner, read, repository, policy),
            repository,
        )
    }

    private data class Fixture(
        val updateOwn: UpdateOwnAthleteProfile,
        val update: UpdateAthlete,
        val remove: RemoveAthlete,
        val athletes: RecordingAthleteRepository,
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

    private class RecordingAthleteRepository(initial: List<AthleteMembership>) : AthleteRepository {
        private val rows = initial.associateByTo(linkedMapOf(), AthleteMembership::userId)
        val positionUpdates = mutableListOf<Pair<UUID, AthletePosition?>>()
        val updateCommands = mutableListOf<UpdateAthleteCommand>()
        val removedUserIds = mutableListOf<UUID>()

        override fun find(groupId: UUID, userId: UUID): AthleteMembership? = rows[userId]

        override fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?): AthleteMembership {
            positionUpdates += userId to position
            val current = rows.getValue(userId)
            val changed = current.copy(position = position)
            rows[userId] = changed
            return changed
        }

        override fun update(command: UpdateAthleteCommand): AthleteMembership {
            updateCommands += command
            val current = rows.getValue(command.userId)
            val changed = current.copy(
                position = command.position,
                membershipType = command.membershipType,
                active = command.active,
            )
            rows[command.userId] = changed
            return changed
        }

        override fun remove(groupId: UUID, userId: UUID) {
            removedUserIds += userId
            rows.remove(userId)
        }
    }
}
