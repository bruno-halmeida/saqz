package br.com.saqz.groups.adapter.output.jdbc.plan

import br.com.saqz.sharedkernel.subscription.TrialUsage
import br.com.saqz.sharedkernel.subscription.TrialUsageLookup
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

/**
 * Uso do teste do Organizador nos grupos de que ele é dono: jogos publicados ou encerrados no período,
 * atletas distintos que confirmaram presença nesses jogos e entradas no caixa (cobranças pagas e lançamentos
 * de entrada), com o mesmo critério do caixa do app.
 */
class JdbcTrialUsage(dataSource: DataSource) : TrialUsageLookup {
    private val jdbc = JdbcClient.create(dataSource)

    override fun usageOf(ownerId: UUID, since: Instant, until: Instant): TrialUsage = jdbc.sql(
        """
        WITH owned AS (SELECT id FROM access_groups WHERE owner_user_id = :owner AND deleted_at IS NULL),
        trial_games AS (
            SELECT g.id FROM games g JOIN owned ON owned.id = g.group_id
            WHERE g.status IN ('PUBLISHED', 'COMPLETED') AND g.published_at >= :since AND g.published_at <= :until
        )
        SELECT
            (SELECT count(*) FROM trial_games) AS games,
            (SELECT count(DISTINCT a.member_user_id) FROM game_attendance a JOIN trial_games tg ON tg.id = a.game_id
              WHERE a.status = 'CONFIRMED') AS athletes,
            COALESCE((SELECT sum(c.amount_cents) FROM group_charges c JOIN owned ON owned.id = c.group_id
              WHERE c.status = 'PAID' AND EXISTS (
                  SELECT 1 FROM group_charge_events e WHERE e.charge_id = c.id AND e.new_status = 'PAID'
                    AND e.occurred_at >= :since AND e.occurred_at <= :until)), 0)
            + COALESCE((SELECT sum(x.amount_cents) FROM group_expenses x JOIN owned ON owned.id = x.group_id
              WHERE x.status = 'ACTIVE' AND x.direction = 'IN' AND x.created_at >= :since AND x.created_at <= :until), 0) AS cash
        """.trimIndent(),
    ).param("owner", ownerId).param("since", Timestamp.from(since)).param("until", Timestamp.from(until))
        .query { rs, _ -> TrialUsage(rs.getInt("games"), rs.getInt("athletes"), rs.getLong("cash")) }.single()
}
