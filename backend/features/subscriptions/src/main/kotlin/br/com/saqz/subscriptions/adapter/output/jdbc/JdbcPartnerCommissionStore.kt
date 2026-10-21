package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.ClosedCommissionLine
import br.com.saqz.subscriptions.application.CloseCommissionResult
import br.com.saqz.subscriptions.application.CommissionDuration
import br.com.saqz.subscriptions.application.CommissionLine
import br.com.saqz.subscriptions.application.CommissionLineKind
import br.com.saqz.subscriptions.application.CommissionMonth
import br.com.saqz.subscriptions.application.CommissionPayment
import br.com.saqz.subscriptions.application.CommissionRule
import br.com.saqz.subscriptions.application.CommissionStatementLine
import br.com.saqz.subscriptions.application.PartnerCommissionCalculator
import br.com.saqz.subscriptions.application.PartnerCommissionStore
import br.com.saqz.subscriptions.application.PartnerCommissionTotals
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcPartnerCommissionStore(dataSource: DataSource) : PartnerCommissionStore {
    private val jdbc = JdbcClient.create(dataSource)
    private val transaction = TransactionTemplate(DataSourceTransactionManager(dataSource))

    override fun rule(partnerId: UUID): CommissionRule? = jdbc.sql(
        "SELECT commission_percent, commission_duration, commission_months FROM partners WHERE id = :id",
    ).param("id", partnerId).query { rs, _ -> rs.rule() }.optional().orElse(null)

    override fun setRule(partnerId: UUID, rule: CommissionRule?): Boolean = jdbc.sql(
        "UPDATE partners SET commission_percent = :p, commission_duration = :d, commission_months = :m WHERE id = :id",
    )
        .param("id", partnerId)
        .param("p", rule?.percent)
        .param("d", rule?.duration?.name)
        .param("m", rule?.months)
        .update() > 0

    override fun months(partnerId: UUID, now: Instant): List<CommissionMonth>? {
        if (!exists(partnerId)) return null
        val names = customerNames(partnerId)
        val statements = statements(partnerId)
        val closedMonths = statements.map { it.month }.toSet()
        val open = PartnerCommissionCalculator.open(rule(partnerId), payments(partnerId), closedLines(partnerId), closedMonths, now)
        val rule = rule(partnerId)
        val openMonths = open.map { (month, lines) ->
            CommissionMonth(
                month = month,
                closed = false,
                rule = rule,
                totalCents = lines.sumOf { it.commissionCents },
                lines = lines.map { CommissionStatementLine(it, names[it.ownerUserId]) },
                canClose = PartnerCommissionCalculator.canClose(month, closedMonths, now),
            )
        }
        val closed = statements.map { s -> s.copy(lines = s.lines.map { it.copy(customerName = names[it.line.ownerUserId]) }) }
        return (openMonths + closed).sortedByDescending { it.month }
    }

    override fun totals(partnerId: UUID, now: Instant): PartnerCommissionTotals {
        val months = months(partnerId, now).orEmpty()
        return PartnerCommissionTotals(
            openCents = months.filter { !it.closed }.sumOf { it.totalCents },
            closedUnpaidCents = months.filter { it.closed && it.paidOn == null }.sumOf { it.totalCents },
        )
    }

    override fun close(partnerId: UUID, month: YearMonth, now: Instant): CloseCommissionResult = requireNotNull(
        transaction.execute {
            // Trava o parceiro: dois "Fechar mês" ao mesmo tempo não gravam o mesmo pagamento duas vezes.
            val locked = jdbc.sql("SELECT id FROM partners WHERE id = :id FOR UPDATE").param("id", partnerId)
                .query(UUID::class.java).optional().orElse(null)
                ?: return@execute CloseCommissionResult.NotFound
            val closedMonths = statements(locked).map { it.month }.toSet()
            if (month in closedMonths) return@execute CloseCommissionResult.AlreadyClosed
            if (!PartnerCommissionCalculator.canClose(month, closedMonths, now)) return@execute CloseCommissionResult.NotEnded
            val rule = rule(locked)
            val lines = PartnerCommissionCalculator.open(rule, payments(locked), closedLines(locked), closedMonths, now)[month].orEmpty()
            val id = UUID.randomUUID()
            jdbc.sql(
                """
                INSERT INTO partner_commission_statements (id, partner_id, month, commission_percent, commission_duration,
                    commission_months, total_cents, closed_at)
                VALUES (:id, :partner, :month, :p, :d, :m, :total, :now)
                """.trimIndent(),
            )
                .param("id", id)
                .param("partner", locked)
                .param("month", month.atDay(1))
                .param("p", rule?.percent)
                .param("d", rule?.duration?.name)
                .param("m", rule?.months)
                .param("total", lines.sumOf { it.commissionCents })
                .param("now", now.atOffset(ZoneOffset.UTC))
                .update()
            lines.forEach { line ->
                jdbc.sql(
                    """
                    INSERT INTO partner_commission_lines (statement_id, partner_id, kind, source, payment_ref, owner_user_id,
                        paid_at, gross_cents, commission_cents)
                    VALUES (:s, :partner, :kind, :source, :ref, :owner, :paidAt, :gross, :commission)
                    """.trimIndent(),
                )
                    .param("s", id)
                    .param("partner", locked)
                    .param("kind", line.kind.name)
                    .param("source", line.source)
                    .param("ref", line.ref)
                    .param("owner", line.ownerUserId)
                    .param("paidAt", line.paidAt.atOffset(ZoneOffset.UTC))
                    .param("gross", line.grossCents)
                    .param("commission", line.commissionCents)
                    .update()
            }
            CloseCommissionResult.Closed(statements(locked).single { it.month == month })
        },
    )

    override fun markPaid(partnerId: UUID, month: YearMonth, paidOn: LocalDate, note: String?): Boolean = jdbc.sql(
        "UPDATE partner_commission_statements SET paid_on = :on, paid_note = :note WHERE partner_id = :p AND month = :m",
    )
        .param("on", paidOn)
        .param("note", note)
        .param("p", partnerId)
        .param("m", month.atDay(1))
        .update() > 0

    private fun exists(partnerId: UUID) =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM partners WHERE id = :id)").param("id", partnerId).query(Boolean::class.java).single()

    /** Pagamentos reais dos clientes do parceiro desde o primeiro uso do cupom dele; sem valor ou fora de BRL = nulo. */
    private fun payments(partnerId: UUID): List<CommissionPayment> = jdbc.sql(
        """
        SELECT p.source, p.payment_ref, p.owner_user_id, p.paid_at, p.refunded,
               CASE WHEN p.currency = 'BRL' THEN p.gross_cents END AS gross_cents
        FROM subscription_payments p
        JOIN customer_acquisition a ON a.owner_user_id = p.owner_user_id
        WHERE a.partner_id = :partner AND NOT p.test AND p.paid_at >= a.partner_first_used_at
        ORDER BY p.paid_at, p.source, p.payment_ref
        """.trimIndent(),
    ).param("partner", partnerId).query { rs, _ ->
        CommissionPayment(
            source = rs.getString("source"),
            ref = rs.getString("payment_ref"),
            ownerUserId = rs.getObject("owner_user_id", UUID::class.java),
            paidAt = rs.getTimestamp("paid_at").toInstant(),
            grossCents = rs.getObject("gross_cents")?.let { (it as Number).toLong() },
            refunded = rs.getBoolean("refunded"),
        )
    }.list()

    private fun closedLines(partnerId: UUID): List<ClosedCommissionLine> = jdbc.sql(
        "SELECT kind, source, payment_ref, commission_cents FROM partner_commission_lines WHERE partner_id = :p",
    ).param("p", partnerId).query { rs, _ ->
        ClosedCommissionLine(CommissionLineKind.valueOf(rs.getString("kind")), rs.getString("source"), rs.getString("payment_ref"), rs.getLong("commission_cents"))
    }.list()

    private fun statements(partnerId: UUID): List<CommissionMonth> {
        val lines = jdbc.sql(
            """
            SELECT statement_id, kind, source, payment_ref, owner_user_id, paid_at, gross_cents, commission_cents
            FROM partner_commission_lines WHERE partner_id = :p ORDER BY paid_at, source, payment_ref
            """.trimIndent(),
        ).param("p", partnerId).query { rs, _ ->
            rs.getObject("statement_id", UUID::class.java) to CommissionStatementLine(
                CommissionLine(
                    CommissionLineKind.valueOf(rs.getString("kind")), rs.getString("source"), rs.getString("payment_ref"),
                    rs.getObject("owner_user_id", UUID::class.java), rs.getTimestamp("paid_at").toInstant(),
                    rs.getLong("gross_cents"), rs.getLong("commission_cents"),
                ),
                null,
            )
        }.list().groupBy({ it.first }, { it.second })
        return jdbc.sql("SELECT * FROM partner_commission_statements WHERE partner_id = :p ORDER BY month DESC")
            .param("p", partnerId)
            .query { rs, _ ->
                val id = rs.getObject("id", UUID::class.java)
                CommissionMonth(
                    month = YearMonth.from(rs.getDate("month").toLocalDate()),
                    closed = true,
                    rule = rs.rule(),
                    totalCents = rs.getLong("total_cents"),
                    lines = lines[id].orEmpty(),
                    closedAt = rs.getTimestamp("closed_at").toInstant(),
                    paidOn = rs.getDate("paid_on")?.toLocalDate(),
                    paidNote = rs.getString("paid_note"),
                )
            }.list()
    }

    private fun customerNames(partnerId: UUID): Map<UUID, String?> = jdbc.sql(
        """
        SELECT u.id, u.display_name FROM customer_acquisition a JOIN access_users u ON u.id = a.owner_user_id
        WHERE a.partner_id = :p
        """.trimIndent(),
    ).param("p", partnerId).query { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("display_name") }.list().toMap()

    private fun ResultSet.rule(): CommissionRule? {
        val percent = getBigDecimal("commission_percent") ?: return null
        return CommissionRule(
            percent.setScale(2),
            CommissionDuration.valueOf(getString("commission_duration")),
            getObject("commission_months")?.let { (it as Number).toInt() },
        )
    }
}
