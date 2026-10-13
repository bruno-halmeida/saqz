package br.com.saqz.groups.adapter.output.jdbc.report

import br.com.saqz.groups.application.report.GroupReportRepository
import br.com.saqz.groups.application.report.ReportGameRow
import br.com.saqz.groups.application.report.ReportLedgerRow
import br.com.saqz.groups.application.report.ReportMemberRow
import br.com.saqz.groups.application.report.ReportOverdueMember
import br.com.saqz.groups.application.report.ReportRange
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcGroupReportRepository(dataSource: DataSource) : GroupReportRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(ROLE)
        .param("actor", actorId).param("group", groupId)
        .query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)

    override fun ownerOf(groupId: UUID): UUID? = jdbc.sql(
        "SELECT owner_user_id FROM access_groups WHERE id = :group AND deleted_at IS NULL",
    ).param("group", groupId).query(UUID::class.java).optional().orElse(null)

    override fun timeZone(groupId: UUID): ZoneId = jdbc.sql(
        "SELECT time_zone FROM access_groups WHERE id = :group AND deleted_at IS NULL",
    ).param("group", groupId).query(String::class.java).optional().map(ZoneId::of).orElse(ZoneOffset.UTC)

    override fun games(groupId: UUID, range: ReportRange, now: Instant): List<ReportGameRow> = jdbc.sql(GAMES)
        .param("group", groupId)
        .param("fromAt", Timestamp.from(range.fromAt))
        .param("toAt", Timestamp.from(range.toAt))
        .param("now", Timestamp.from(now))
        .query { rs, _ ->
            ReportGameRow(
                gameId = rs.getObject("id", UUID::class.java),
                title = rs.getString("title"),
                startsAt = rs.getTimestamp("starts_at").toInstant(),
                capacity = rs.getInt("capacity"),
                status = rs.getString("status"),
                confirmed = rs.getInt("confirmed"),
                confirmedMembers = rs.getInt("confirmed_members"),
                declined = rs.getInt("declined"),
                waitlisted = rs.getInt("waitlisted"),
                noResponse = rs.getInt("no_response"),
            )
        }.list()

    override fun members(groupId: UUID, range: ReportRange, now: Instant): List<ReportMemberRow> = jdbc.sql(MEMBERS)
        .param("group", groupId)
        .param("fromAt", Timestamp.from(range.fromAt))
        .param("toAt", Timestamp.from(range.toAt))
        .param("now", Timestamp.from(now))
        .query { rs, _ ->
            ReportMemberRow(
                userId = rs.getObject("user_id", UUID::class.java),
                name = rs.getString("name"),
                games = rs.getInt("games"),
                confirmed = rs.getInt("confirmed"),
                declined = rs.getInt("declined"),
                responded = rs.getInt("responded"),
            )
        }.list()

    override fun ledger(groupId: UUID, range: ReportRange): List<ReportLedgerRow> = jdbc.sql(LEDGER)
        .param("group", groupId)
        .param("fromAt", Timestamp.from(range.fromAt))
        .param("toAt", Timestamp.from(range.toAt))
        .param("fromDate", range.from)
        .param("toDate", range.toExclusive)
        .query { rs, _ ->
            ReportLedgerRow(
                occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                direction = rs.getString("direction"),
                type = rs.getString("type"),
                title = rs.getString("title"),
                category = rs.getString("category"),
                paidMethod = rs.getString("paid_method"),
                amountCents = rs.getLong("amount_cents"),
            )
        }.list()

    override fun receivableCents(groupId: UUID, range: ReportRange): Long = jdbc.sql(
        """
        SELECT COALESCE(SUM(amount_cents), 0) FROM group_charges
        WHERE group_id = :group AND status = 'PENDING' AND due_date >= :fromDate AND due_date < :toDate
        """.trimIndent(),
    ).param("group", groupId).param("fromDate", range.from).param("toDate", range.toExclusive)
        .query(Long::class.java).single()

    override fun overdueMonthly(groupId: UUID, range: ReportRange, today: LocalDate): List<ReportOverdueMember> =
        jdbc.sql(OVERDUE)
            .param("group", groupId)
            .param("today", today)
            .param("toDate", range.toExclusive)
            .query { rs, _ ->
                ReportOverdueMember(
                    userId = rs.getObject("member_user_id", UUID::class.java),
                    name = rs.getString("name"),
                    amountCents = rs.getLong("amount_cents"),
                    months = rs.getInt("months"),
                )
            }.list()

    private companion object {
        const val ROLE = """
            SELECT CASE WHEN groups.owner_user_id = :actor THEN 'OWNER' ELSE memberships.role::text END
            FROM access_groups groups
            LEFT JOIN group_memberships memberships
              ON memberships.group_id = groups.id AND memberships.user_id = :actor
            WHERE groups.id = :group
              AND groups.deleted_at IS NULL
              AND (groups.owner_user_id = :actor OR memberships.user_id IS NOT NULL)
            """

        // Só jogos que já aconteceram (começaram antes de agora) e não foram cancelados nem rascunho.
        const val GAMES = """
            SELECT g.id, g.title, g.starts_at, g.capacity, g.status::text AS status,
                   COUNT(a.*) FILTER (WHERE a.status = 'CONFIRMED') AS confirmed,
                   COUNT(a.*) FILTER (WHERE a.status = 'CONFIRMED' AND a.guest_seq = 0) AS confirmed_members,
                   COUNT(a.*) FILTER (WHERE a.status = 'DECLINED' AND a.guest_seq = 0) AS declined,
                   COUNT(a.*) FILTER (WHERE a.status = 'WAITLISTED' AND a.guest_seq = 0) AS waitlisted,
                   (SELECT COUNT(*) FROM group_memberships m
                     WHERE m.group_id = g.group_id AND m.active AND m.created_at <= g.starts_at
                       AND NOT EXISTS (
                           SELECT 1 FROM game_attendance x
                           WHERE x.game_id = g.id AND x.member_user_id = m.user_id AND x.guest_seq = 0
                       )) AS no_response
            FROM games g
            JOIN access_groups ag ON ag.id = g.group_id AND ag.deleted_at IS NULL
            LEFT JOIN game_attendance a ON a.game_id = g.id AND a.group_id = g.group_id
            WHERE g.group_id = :group
              AND g.status IN ('PUBLISHED', 'COMPLETED')
              AND g.starts_at >= :fromAt AND g.starts_at < :toAt AND g.starts_at < :now
            GROUP BY g.id
            ORDER BY g.starts_at, g.id
            """

        const val MEMBERS = """
            SELECT m.user_id, u.display_name AS name,
                   COUNT(g.id) AS games,
                   COUNT(a.member_user_id) FILTER (WHERE a.status = 'CONFIRMED') AS confirmed,
                   COUNT(a.member_user_id) FILTER (WHERE a.status = 'DECLINED') AS declined,
                   COUNT(a.member_user_id) AS responded
            FROM group_memberships m
            JOIN access_users u ON u.id = m.user_id
            JOIN access_groups ag ON ag.id = m.group_id AND ag.deleted_at IS NULL
            LEFT JOIN games g
              ON g.group_id = m.group_id
             AND g.status IN ('PUBLISHED', 'COMPLETED')
             AND g.starts_at >= :fromAt AND g.starts_at < :toAt AND g.starts_at < :now
             AND g.starts_at >= m.created_at
            LEFT JOIN game_attendance a
              ON a.game_id = g.id AND a.member_user_id = m.user_id AND a.guest_seq = 0
            WHERE m.group_id = :group AND m.active
            GROUP BY m.user_id, u.display_name
            """

        const val LEDGER = """
            WITH paid_events AS (
                SELECT charges.id AS charge_id, events.occurred_at,
                       row_number() OVER (
                           PARTITION BY charges.id ORDER BY events.occurred_at DESC, events.id DESC
                       ) AS event_rank
                FROM group_charges charges
                JOIN group_charge_events events
                  ON events.charge_id = charges.id AND events.group_id = charges.group_id
                 AND events.new_status = 'PAID'
                WHERE charges.group_id = :group AND charges.status = 'PAID'
            )
            SELECT ledger.occurred_at, ledger.direction, ledger.type, ledger.title, ledger.category,
                   ledger.paid_method, ledger.amount_cents
            FROM (
                SELECT paid.occurred_at, 'IN'::text AS direction, 'CHARGE' AS type,
                       CASE charges.kind::text
                           WHEN 'MONTHLY' THEN 'Mensalidade · ' || COALESCE(charges.member_display_name, '')
                           ELSE 'Cobrança · ' || COALESCE(charges.member_display_name, '')
                       END AS title,
                       charges.kind::text AS category, charges.paid_method::text AS paid_method,
                       charges.amount_cents
                FROM paid_events paid
                JOIN group_charges charges ON charges.id = paid.charge_id
                WHERE paid.event_rank = 1 AND paid.occurred_at >= :fromAt AND paid.occurred_at < :toAt
                UNION ALL
                SELECT expenses.expense_date::timestamp AT TIME ZONE groups.time_zone AS occurred_at,
                       expenses.direction::text, 'EXPENSE', expenses.description,
                       expenses.category::text, NULL::text, expenses.amount_cents
                FROM group_expenses expenses
                JOIN access_groups groups ON groups.id = expenses.group_id AND groups.deleted_at IS NULL
                WHERE expenses.group_id = :group AND expenses.status = 'ACTIVE'
                  AND expenses.expense_date >= :fromDate AND expenses.expense_date < :toDate
            ) ledger
            ORDER BY ledger.occurred_at, ledger.title
            """

        const val OVERDUE = """
            SELECT c.member_user_id,
                   COALESCE(MAX(c.member_display_name), MAX(u.display_name), '') AS name,
                   SUM(c.amount_cents) AS amount_cents, COUNT(*) AS months
            FROM group_charges c
            LEFT JOIN access_users u ON u.id = c.member_user_id
            WHERE c.group_id = :group AND c.kind = 'MONTHLY' AND c.status = 'PENDING'
              AND c.due_date < :today AND c.due_date < :toDate AND c.guest_seq = 0
            GROUP BY c.member_user_id
            ORDER BY SUM(c.amount_cents) DESC, name
            LIMIT 50
            """
    }
}
