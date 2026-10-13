package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.TrialEndingRepository
import br.com.saqz.subscriptions.domain.OrganizerTrial
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcTrialEndingRepository(dataSource: DataSource) : TrialEndingRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun endingSoon(now: Instant, within: Duration): List<OrganizerTrial> = jdbc.sql(
        """
        SELECT owner_user_id, started_at, ends_at FROM organizer_trials
        WHERE ends_at > :now AND ends_at <= :limit ORDER BY ends_at, owner_user_id
        """.trimIndent(),
    ).param("now", Timestamp.from(now)).param("limit", Timestamp.from(now.plus(within))).query { rs, _ ->
        OrganizerTrial(
            rs.getObject("owner_user_id", UUID::class.java),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("ends_at").toInstant(),
        )
    }.list()

    override fun claim(ownerUserId: UUID, due: List<Int>): Boolean {
        val tightest = due.min()
        var claimedTightest = false
        due.forEach { milestone ->
            val inserted = jdbc.sql(
                """
                INSERT INTO organizer_trial_notices (owner_user_id, milestone) VALUES (:owner, :milestone)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).param("owner", ownerUserId).param("milestone", milestone.toShort()).update()
            if (milestone == tightest && inserted == 1) claimedTightest = true
        }
        return claimedTightest
    }

    override fun release(ownerUserId: UUID, milestone: Int) {
        jdbc.sql("DELETE FROM organizer_trial_notices WHERE owner_user_id = :owner AND milestone = :milestone")
            .param("owner", ownerUserId).param("milestone", milestone.toShort()).update()
    }
}
