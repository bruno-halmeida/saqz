package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminPartnerDirectory
import br.com.saqz.subscriptions.application.Partner
import br.com.saqz.subscriptions.application.PartnerCoupon
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.application.PartnerMetrics
import br.com.saqz.subscriptions.application.PartnerPayer
import br.com.saqz.subscriptions.application.PartnerReport
import br.com.saqz.subscriptions.application.PartnerSummary
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

class JdbcAdminPartnerDirectory(dataSource: DataSource) : AdminPartnerDirectory {
    private val jdbc = JdbcClient.create(dataSource)

    override fun list(now: Instant): List<PartnerSummary> {
        val partners = jdbc.sql("SELECT * FROM partners ORDER BY active DESC, created_at DESC, id").query { rs, _ -> rs.partner() }.list()
        val coupons = coupons(null).groupBy({ it.first }, { it.second })
        val people = people(null, null, null, now).groupBy { it.partnerId }
        return partners.map { partner ->
            PartnerSummary(partner, coupons[partner.id].orEmpty(), metrics(people[partner.id].orEmpty()))
        }
    }

    override fun create(command: PartnerCreate): PartnerCreateResult {
        val id = UUID.randomUUID()
        val inserted = jdbc.sql(
            """
            INSERT INTO partners (id, name, contact_name, contact_phone, contact_email, notes)
            SELECT :id, :name, :contactName, :contactPhone, :contactEmail, :notes
            WHERE NOT EXISTS (SELECT 1 FROM partners WHERE lower(name) = lower(:name))
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
            .param("id", id)
            .param("name", command.name)
            .param("contactName", command.contactName)
            .param("contactPhone", command.contactPhone)
            .param("contactEmail", command.contactEmail)
            .param("notes", command.notes)
            .update()
        if (inserted == 0) return PartnerCreateResult.DuplicateName
        return PartnerCreateResult.Created(find(id)!!)
    }

    override fun exists(partnerId: UUID): Boolean =
        jdbc.sql("SELECT EXISTS (SELECT 1 FROM partners WHERE id = :id AND active)").param("id", partnerId).query(Boolean::class.java).single()

    override fun end(partnerId: UUID, now: Instant): Boolean =
        jdbc.sql("UPDATE partners SET active = false, ended_at = COALESCE(ended_at, :now) WHERE id = :id")
            .param("id", partnerId).param("now", now.atOffset(ZoneOffset.UTC)).update() > 0

    override fun report(partnerId: UUID, from: Instant?, to: Instant?, now: Instant): PartnerReport? {
        val partner = find(partnerId) ?: return null
        val people = people(partnerId, from, to, now)
        return PartnerReport(
            partner = partner,
            coupons = coupons(partnerId).map { it.second },
            from = from,
            to = to,
            metrics = metrics(people),
            payers = people.filter { it.firstPaymentAt != null }.sortedByDescending { it.firstPaymentAt }.map { person ->
                PartnerPayer(
                    userId = person.userId,
                    name = person.name,
                    email = person.email,
                    plan = person.plan,
                    cycle = person.cycle,
                    source = requireNotNull(person.firstSource),
                    firstCouponUseAt = person.firstUseAt,
                    firstPaymentAt = requireNotNull(person.firstPaymentAt),
                    totalPaidCents = person.payments.sumOf { it.cents ?: 0 },
                    paymentsWithoutValue = person.payments.count { it.cents == null },
                    status = person.status,
                )
            },
        )
    }

    private fun find(id: UUID): Partner? =
        jdbc.sql("SELECT * FROM partners WHERE id = :id").param("id", id).query { rs, _ -> rs.partner() }.optional().orElse(null)

    private fun coupons(partnerId: UUID?): List<Pair<UUID, PartnerCoupon>> = jdbc.sql(
        """
        SELECT partner_id, id, 'DISCOUNT' AS type, code, campaign, created_at FROM coupons WHERE partner_id IS NOT NULL
        UNION ALL
        SELECT partner_id, id, 'TRIAL', code, campaign, created_at FROM trial_coupons WHERE partner_id IS NOT NULL
        ORDER BY created_at DESC, id
        """.trimIndent(),
    ).query { rs, _ ->
        rs.getObject("partner_id", UUID::class.java) to
            PartnerCoupon(rs.getObject("id", UUID::class.java), rs.getString("type"), rs.getString("code"), rs.getString("campaign"))
    }.list().filter { partnerId == null || it.first == partnerId }

    private data class PaymentRow(val source: String, val at: Instant, val cents: Long?)

    private data class Person(
        val partnerId: UUID,
        val userId: UUID,
        val name: String?,
        val email: String?,
        val firstUseAt: Instant,
        val plan: String?,
        val cycle: String?,
        val status: String,
        val payments: List<PaymentRow>,
    ) {
        val firstPaymentAt: Instant? get() = payments.minOfOrNull { it.at }
        val firstSource: String? get() = payments.minByOrNull { it.at }?.source
    }

    /**
     * Quem veio de parceiro, com os pagamentos depois do primeiro uso do cupom do parceiro. Teste
     * e estorno não contam; valor nulo ou fora de BRL conta como "sem valor".
     */
    private fun people(partnerId: UUID?, from: Instant?, to: Instant?, now: Instant): List<Person> {
        val until = to?.takeIf { it.isBefore(now) } ?: now
        val base = jdbc.sql(
            """
            SELECT a.partner_id, a.owner_user_id, a.partner_first_used_at, u.display_name, u.email,
                   latest.plan::text AS plan, latest.cycle::text AS cycle,
                   CASE
                     WHEN EXISTS (SELECT 1 FROM subscriptions s WHERE s.owner_user_id = a.owner_user_id AND s.first_confirmed_at IS NOT NULL
                                   AND (s.status = 'ACTIVE' OR (s.status = 'CANCELED' AND s.current_period_end > :now)))
                       OR EXISTS (SELECT 1 FROM app_store_subscriptions x WHERE x.owner_user_id = a.owner_user_id AND x.revoked_at IS NULL
                                   AND (x.expires_at > :now OR x.grace_period_expires_at > :now))
                       OR EXISTS (SELECT 1 FROM google_play_subscriptions g WHERE g.owner_user_id = a.owner_user_id AND g.superseded_at IS NULL
                                   AND g.state IN ('ACTIVE', 'CANCELED', 'IN_GRACE_PERIOD') AND g.expires_at > :now)
                       THEN 'ACTIVE'
                     WHEN EXISTS (SELECT 1 FROM subscriptions s WHERE s.owner_user_id = a.owner_user_id AND s.status = 'PAST_DUE' AND s.first_confirmed_at IS NOT NULL)
                       OR EXISTS (SELECT 1 FROM app_store_subscriptions x WHERE x.owner_user_id = a.owner_user_id AND x.in_billing_retry AND x.revoked_at IS NULL)
                       OR EXISTS (SELECT 1 FROM google_play_subscriptions g WHERE g.owner_user_id = a.owner_user_id AND g.superseded_at IS NULL AND g.state = 'ON_HOLD')
                       THEN 'PAST_DUE'
                     ELSE 'CANCELED'
                   END AS status
            FROM customer_acquisition a
            JOIN access_users u ON u.id = a.owner_user_id
            LEFT JOIN LATERAL (
                SELECT plan, cycle FROM (
                    SELECT plan::text AS plan, cycle::text AS cycle, updated_at AS at FROM subscriptions WHERE owner_user_id = a.owner_user_id
                    UNION ALL
                    SELECT plan::text, cycle::text, latest_purchase_date FROM app_store_subscriptions WHERE owner_user_id = a.owner_user_id
                    UNION ALL
                    SELECT plan::text, cycle::text, created_at FROM google_play_subscriptions WHERE owner_user_id = a.owner_user_id AND superseded_at IS NULL
                ) subs ORDER BY at DESC LIMIT 1
            ) latest ON true
            WHERE a.partner_id IS NOT NULL
              AND (CAST(:partner AS uuid) IS NULL OR a.partner_id = CAST(:partner AS uuid))
              AND (CAST(:from AS timestamptz) IS NULL OR a.partner_first_used_at >= CAST(:from AS timestamptz))
              AND a.partner_first_used_at <= :until
            """.trimIndent(),
        )
            .param("partner", partnerId)
            .param("from", from?.atOffset(ZoneOffset.UTC))
            .param("until", until.atOffset(ZoneOffset.UTC))
            .param("now", now.atOffset(ZoneOffset.UTC))
            .query { rs, _ -> rs.person() }
            .list()
        if (base.isEmpty()) return base
        val payments = jdbc.sql(
            """
            SELECT p.owner_user_id, p.source, p.paid_at, CASE WHEN p.currency = 'BRL' THEN p.gross_cents END AS cents
            FROM subscription_payments p
            JOIN customer_acquisition a ON a.owner_user_id = p.owner_user_id AND a.partner_id IS NOT NULL
            WHERE NOT p.test AND NOT p.refunded
              AND p.paid_at >= a.partner_first_used_at
              AND (CAST(:from AS timestamptz) IS NULL OR p.paid_at >= CAST(:from AS timestamptz))
              AND p.paid_at <= :until
            """.trimIndent(),
        )
            .param("from", from?.atOffset(ZoneOffset.UTC))
            .param("until", until.atOffset(ZoneOffset.UTC))
            .query { rs, _ ->
                rs.getObject("owner_user_id", UUID::class.java) to PaymentRow(
                    rs.getString("source"),
                    rs.getTimestamp("paid_at").toInstant(),
                    rs.getObject("cents")?.let { (it as Number).toLong() },
                )
            }
            .list()
            .groupBy({ it.first }, { it.second })
        return base.map { it.copy(payments = payments[it.userId].orEmpty()) }
    }

    private fun metrics(people: List<Person>): PartnerMetrics {
        val payments = people.flatMap { it.payments }
        return PartnerMetrics(
            users = people.size,
            payers = people.count { it.payments.isNotEmpty() },
            activeSubscribers = people.count { it.payments.isNotEmpty() && it.status == "ACTIVE" },
            revenueCents = payments.sumOf { it.cents ?: 0 },
            revenueBySource = payments.filter { it.cents != null }.groupBy { it.source }.mapValues { (_, list) -> list.sumOf { it.cents!! } },
            paymentsWithoutValue = payments.count { it.cents == null },
        )
    }

    private fun ResultSet.person() = Person(
        partnerId = getObject("partner_id", UUID::class.java),
        userId = getObject("owner_user_id", UUID::class.java),
        name = getString("display_name"),
        email = getString("email"),
        firstUseAt = getTimestamp("partner_first_used_at").toInstant(),
        plan = getString("plan"),
        cycle = getString("cycle"),
        status = getString("status"),
        payments = emptyList(),
    )

    private fun ResultSet.partner() = Partner(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        contactName = getString("contact_name"),
        contactPhone = getString("contact_phone"),
        contactEmail = getString("contact_email"),
        notes = getString("notes"),
        active = getBoolean("active"),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}
