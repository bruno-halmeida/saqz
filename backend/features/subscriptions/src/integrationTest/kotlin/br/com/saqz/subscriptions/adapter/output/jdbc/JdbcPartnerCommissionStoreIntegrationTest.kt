package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.CloseCommissionResult
import br.com.saqz.subscriptions.application.CommissionDuration
import br.com.saqz.subscriptions.application.CommissionLineKind
import br.com.saqz.subscriptions.application.CommissionRule
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcPartnerCommissionStoreIntegrationTest {
    private val database = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations())
    private lateinit var store: JdbcPartnerCommissionStore
    private lateinit var jdbc: JdbcClient
    private val now = Instant.parse("2026-10-21T15:00:00Z")
    private val september = YearMonth.of(2026, 9)

    @BeforeAll
    fun start() {
        store = JdbcPartnerCommissionStore(database.dataSource)
        jdbc = JdbcClient.create(database.dataSource)
    }

    @BeforeEach
    fun clear() {
        jdbc.sql(
            "TRUNCATE partner_commission_lines, partner_commission_statements, subscription_events, coupon_store_offers, coupon_redemptions, " +
                "subscriptions, coupons, organizer_trials, trial_coupons, app_store_transactions, app_store_subscriptions, " +
                "google_play_orders, google_play_subscriptions, partners, access_users CASCADE",
        ).update()
    }

    private fun setupPartner(): Pair<UUID, UUID> {
        val partner = (JdbcAdminPartnerDirectory(database.dataSource).create(PartnerCreate("Arena")) as PartnerCreateResult.Created).partner.id
        val coupon = (JdbcAdminCouponDirectoryRepository(database.dataSource)
            .create(AdminCouponCreate("ARENA30", "Arena", 30, null, null, partnerId = partner)) as AdminCouponCreateResult.Created).coupon.id
        val user = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :s, true, 'Ana Cliente', now(), now())")
            .param("id", user).param("s", "sub-$user").update()
        JdbcCouponRepository(database.dataSource).saveRedemption(CouponRedemption(coupon, user, Instant.parse("2026-08-01T12:00:00Z")))
        return partner to user
    }

    private fun sitePayment(user: UUID, id: String, at: String, reais: String) {
        jdbc.sql(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) " +
                "VALUES (:id, :evt, 'PAYMENT_CONFIRMED', CAST(:payload AS jsonb), :at, now(), :o)",
        ).param("id", UUID.randomUUID()).param("evt", "evt-$id")
            .param("payload", """{"payment":{"id":"$id","value":$reais}}""")
            .param("at", Timestamp.from(Instant.parse(at))).param("o", user).update()
    }

    private fun refundSite(user: UUID, id: String) {
        jdbc.sql(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) " +
                "VALUES (:id, :evt, 'PAYMENT_REFUNDED', CAST(:payload AS jsonb), now(), now(), :o)",
        ).param("id", UUID.randomUUID()).param("evt", "evt-ref-$id").param("payload", """{"payment":{"id":"$id","value":0}}""").param("o", user).update()
    }

    @Test
    fun `fechar mês grava o extrato, que não muda com troca de percentual nem fecha duas vezes`() {
        val (partner, user) = setupPartner()
        store.setRule(partner, CommissionRule(BigDecimal("10.00"), CommissionDuration.LIFETIME))
        sitePayment(user, "pay-antes", "2026-07-20T12:00:00Z", "41.93") // antes do cupom: não conta
        sitePayment(user, "pay-set", "2026-09-10T12:00:00Z", "41.93")
        sitePayment(user, "pay-out", "2026-10-10T12:00:00Z", "41.93")

        assertEquals(CloseCommissionResult.NotEnded, store.close(partner, YearMonth.of(2026, 10), now))
        val closed = assertIs<CloseCommissionResult.Closed>(store.close(partner, september, now)).month
        assertEquals(419, closed.totalCents)
        assertEquals("Ana Cliente", store.months(partner, now)!!.single { it.month == september }.lines.single().customerName)
        assertEquals(CloseCommissionResult.AlreadyClosed, store.close(partner, september, now))

        store.setRule(partner, CommissionRule(BigDecimal("50.00"), CommissionDuration.LIFETIME))
        val months = store.months(partner, now)!!.associateBy { it.month }
        assertEquals(419, months.getValue(september).totalCents)
        assertEquals(BigDecimal("10.00"), months.getValue(september).rule?.percent)
        assertEquals(2_097, months.getValue(YearMonth.of(2026, 10)).totalCents) // 4193 × 50% = 2096,5 → 2097
        assertEquals(2_097, store.totals(partner, now).openCents)
        assertEquals(419, store.totals(partner, now).closedUnpaidCents)

        assertTrue(store.markPaid(partner, september, LocalDate.parse("2026-10-21"), "Pix 21/10"))
        assertEquals(0, store.totals(partner, now).closedUnpaidCents)
        assertEquals("Pix 21/10", store.months(partner, now)!!.single { it.month == september }.paidNote)
    }

    @Test
    fun `estorno depois do fechamento desconta no mês em aberto - antes, some`() {
        val (partner, user) = setupPartner()
        store.setRule(partner, CommissionRule(BigDecimal("10.00"), CommissionDuration.LIFETIME))
        sitePayment(user, "pay-set", "2026-09-10T12:00:00Z", "41.93")
        sitePayment(user, "pay-out", "2026-10-02T12:00:00Z", "41.93")
        store.close(partner, september, now)

        refundSite(user, "pay-set")
        refundSite(user, "pay-out")
        val october = store.months(partner, now)!!.single { it.month == YearMonth.of(2026, 10) }
        val refund = october.lines.single { it.line.kind == CommissionLineKind.REFUND }
        assertEquals(-419, refund.line.commissionCents)
        assertTrue(october.lines.none { it.line.ref == "pay-out" })
    }

    @Test
    fun `pedido do Play sem valor entra quando o valor chega, no mês em aberto`() {
        val (partner, user) = setupPartner()
        store.setRule(partner, CommissionRule(BigDecimal("10.00"), CommissionDuration.FIRST_PAYMENT))
        jdbc.sql(
            "INSERT INTO google_play_subscriptions (purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at) " +
                "VALUES ('tok', :o, 'app.saqz.organizador', 'mensal', 'ORGANIZADOR', 'MONTHLY', 'ACTIVE', now() + interval '10 days')",
        ).param("o", user).update()
        jdbc.sql(
            "INSERT INTO google_play_orders (order_id, purchase_token, owner_user_id, product_id, base_plan_id, recorded_at) " +
                "VALUES ('GPA.1', 'tok', :o, 'app.saqz.organizador', 'mensal', '2026-09-05T12:00:00Z')",
        ).param("o", user).update()
        assertEquals(0, assertIs<CloseCommissionResult.Closed>(store.close(partner, september, now)).month.totalCents)

        jdbc.sql("UPDATE google_play_orders SET gross_cents = 4190, currency = 'BRL', order_state = 'PROCESSED' WHERE order_id = 'GPA.1'").update()
        val october = store.months(partner, now)!!.single { it.month == YearMonth.of(2026, 10) }
        assertEquals(419, october.totalCents)
        assertEquals("GOOGLE_PLAY", october.lines.single().line.source)
    }
}
