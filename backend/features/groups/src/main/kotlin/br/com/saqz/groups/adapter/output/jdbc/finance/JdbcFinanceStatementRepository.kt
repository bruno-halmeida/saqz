package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.statement.FinanceStatementDirection
import br.com.saqz.groups.application.finance.statement.FinanceStatementItem
import br.com.saqz.groups.application.finance.statement.FinanceStatementPage
import br.com.saqz.groups.application.finance.statement.FinanceStatementQuery
import br.com.saqz.groups.application.finance.statement.FinanceStatementRepository
import br.com.saqz.groups.application.finance.statement.FinanceStatementSummary
import br.com.saqz.groups.application.finance.statement.FinanceStatementType
import br.com.saqz.groups.domain.GroupRole
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Types
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcFinanceStatementRepository(
    dataSource: DataSource,
) : FinanceStatementRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun role(actorId: UUID, groupId: UUID): GroupRole? = jdbc.sql(ROLE)
        .param("actor", actorId)
        .param("group", groupId)
        .query(String::class.java)
        .optional()
        .map(GroupRole::valueOf)
        .orElse(null)

    override fun timeZone(groupId: UUID): ZoneId = jdbc.sql(TIME_ZONE)
        .param("group", groupId)
        .query(String::class.java)
        .optional()
        .map(ZoneId::of)
        .orElse(ZoneOffset.UTC)

    override fun page(query: FinanceStatementQuery): FinanceStatementPage {
        require(query.limit > 0) { "limit must be positive" }
        require(query.offset >= 0) { "offset must not be negative" }

        val fetched = jdbc.sql(PAGE)
            .param("group", query.groupId)
            .param("monthStart", query.month.atDay(1))
            .param("monthEnd", query.month.plusMonths(1).atDay(1))
            .param("direction", query.direction?.name, Types.VARCHAR)
            .param("limit", query.limit + 1)
            .param("offset", query.offset)
            .query(::mapItem)
            .list()
        val summary = summary(query)
        return FinanceStatementPage(
            month = query.month,
            items = fetched.take(query.limit),
            summary = summary,
            limit = query.limit,
            offset = query.offset,
            hasMore = fetched.size > query.limit,
        )
    }

    private fun summary(query: FinanceStatementQuery): FinanceStatementSummary {
        val row = jdbc.sql(SUMMARY)
            .param("group", query.groupId)
            .param("monthStart", query.month.atDay(1))
            .param("monthEnd", query.month.plusMonths(1).atDay(1))
            .query { rs, _ ->
                SummaryRow(
                    totalInCents = rs.getLong("total_in_cents"),
                    totalOutCents = rs.getLong("total_out_cents"),
                    accumulatedBalanceCents = rs.getLong("accumulated_balance_cents"),
                    openingBalanceCents = rs.getObject("opening_balance_cents", java.lang.Long::class.java)?.toLong(),
                )
            }
            .single()
        return FinanceStatementSummary(
            totalInCents = row.totalInCents,
            totalOutCents = row.totalOutCents,
            periodBalanceCents = row.totalInCents - row.totalOutCents,
            accumulatedBalanceCents = row.accumulatedBalanceCents,
            openingBalanceCents = row.openingBalanceCents,
        )
    }

    private fun mapItem(rs: ResultSet, row: Int): FinanceStatementItem = FinanceStatementItem(
        id = rs.getObject("id", UUID::class.java),
        type = FinanceStatementType.valueOf(rs.getString("type")),
        direction = FinanceStatementDirection.valueOf(rs.getString("direction")),
        title = rs.getString("title"),
        category = rs.getString("category"),
        paidMethod = rs.getString("paid_method"),
        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
        amountCents = rs.getLong("amount_cents"),
        settledBy = rs.getString("settled_by"),
        appMethod = rs.getString("app_method"),
        origin = rs.getString("origin"),
    )

    private data class SummaryRow(
        val totalInCents: Long,
        val totalOutCents: Long,
        val accumulatedBalanceCents: Long,
        val openingBalanceCents: Long?,
    )

    private companion object {
        const val TIME_ZONE = """
            SELECT time_zone
            FROM access_groups
            WHERE id = :group
              AND deleted_at IS NULL
            """

        const val ROLE = """
            SELECT CASE WHEN groups.owner_user_id = :actor THEN 'OWNER' ELSE memberships.role::text END
            FROM access_groups groups
            LEFT JOIN group_memberships memberships
              ON memberships.group_id = groups.id AND memberships.user_id = :actor
            WHERE groups.id = :group
              AND groups.deleted_at IS NULL
              AND (groups.owner_user_id = :actor OR memberships.user_id IS NOT NULL)
            """

        /** Valor recebido de uma cobrança paga: o pago pelo app (payable) ou o valor da cobrança. */
        const val PAID_AMOUNT = "CASE WHEN charges.settled_by = 'APP' THEN coalesce(charges.payable_cents, charges.amount_cents) ELSE charges.amount_cents END"

        const val PAID_CHARGE_EVENTS = """
            SELECT charges.id AS charge_id,
                   charges.group_id,
                   -- FIN-014: pago pelo app entra pelo que o atleta pagou; as taxas saem na saída automática.
                   $PAID_AMOUNT AS amount_cents,
                   events.occurred_at,
                   groups.time_zone,
                   row_number() OVER (
                       PARTITION BY charges.id
                       ORDER BY events.occurred_at DESC, events.id DESC
                   ) AS event_rank
            FROM group_charges charges
            JOIN access_groups groups
              ON groups.id = charges.group_id AND groups.deleted_at IS NULL
            JOIN group_charge_events events
              ON events.charge_id = charges.id
             AND events.group_id = charges.group_id
             AND events.new_status = 'PAID'
            WHERE charges.group_id = :group
              AND charges.status = 'PAID'
            """

        const val PAGE = """
            WITH paid_charge_events AS ($PAID_CHARGE_EVENTS)
            SELECT ledger.id,
                   ledger.type,
                   ledger.direction,
                   ledger.title,
                   ledger.category,
                   ledger.paid_method,
                   ledger.occurred_at,
                   ledger.amount_cents,
                   ledger.settled_by,
                   ledger.app_method,
                   ledger.origin
            FROM (
                SELECT charges.id,
                       'CHARGE' AS type,
                       'IN'::text AS direction,
                       CASE charges.kind::text
                           WHEN 'MONTHLY' THEN 'Mensalidade · ' || charges.member_display_name
                           ELSE 'Cobrança · ' || charges.member_display_name
                       END AS title,
                       charges.kind::text AS category,
                       charges.paid_method::text AS paid_method,
                       paid.occurred_at,
                       $PAID_AMOUNT AS amount_cents,
                       charges.settled_by::text AS settled_by,
                       charges.app_method::text AS app_method,
                       NULL::text AS origin
                FROM paid_charge_events paid
                JOIN group_charges charges ON charges.id = paid.charge_id
                JOIN access_groups groups
                  ON groups.id = charges.group_id AND groups.deleted_at IS NULL
                WHERE paid.event_rank = 1
                  AND (paid.occurred_at AT TIME ZONE groups.time_zone) >= :monthStart
                  AND (paid.occurred_at AT TIME ZONE groups.time_zone) < :monthEnd
                  AND (:direction IS NULL OR :direction = 'IN')
                UNION ALL
                SELECT expenses.id,
                       'EXPENSE' AS type,
                       expenses.direction::text,
                       expenses.description AS title,
                       expenses.category::text,
                       NULL::text AS paid_method,
                       expenses.expense_date::timestamp AT TIME ZONE groups.time_zone AS occurred_at,
                       CASE expenses.direction::text
                           WHEN 'OUT' THEN -expenses.amount_cents
                           ELSE expenses.amount_cents
                       END AS amount_cents,
                       NULL::text AS settled_by,
                       NULL::text AS app_method,
                       expenses.origin::text AS origin
                FROM group_expenses expenses
                JOIN access_groups groups
                  ON groups.id = expenses.group_id AND groups.deleted_at IS NULL
                WHERE expenses.group_id = :group
                  AND expenses.status = 'ACTIVE'
                  AND expenses.expense_date >= :monthStart
                  AND expenses.expense_date < :monthEnd
                  AND (:direction IS NULL OR expenses.direction::text = :direction)
            ) ledger
            ORDER BY ledger.occurred_at DESC, ledger.id DESC
            LIMIT :limit OFFSET :offset
            """

        const val SUMMARY = """
            WITH paid_charge_events AS ($PAID_CHARGE_EVENTS)
            SELECT
                COALESCE((
                    SELECT SUM(paid.amount_cents)
                    FROM paid_charge_events paid
                    WHERE paid.event_rank = 1
                      AND (paid.occurred_at AT TIME ZONE paid.time_zone) >= :monthStart
                      AND (paid.occurred_at AT TIME ZONE paid.time_zone) < :monthEnd
                ), 0)
                + COALESCE((
                    SELECT SUM(expenses.amount_cents)
                    FROM group_expenses expenses
                    WHERE expenses.group_id = :group
                      AND expenses.status = 'ACTIVE'
                      AND expenses.direction = 'IN'
                      AND expenses.expense_date >= :monthStart
                      AND expenses.expense_date < :monthEnd
                ), 0) AS total_in_cents,
                COALESCE((
                    SELECT SUM(expenses.amount_cents)
                    FROM group_expenses expenses
                    WHERE expenses.group_id = :group
                      AND expenses.status = 'ACTIVE'
                      AND expenses.direction = 'OUT'
                      AND expenses.expense_date >= :monthStart
                      AND expenses.expense_date < :monthEnd
                ), 0) AS total_out_cents,
                COALESCE((
                    SELECT SUM($PAID_AMOUNT)
                    FROM group_charges charges
                    WHERE charges.group_id = :group AND charges.status = 'PAID'
                ), 0)
                + COALESCE((
                    SELECT SUM(expenses.amount_cents)
                    FROM group_expenses expenses
                    WHERE expenses.group_id = :group
                      AND expenses.status = 'ACTIVE'
                      AND expenses.direction = 'IN'
                ), 0)
                - COALESCE((
                    SELECT SUM(expenses.amount_cents)
                    FROM group_expenses expenses
                    WHERE expenses.group_id = :group
                      AND expenses.status = 'ACTIVE'
                      AND expenses.direction = 'OUT'
                ), 0)
                -- Saldo inicial (DEB-148): soma sempre, sem data.
                + COALESCE((SELECT opening_balance_cents FROM access_groups WHERE id = :group), 0) AS accumulated_balance_cents,
                (SELECT opening_balance_cents FROM access_groups WHERE id = :group) AS opening_balance_cents
            """
    }
}
