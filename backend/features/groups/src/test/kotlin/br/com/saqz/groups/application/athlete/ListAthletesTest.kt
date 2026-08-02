package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ListAthletesTest {
    private val actor = UUID.randomUUID()
    private val groupId = UUID.randomUUID()
    private val entry = AthleteRosterEntry(
        userId = UUID.randomUUID(),
        displayName = AccessName.from("Member Person"),
        phone = "+5511987654321",
        position = null,
        membershipType = AthleteMembershipType.AVULSO,
        active = true,
        financialStatus = FinancialStatus.PENDENTE,
    )

    @Test
    fun `athlete can list the group roster`() {
        val filter = AthleteRosterFilter(search = "member")
        val roster = RecordingRosterRepository(listOf(entry))

        val result = useCase(GroupRole.ATHLETE, roster).execute(actor, groupId, filter)

        assertEquals(ListAthletesSuccess(listOf(entry), GroupRole.ATHLETE), result)
        assertEquals(filter, roster.lastFilter)
    }

    @Test
    fun `nonmember is forbidden when the group exists`() {
        val roster = RecordingRosterRepository(listOf(entry))

        val result = useCase(null, roster).execute(actor, groupId, AthleteRosterFilter())

        assertSame(ListAthletesResult.AccessForbidden, result)
        assertEquals(0, roster.calls)
    }

    @Test
    fun `athlete financial filter is forbidden before reading the roster`() {
        val roster = RecordingRosterRepository(listOf(entry))

        val result = useCase(GroupRole.ATHLETE, roster).execute(
            actor,
            groupId,
            AthleteRosterFilter(financialStatus = FinancialStatus.PENDENTE),
        )

        assertSame(ListAthletesResult.AccessForbidden, result)
        assertEquals(0, roster.calls)
    }

    @Test
    fun `owner and admin can use the financial filter`() {
        listOf(GroupRole.OWNER, GroupRole.ADMIN).forEach { role ->
            val filter = AthleteRosterFilter(financialStatus = FinancialStatus.PENDENTE)
            val roster = RecordingRosterRepository(listOf(entry))

            val result = useCase(role, roster).execute(actor, groupId, filter)

            assertEquals(ListAthletesSuccess(listOf(entry), role), result)
            assertEquals(filter, roster.lastFilter)
        }
    }

    private fun useCase(role: GroupRole?, roster: RecordingRosterRepository) = ListAthletes(
        groupReadRepository = FixedGroupReadRepository(role),
        rosterRepository = roster,
        accessPolicy = GroupAccessPolicy(),
    )

    private class FixedGroupReadRepository(private val role: GroupRole?) : GroupReadRepository {
        override fun find(key: GroupReadKey) = GroupReadSnapshot(
            id = key.groupId,
            name = AccessName.from("Training Group"),
            timeZone = IanaTimeZone.from("UTC"),
            role = role,
            version = 1,
        )
    }

    private class RecordingRosterRepository(
        private val entries: List<AthleteRosterEntry>,
    ) : AthleteRosterRepository {
        var calls = 0
        var lastFilter: AthleteRosterFilter? = null

        override fun list(actorId: UUID, groupId: UUID, filter: AthleteRosterFilter): List<AthleteRosterEntry> {
            calls++
            lastFilter = filter
            return entries
        }

        override fun findOwnProfile(actor: UUID): OwnAthleteProfile? = null
    }
}
