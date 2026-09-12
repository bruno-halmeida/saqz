package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.OrganizerTrialRepository
import br.com.saqz.subscriptions.domain.OrganizerTrial
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.util.UUID
import javax.sql.DataSource

class JdbcOrganizerTrialRepository(dataSource: DataSource) : OrganizerTrialRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(ownerUserId: UUID): OrganizerTrial? = jdbc.sql(
        "SELECT owner_user_id, started_at, ends_at FROM organizer_trials WHERE owner_user_id = :owner",
    ).param("owner", ownerUserId).query { rs, _ ->
        OrganizerTrial(rs.getObject("owner_user_id", UUID::class.java), rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("ends_at").toInstant())
    }.optional().orElse(null)

    override fun insert(trial: OrganizerTrial) {
        jdbc.sql(
            """
            INSERT INTO organizer_trials (owner_user_id, started_at, ends_at)
            VALUES (:owner, :start, :end)
            ON CONFLICT (owner_user_id) DO NOTHING
            """.trimIndent(),
        ).param("owner", trial.ownerUserId)
            .param("start", Timestamp.from(trial.startedAt))
            .param("end", Timestamp.from(trial.endsAt))
            .update()
    }
}
