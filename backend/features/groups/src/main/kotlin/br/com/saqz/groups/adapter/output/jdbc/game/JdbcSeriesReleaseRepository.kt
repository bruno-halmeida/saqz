package br.com.saqz.groups.adapter.output.jdbc.game

import br.com.saqz.groups.application.game.series.ReleasableOccurrence
import br.com.saqz.groups.application.game.series.ReleasableOccurrenceRepository
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource

class JdbcSeriesReleaseRepository(dataSource: DataSource) : ReleasableOccurrenceRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun nextOccurrences(after: Instant, until: Instant, groupId: UUID?): List<ReleasableOccurrence> =
        jdbc.sql(NEXT_OCCURRENCES)
            .param("after", Timestamp.from(after))
            .param("until", Timestamp.from(until))
            .param("groupId", groupId)
            .query { rs, _ ->
                ReleasableOccurrence(
                    groupId = rs.getObject("group_id", UUID::class.java),
                    gameId = rs.getObject("id", UUID::class.java),
                    version = rs.getLong("version"),
                    ownerId = rs.getObject("owner_user_id", UUID::class.java),
                )
            }
            .list()

    private companion object {
        // Rascunho avulso (series_id nulo) é do gestor e não entra; jogo que já começou também não.
        const val NEXT_OCCURRENCES = """
            SELECT DISTINCT ON (g.group_id, g.series_id, g.slot_key)
                   g.group_id, g.id, g.version, groups.owner_user_id
            FROM games g
            JOIN access_groups groups ON groups.id = g.group_id AND groups.deleted_at IS NULL
            WHERE g.series_id IS NOT NULL
              AND g.status = 'DRAFT'
              AND g.starts_at > :after
              AND g.starts_at <= :until
              AND (CAST(:groupId AS uuid) IS NULL OR g.group_id = CAST(:groupId AS uuid))
            ORDER BY g.group_id, g.series_id, g.slot_key, g.starts_at, g.id
        """
    }
}
