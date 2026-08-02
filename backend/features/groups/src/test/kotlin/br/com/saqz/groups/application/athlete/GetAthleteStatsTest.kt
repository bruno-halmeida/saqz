package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

class GetAthleteStatsTest {
    private val actor = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val member = AthleteMembership(
        target,
        AccessName.from("Target Person"),
        GroupRole.ATHLETE,
        position = null,
        membershipType = AthleteMembershipType.AVULSO,
        active = true,
    )

    @Test
    fun `calculates attendance rate from confirmed and declined rows`() {
        val result = service(GroupRole.OWNER, AthleteStatsAggregate(games = 4, eligibleGames = 3, absences = 1))
            .execute(actor, group, target)

        assertEquals(GetAthleteStatsResult.Success(AthleteStats(4, 66, 1)), result)
    }

    @Test
    fun `returns null attendance rate when only waitlisted attendance exists`() {
        val result = service(GroupRole.OWNER, AthleteStatsAggregate(games = 1, eligibleGames = 0, absences = 0))
            .execute(actor, group, target)

        assertEquals(GetAthleteStatsResult.Success(AthleteStats(1, null, 0)), result)
    }

    @Test
    fun `athlete can read only own stats`() {
        val service = service(GroupRole.ATHLETE, AthleteStatsAggregate(1, 1, 0))

        assertEquals(GetAthleteStatsResult.Success(AthleteStats(1, 100, 0)), service.execute(actor, group, actor))
    }

    @Test
    fun `athlete cannot read another member stats`() {
        val result = service(GroupRole.ATHLETE, AthleteStatsAggregate(1, 1, 0)).execute(actor, group, target)

        assertSame(GetAthleteStatsResult.AccessForbidden, result)
    }

    @Test
    fun `nonmember group access is hidden as not found`() {
        val result = service(null, AthleteStatsAggregate(1, 1, 0)).execute(actor, group, target)

        assertSame(GetAthleteStatsResult.GroupNotFound, result)
    }

    private fun service(role: GroupRole?, aggregate: AthleteStatsAggregate): GetAthleteStats {
        val targetOrActor = if (role == GroupRole.ATHLETE) actor else target
        return GetAthleteStats(
            groupReadRepository = object : GroupReadRepository {
                override fun find(key: GroupReadKey) = GroupReadSnapshot(
                    key.groupId,
                    AccessName.from("Training Group"),
                    IanaTimeZone.from("UTC"),
                    role,
                    1,
                )
            },
            athleteRepository = object : AthleteRepository {
                override fun find(groupId: UUID, userId: UUID) = member.copy(userId = targetOrActor)
                override fun updateOwn(command: UpdateOwnAthleteProfileCommand) = member
                override fun updatePosition(groupId: UUID, userId: UUID, position: br.com.saqz.groups.domain.AthletePosition?) = member
                override fun update(command: UpdateAthleteCommand) = member
                override fun remove(groupId: UUID, userId: UUID) = Unit
            },
            statsRepository = AthleteStatsRepository { _, _ -> aggregate },
        )
    }
}
