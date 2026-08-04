package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.overview.FinanceOverviewGroup
import br.com.saqz.groups.application.finance.overview.FinanceOverviewPeriod
import br.com.saqz.groups.application.finance.overview.FinanceOverviewReadModel
import br.com.saqz.groups.application.finance.overview.FinanceOverviewRepository
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTotals
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTransaction
import br.com.saqz.groups.application.finance.overview.FinanceOverviewTransactionKind
import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

class JdbcFinanceOverviewRepository(
    dataSource: DataSource,
    private val zoneId: ZoneId = ZoneId.of("America/Sao_Paulo"),
) : FinanceOverviewRepository {
    private val jdbc = JdbcClient.create(dataSource)

    override fun find(actorId: UUID, period: FinanceOverviewPeriod): FinanceOverviewReadModel {
        val startAt = Timestamp.from(period.startDate.atStartOfDay(zoneId).toInstant())
        val endAt = Timestamp.from(period.endDate.plusDays(1).atStartOfDay(zoneId).toInstant())
        val groups = jdbc.sql(GROUPS)
            .param("actorId", actorId)
            .param("startDate", period.startDate)
            .param("endDate", period.endDate)
            .param("startAt", startAt)
            .param("endAt", endAt)
            .query(::mapGroup)
            .list()
        val recent = (recentMonthly(actorId, startAt, endAt) + recentLaunches(actorId, period))
            .sortedWith(compareByDescending<FinanceOverviewTransaction> { it.occurredAt }.thenByDescending { it.id })
            .take(5)

        return FinanceOverviewReadModel(
            period = period,
            totals = FinanceOverviewTotals(
                balanceCents = groups.sumOf { it.balanceCents },
                inCents = groups.sumOf { it.inCents },
                outCents = groups.sumOf { it.outCents },
                pendingCents = groups.sumOf { it.pendingCents },
            ),
            groups = groups.map {
                FinanceOverviewGroup(
                    id = it.id,
                    name = it.name,
                    balanceCents = it.balanceCents,
                    pendingMonthlyCount = it.pendingMonthlyCount,
                    hasBillingConfigured = it.hasBillingConfigured,
                )
            },
            recentTransactions = recent,
        )
    }

    private fun recentMonthly(
        actorId: UUID,
        startAt: Timestamp,
        endAt: Timestamp,
    ): List<FinanceOverviewTransaction> = jdbc.sql(RECENT_MONTHLY)
        .param("actorId", actorId)
        .param("startAt", startAt)
        .param("endAt", endAt)
        .query { result, _ ->
            FinanceOverviewTransaction(
                id = result.getObject("transaction_id", UUID::class.java),
                groupId = result.getObject("group_id", UUID::class.java),
                groupName = result.getString("group_name"),
                kind = FinanceOverviewTransactionKind.MONTHLY,
                direction = null,
                memberName = result.getString("member_name"),
                description = null,
                amountCents = result.getLong("amount_cents"),
                occurredAt = result.getTimestamp("occurred_at").toInstant(),
            )
        }
        .list()

    private fun recentLaunches(
        actorId: UUID,
        period: FinanceOverviewPeriod,
    ): List<FinanceOverviewTransaction> = jdbc.sql(RECENT_LAUNCHES)
        .param("actorId", actorId)
        .param("startDate", period.startDate)
        .param("endDate", period.endDate)
        .query { result, _ ->
            val expenseDate = result.getObject("expense_date", java.time.LocalDate::class.java)
            FinanceOverviewTransaction(
                id = result.getObject("transaction_id", UUID::class.java),
                groupId = result.getObject("group_id", UUID::class.java),
                groupName = result.getString("group_name"),
                kind = FinanceOverviewTransactionKind.LAUNCH,
                direction = ExpenseDirection.valueOf(result.getString("direction")),
                memberName = null,
                description = result.getString("description"),
                amountCents = result.getLong("amount_cents"),
                occurredAt = expenseDate.atStartOfDay(zoneId).toInstant(),
            )
        }
        .list()

    private fun mapGroup(result: ResultSet, @Suppress("UNUSED_PARAMETER") row: Int): GroupRow = GroupRow(
        id = result.getObject("group_id", UUID::class.java),
        name = result.getString("group_name"),
        balanceCents = result.getLong("balance_cents"),
        inCents = result.getLong("in_cents"),
        outCents = result.getLong("out_cents"),
        pendingCents = result.getLong("pending_cents"),
        pendingMonthlyCount = result.getInt("pending_monthly_count"),
        hasBillingConfigured = result.getBoolean("has_billing_configured"),
    )

    private data class GroupRow(
        val id: UUID,
        val name: String,
        val balanceCents: Long,
        val inCents: Long,
        val outCents: Long,
        val pendingCents: Long,
        val pendingMonthlyCount: Int,
        val hasBillingConfigured: Boolean,
    )

    private companion object {
        const val ADMINISTERED_GROUPS = """
            SELECT groups.id, groups.name, groups.monthly_fee_cents
            FROM access_groups groups
            WHERE groups.deleted_at IS NULL
              AND (
                  groups.owner_user_id = :actorId
                  OR EXISTS (
                      SELECT 1
                      FROM group_memberships memberships
                      WHERE memberships.group_id = groups.id
                        AND memberships.user_id = :actorId
                        AND memberships.role = 'ADMIN'
                  )
              )
        """

        const val GROUPS = """
            WITH administered_groups AS ($ADMINISTERED_GROUPS)
            SELECT
                groups.id AS group_id,
                groups.name AS group_name,
                (
                    COALESCE((
                        SELECT SUM(charges.amount_cents)
                        FROM group_charges charges
                        WHERE charges.group_id = groups.id
                          AND charges.status = 'PAID'
                    ), 0)
                    + COALESCE((
                        SELECT SUM(expenses.amount_cents)
                        FROM group_expenses expenses
                        WHERE expenses.group_id = groups.id
                          AND expenses.status = 'ACTIVE'
                          AND expenses.direction = 'IN'
                    ), 0)
                    - COALESCE((
                        SELECT SUM(expenses.amount_cents)
                        FROM group_expenses expenses
                        WHERE expenses.group_id = groups.id
                          AND expenses.status = 'ACTIVE'
                          AND expenses.direction = 'OUT'
                    ), 0)
                ) AS balance_cents,
                (
                    COALESCE((
                        SELECT SUM(charges.amount_cents)
                        FROM group_charges charges
                        JOIN group_charge_events events
                          ON events.charge_id = charges.id
                         AND events.new_status = 'PAID'
                        WHERE charges.group_id = groups.id
                          AND charges.status = 'PAID'
                          AND events.occurred_at >= :startAt
                          AND events.occurred_at < :endAt
                    ), 0)
                    + COALESCE((
                        SELECT SUM(expenses.amount_cents)
                        FROM group_expenses expenses
                        WHERE expenses.group_id = groups.id
                          AND expenses.status = 'ACTIVE'
                          AND expenses.direction = 'IN'
                          AND expenses.expense_date BETWEEN :startDate AND :endDate
                    ), 0)
                ) AS in_cents,
                COALESCE((
                    SELECT SUM(expenses.amount_cents)
                    FROM group_expenses expenses
                    WHERE expenses.group_id = groups.id
                      AND expenses.status = 'ACTIVE'
                      AND expenses.direction = 'OUT'
                      AND expenses.expense_date BETWEEN :startDate AND :endDate
                ), 0) AS out_cents,
                COALESCE((
                    SELECT SUM(charges.amount_cents)
                    FROM group_charges charges
                    WHERE charges.group_id = groups.id
                      AND charges.status = 'PENDING'
                      AND charges.due_date BETWEEN :startDate AND :endDate
                ), 0) AS pending_cents,
                (
                    SELECT COUNT(*)
                    FROM group_charges charges
                    WHERE charges.group_id = groups.id
                      AND charges.kind = 'MONTHLY'
                      AND charges.status = 'PENDING'
                      AND charges.due_date <= :endDate
                ) AS pending_monthly_count,
                (
                    groups.monthly_fee_cents IS NOT NULL
                    OR EXISTS (
                        SELECT 1
                        FROM group_memberships memberships
                        WHERE memberships.group_id = groups.id
                          AND memberships.membership_type = 'MENSALISTA'
                          AND memberships.active = true
                          AND COALESCE(memberships.monthly_fee_cents, groups.monthly_fee_cents) IS NOT NULL
                    )
                ) AS has_billing_configured
            FROM administered_groups groups
            ORDER BY groups.name, groups.id
        """

        const val RECENT_MONTHLY = """
            WITH administered_groups AS ($ADMINISTERED_GROUPS)
            SELECT
                events.id AS transaction_id,
                groups.id AS group_id,
                groups.name AS group_name,
                charges.member_display_name AS member_name,
                charges.amount_cents,
                events.occurred_at
            FROM group_charge_events events
            JOIN group_charges charges ON charges.id = events.charge_id
            JOIN administered_groups groups ON groups.id = charges.group_id
            WHERE charges.kind = 'MONTHLY'
              AND charges.status = 'PAID'
              AND events.new_status = 'PAID'
              AND events.occurred_at >= :startAt
              AND events.occurred_at < :endAt
        """

        const val RECENT_LAUNCHES = """
            WITH administered_groups AS ($ADMINISTERED_GROUPS)
            SELECT
                expenses.id AS transaction_id,
                groups.id AS group_id,
                groups.name AS group_name,
                expenses.description,
                expenses.amount_cents,
                expenses.expense_date,
                expenses.direction
            FROM group_expenses expenses
            JOIN administered_groups groups ON groups.id = expenses.group_id
            WHERE expenses.status = 'ACTIVE'
              AND expenses.expense_date BETWEEN :startDate AND :endDate
        """
    }
}
