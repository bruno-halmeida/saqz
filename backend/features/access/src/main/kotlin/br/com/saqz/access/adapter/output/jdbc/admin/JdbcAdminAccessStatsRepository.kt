package br.com.saqz.access.adapter.output.jdbc.admin

import br.com.saqz.access.application.admin.AdminAccessStats
import br.com.saqz.access.application.admin.CohortWeek
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import javax.sql.DataSource

class JdbcAdminAccessStatsRepository(
    dataSource: DataSource,
) : AdminAccessStats {
    private val jdbc = JdbcClient.create(dataSource)

    override fun totalUsers(): Long =
        single("SELECT count(*) FROM access_users WHERE deleted_at IS NULL")

    override fun newUsers(from: Instant?, to: Instant): Long = single(
        "SELECT count(*) FROM access_users " +
            "WHERE created_at < :to AND (:from::timestamptz IS NULL OR created_at >= :from)",
        mapOf("from" to from?.atOffset(ZoneOffset.UTC), "to" to to.atOffset(ZoneOffset.UTC)),
    )

    override fun activeUsers(since: Instant): Long = single(
        "SELECT count(*) FROM access_users WHERE deleted_at IS NULL AND updated_at >= :since",
        mapOf("since" to since.atOffset(ZoneOffset.UTC)),
    )

    override fun signupCohort(weeksBack: Int, now: Instant): List<CohortWeek> {
        val currentWeekStart = now.atOffset(ZoneOffset.UTC).toLocalDate().with(DayOfWeek.MONDAY)
        return ((weeksBack - 1).toLong() downTo 0L).map { back ->
            val weekStart = currentWeekStart.minusWeeks(back)
            val window = mapOf(
                "from" to weekStart.atStartOfDay().atOffset(ZoneOffset.UTC),
                "to" to weekStart.plusWeeks(1).atStartOfDay().atOffset(ZoneOffset.UTC),
            )
            CohortWeek(
                weekStart = weekStart,
                signups = single(
                    "SELECT count(*) FROM access_users WHERE created_at >= :from AND created_at < :to",
                    window,
                ),
                joinedGroup = single(
                    """
                    SELECT count(*) FROM access_users u
                    WHERE u.created_at >= :from AND u.created_at < :to
                      AND (
                        EXISTS (SELECT 1 FROM group_memberships m WHERE m.user_id = u.id)
                        OR EXISTS (SELECT 1 FROM access_groups g WHERE g.owner_user_id = u.id)
                      )
                    """.trimIndent(),
                    window,
                ),
            )
        }
    }

    private fun single(sql: String, params: Map<String, Any?> = emptyMap()): Long {
        var spec = jdbc.sql(sql)
        params.forEach { (name, value) -> spec = spec.param(name, value) }
        return spec.query(Long::class.java).single()
    }
}
