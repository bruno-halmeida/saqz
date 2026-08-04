package br.com.saqz.groups.adapter.output.jdbc.admin

import br.com.saqz.groups.application.admin.AdminGroupStats
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

class JdbcAdminGroupStatsRepository(
    dataSource: DataSource,
) : AdminGroupStats {
    private val jdbc = JdbcClient.create(dataSource)

    override fun activeGroups(): Long =
        jdbc.sql("SELECT count(*) FROM access_groups WHERE deleted_at IS NULL")
            .query(Long::class.java).single()

    override fun groupsCreated(from: Instant?, to: Instant): Long = jdbc.sql(
        "SELECT count(*) FROM access_groups " +
            "WHERE created_at < :to AND (:from::timestamptz IS NULL OR created_at >= :from)",
    )
        .param("from", from?.atOffset(ZoneOffset.UTC))
        .param("to", to.atOffset(ZoneOffset.UTC))
        .query(Long::class.java).single()

    override fun gamesPlayed(from: Instant?, to: Instant): Long = jdbc.sql(
        """
        SELECT count(*) FROM games
        WHERE status IN ('PUBLISHED', 'COMPLETED')
          AND starts_at < :to
          AND (:from::timestamptz IS NULL OR starts_at >= :from)
        """.trimIndent(),
    )
        .param("from", from?.atOffset(ZoneOffset.UTC))
        .param("to", to.atOffset(ZoneOffset.UTC))
        .query(Long::class.java).single()
}
