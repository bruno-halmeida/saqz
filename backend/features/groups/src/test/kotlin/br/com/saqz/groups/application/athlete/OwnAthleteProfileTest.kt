package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

/** "Meu perfil": o app recebe a pessoa e cada grupo dela como estão no cadastro; quem não existe não tem perfil. */
class OwnAthleteProfileTest {
    private val user = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val membership = OwnAthleteMembership(
        groupId = group, groupName = AccessName.from("Vôlei de quarta"), role = GroupRole.ADMIN, position = null,
        membershipType = AthleteMembershipType.MENSALISTA, active = true, nickname = "Nanda",
        monthlyFeeCents = 7000, monthlyDueDay = 10, joinedAt = Instant.parse("2026-03-01T10:00:00Z"),
    )
    private val profile = OwnAthleteProfile(user, AccessName.from("Fernanda"), "11999990000", listOf(membership))

    private fun roster(found: OwnAthleteProfile?) = object : AthleteRosterRepository {
        val asked = mutableListOf<UUID>()
        override fun list(actor: UUID, groupId: UUID, filter: AthleteRosterFilter) = error("unused")
        override fun findOwnProfile(actor: UUID): OwnAthleteProfile? { asked += actor; return found }
    }

    @Test fun `perfil existente chega com a pessoa e os grupos dela`() {
        val repository = roster(profile)
        val result = GetOwnAthleteProfile(repository).execute(user)
        assertEquals(GetOwnAthleteProfileResult.Success(profile), result)
        assertEquals(listOf(user), repository.asked)
        val got = (result as GetOwnAthleteProfileResult.Success).profile
        assertEquals(user, got.userId)
        assertEquals("11999990000", got.phone)
        val first = got.memberships.single()
        assertEquals(group, first.groupId)
        assertEquals(GroupRole.ADMIN, first.role)
        assertEquals(AthleteMembershipType.MENSALISTA, first.membershipType)
        assertEquals("Nanda", first.nickname)
        assertEquals(7000L, first.monthlyFeeCents)
        assertEquals(10, first.monthlyDueDay)
        assertEquals(Instant.parse("2026-03-01T10:00:00Z"), first.joinedAt)
        assertEquals(true, first.active)
    }

    @Test fun `quem não tem cadastro não tem perfil`() {
        assertEquals(GetOwnAthleteProfileResult.NotFound, GetOwnAthleteProfile(roster(null)).execute(user))
    }
}
