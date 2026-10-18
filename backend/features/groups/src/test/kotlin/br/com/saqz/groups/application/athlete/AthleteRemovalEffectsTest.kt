package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.attendance.AttendanceRelease
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
import kotlin.test.assertTrue

/**
 * Sair do grupo ou ser removido: a vaga nos jogos futuros é liberada antes de a pessoa sair da lista,
 * e a perda do papel de admin é avisada ao resto do sistema (SAI-003 · só o dono remove admin).
 */
class AthleteRemovalEffectsTest {
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val events = mutableListOf<String>()
    private var callerRole: GroupRole? = GroupRole.OWNER

    private val rows = mutableMapOf(
        owner to row(owner, GroupRole.OWNER),
        admin to row(admin, GroupRole.ADMIN),
        member to row(member, GroupRole.ATHLETE),
    )

    private val remove = RemoveAthlete(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T) = block() },
        object : GroupReadRepository {
            override fun find(key: GroupReadKey) =
                GroupReadSnapshot(group, AccessName.from("Grupo"), IanaTimeZone.from("UTC"), callerRole, 1)
        },
        object : AthleteRepository {
            override fun find(groupId: UUID, userId: UUID) = rows[userId]
            override fun remove(groupId: UUID, userId: UUID) { events += "removed $userId"; rows.remove(userId) }
            override fun updateOwn(command: UpdateOwnAthleteProfileCommand) = error("unused")
            override fun updatePosition(groupId: UUID, userId: UUID, position: AthletePosition?) = error("unused")
            override fun update(command: UpdateAthleteCommand) = error("unused")
        },
        GroupAccessPolicy(),
        { groupId, userId -> events += "revoked $groupId $userId" },
        AttendanceRelease { groupId, memberId, actorId -> events += "released $groupId $memberId by $actorId" },
    )

    private fun row(id: UUID, role: GroupRole) =
        AthleteMembership(id, AccessName.from("Pessoa"), role, null, AthleteMembershipType.AVULSO, true)

    @Test fun `atleta que sai libera as próprias vagas futuras antes de sair e não revoga nada`() {
        assertEquals(RemoveAthleteResult.Success, remove.leave(member, group))
        assertEquals(listOf("released $group $member by $member", "removed $member"), events)
    }

    @Test fun `admin que sai libera as vagas, sai e tem o papel revogado`() {
        assertEquals(RemoveAthleteResult.Success, remove.leave(admin, group))
        assertEquals(listOf("released $group $admin by $admin", "removed $admin", "revoked $group $admin"), events)
    }

    @Test fun `o dono não sai, nada é liberado, removido ou revogado`() {
        assertEquals(RemoveAthleteResult.OwnerImmutable, remove.leave(owner, group))
        assertTrue(events.isEmpty())
    }

    @Test fun `quem já saiu pode repetir o pedido sem efeito colateral`() {
        assertEquals(RemoveAthleteResult.Success, remove.leave(UUID.randomUUID(), group))
        assertTrue(events.isEmpty())
    }

    @Test fun `dono remove atleta, e a vaga liberada é atribuída ao atleta e à pessoa que removeu`() {
        assertEquals(RemoveAthleteResult.Success, remove.execute(owner, group, member))
        assertEquals(listOf("released $group $member by $owner", "removed $member"), events)
    }

    @Test fun `dono remove admin e o papel dele é revogado`() {
        assertEquals(RemoveAthleteResult.Success, remove.execute(owner, group, admin))
        assertEquals(listOf("released $group $admin by $owner", "removed $admin", "revoked $group $admin"), events)
    }

    @Test fun `admin remove atleta, mas não remove outro admin`() {
        callerRole = GroupRole.ADMIN
        assertEquals(RemoveAthleteResult.Success, remove.execute(admin, group, member))
        events.clear()
        val otherAdmin = UUID.randomUUID()
        rows[otherAdmin] = row(otherAdmin, GroupRole.ADMIN)
        assertEquals(RemoveAthleteResult.AccessForbidden, remove.execute(admin, group, otherAdmin))
        assertTrue(events.isEmpty())
    }

    @Test fun `ninguém remove o dono, nem o próprio dono por esse caminho`() {
        assertEquals(RemoveAthleteResult.OwnerImmutable, remove.execute(owner, group, owner))
        assertTrue(events.isEmpty())
    }

    @Test fun `remover quem não está mais no grupo é sucesso sem efeito`() {
        assertEquals(RemoveAthleteResult.Success, remove.execute(owner, group, UUID.randomUUID()))
        assertTrue(events.isEmpty())
    }

    @Test fun `atleta comum não remove ninguém e quem é de fora não encontra o grupo`() {
        callerRole = GroupRole.ATHLETE
        assertEquals(RemoveAthleteResult.AccessForbidden, remove.execute(member, group, admin))
        callerRole = null
        assertEquals(RemoveAthleteResult.GroupNotFound, remove.execute(member, group, admin))
        assertTrue(events.isEmpty())
    }
}
