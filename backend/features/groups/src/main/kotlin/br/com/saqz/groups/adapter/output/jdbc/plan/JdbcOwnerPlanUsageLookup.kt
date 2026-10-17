package br.com.saqz.groups.adapter.output.jdbc.plan

import br.com.saqz.sharedkernel.subscription.OwnerPlanUsage
import br.com.saqz.sharedkernel.subscription.OwnerPlanUsageLookup
import org.springframework.jdbc.core.simple.JdbcClient
import java.util.UUID
import javax.sql.DataSource

/**
 * Uso do plano para a troca de plano. O limite de atletas é por grupo (SAI-005): conta só
 * quem é membro agora, sem o dono, e o que interessa é o maior grupo do dono.
 */
class JdbcOwnerPlanUsageLookup(
    dataSource: DataSource,
) : OwnerPlanUsageLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun usageFor(ownerUserId: UUID): OwnerPlanUsage {
        val ownedGroupCount = jdbc.sql(
            """
            SELECT count(*)::int AS cnt
            FROM access_groups
            WHERE owner_user_id = :ownerUserId
              AND deleted_at IS NULL
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> rs.getInt("cnt") }
            .single()

        val largestGroupAthleteCount = jdbc.sql(
            """
            SELECT COALESCE(max(per_group.cnt), 0)::int AS cnt
            FROM (
                SELECT count(m.user_id) AS cnt
                FROM access_groups g
                LEFT JOIN group_memberships m
                    ON m.group_id = g.id AND m.user_id <> g.owner_user_id
                WHERE g.owner_user_id = :ownerUserId
                  AND g.deleted_at IS NULL
                GROUP BY g.id
            ) per_group
            """.trimIndent(),
        )
            .param("ownerUserId", ownerUserId)
            .query { rs, _ -> rs.getInt("cnt") }
            .single()

        return OwnerPlanUsage(
            ownedGroupCount = ownedGroupCount,
            largestGroupAthleteCount = largestGroupAthleteCount,
        )
    }
}
