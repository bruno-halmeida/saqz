package br.com.saqz.groups.adapter.output.jdbc.plan

import br.com.saqz.groups.domain.plan.ClosedAthleteOccupancy
import br.com.saqz.groups.domain.plan.PlanLimitPolicy
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsage
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.util.UUID
import javax.sql.DataSource

/**
 * Owner-wide usage for downgrade checks. Athlete occupancy matches invite redemption:
 * open members ∪ game WAITLISTED ∪ memberships removed within 30 days
 * ([PlanLimitPolicy.occupyingAthletes]).
 */
class JdbcOwnerPlanUsageLookup(
    dataSource: DataSource,
    private val clock: Clock = Clock.systemUTC(),
) : OwnerPlanUsageLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun usageFor(ownerUserId: UUID): OwnerPlanUsage {
        val ownedGroupCount = jdbc.sql(
            """
            SELECT count(*)::int AS cnt
            FROM access_groups
            WHERE owner_user_id = :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> rs.getInt("cnt") }
            .single()

        val openMemberIds = jdbc.sql(
            """
            SELECT DISTINCT m.user_id
            FROM group_memberships m
            INNER JOIN access_groups g ON g.id = m.group_id
            WHERE g.owner_user_id = :ownerUserId
              AND m.user_id <> :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> rs.getObject("user_id", UUID::class.java) }
            .list()
            .toSet()

        val openWaitlistIds = jdbc.sql(
            """
            SELECT DISTINCT a.member_user_id
            FROM game_attendance a
            INNER JOIN access_groups g ON g.id = a.group_id
            WHERE g.owner_user_id = :ownerUserId
              AND a.status = 'WAITLISTED'
              AND a.member_user_id <> :ownerUserId
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> rs.getObject("member_user_id", UUID::class.java) }
            .list()
            .toSet()

        val closedOccupancies = jdbc.sql(
            """
            SELECT r.user_id, r.removed_at
            FROM group_membership_removals r
            INNER JOIN access_groups g ON g.id = r.group_id
            WHERE g.owner_user_id = :ownerUserId
              AND r.user_id <> :ownerUserId
              AND r.removed_at > now() - interval '30 days'
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ ->
                ClosedAthleteOccupancy(
                    athleteId = rs.getObject("user_id", UUID::class.java),
                    closedAt = rs.getTimestamp("removed_at").toInstant(),
                )
            }
            .list()

        val occupying = PlanLimitPolicy.occupyingAthletes(
            openMemberIds = openMemberIds,
            openWaitlistIds = openWaitlistIds,
            closedOccupancies = closedOccupancies,
            now = clock.instant(),
        )

        return OwnerPlanUsage(
            ownedGroupCount = ownedGroupCount,
            occupyingAthleteCount = occupying.size,
        )
    }
}
