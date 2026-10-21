package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.CouponStoreAttribution
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.domain.AppStoreEnvironment
import br.com.saqz.subscriptions.domain.AppStoreTransaction
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.GooglePlayPurchase
import br.com.saqz.subscriptions.domain.GooglePlayState
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminPartnerDirectoryIntegrationTest {
    private val database = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations())
    private lateinit var partners: JdbcAdminPartnerDirectory
    private lateinit var coupons: JdbcAdminCouponDirectoryRepository
    private lateinit var jdbc: JdbcClient
    private val now: Instant = Instant.now()
    private val day: Duration = Duration.ofDays(1)

    @BeforeAll
    fun start() {
        partners = JdbcAdminPartnerDirectory(database.dataSource)
        coupons = JdbcAdminCouponDirectoryRepository(database.dataSource)
        jdbc = JdbcClient.create(database.dataSource)
    }

    @BeforeEach
    fun clear() {
        jdbc.sql(
            "TRUNCATE subscription_events, coupon_store_offers, coupon_redemptions, subscriptions, coupons, organizer_trials, " +
                "trial_coupons, app_store_transactions, app_store_subscriptions, google_play_orders, google_play_subscriptions, partners, access_users CASCADE",
        ).update()
    }

    private fun partner(name: String) = (partners.create(PartnerCreate(name, contactPhone = "11999990000")) as PartnerCreateResult.Created).partner.id

    private fun discount(code: String, partnerId: UUID?, stores: Boolean = false) = (
        coupons.create(AdminCouponCreate(code, "Campanha $code", 30, if (stores) 3 else null, null, stores = stores, partnerId = partnerId))
            as AdminCouponCreateResult.Created
        ).coupon.id

    private fun trialCoupon(code: String, partnerId: UUID): UUID {
        val id = UUID.randomUUID()
        jdbc.sql("INSERT INTO trial_coupons (id, code, trial_days, created_at, partner_id) VALUES (:id, :code, 30, now(), :p)")
            .param("id", id).param("code", code).param("p", partnerId).update()
        return id
    }

    private fun user(name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.sql(
            "INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) " +
                "VALUES (:id, :sub, :email, true, :name, now(), now())",
        ).param("id", id).param("sub", "sub-$id").param("email", "${name.lowercase()}@saqz.test").param("name", name).update()
        return id
    }

    private fun redeem(coupon: UUID, user: UUID, at: Instant, platform: CouponPlatform = CouponPlatform.WEB) =
        JdbcCouponRepository(database.dataSource).saveRedemption(CouponRedemption(coupon, user, at, platform))

    private fun trialStart(coupon: UUID, user: UUID, at: Instant) {
        jdbc.sql("INSERT INTO organizer_trials (owner_user_id, started_at, ends_at, coupon_id) VALUES (:o, :s, :e, :c)")
            .param("o", user).param("s", java.sql.Timestamp.from(at)).param("e", java.sql.Timestamp.from(at.plus(day.multipliedBy(30)))).param("c", coupon).update()
    }

    private fun sitePayment(user: UUID, at: Instant, reais: String) {
        jdbc.sql(
            "INSERT INTO subscription_events (id, asaas_event_id, type, payload, processed_at, created_at, owner_user_id) " +
                "VALUES (:id, :evt, 'PAYMENT_CONFIRMED', CAST(:payload AS jsonb), :at, now(), :o)",
        ).param("id", UUID.randomUUID()).param("evt", "evt-${UUID.randomUUID()}")
            .param("payload", """{"payment":{"id":"pay-${UUID.randomUUID()}","value":$reais}}""")
            .param("at", java.sql.Timestamp.from(at)).param("o", user).update()
        jdbc.sql(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, asaas_subscription_id, current_period_end, " +
                "first_confirmed_at, created_at, updated_at) VALUES (:o, 'ORGANIZADOR', 'MONTHLY', 'ACTIVE', :c, :s, :end, :at, :at, :at) " +
                "ON CONFLICT DO NOTHING",
        ).param("o", user).param("c", "cus-$user").param("s", "sub-$user").param("end", java.sql.Timestamp.from(now.plus(day.multipliedBy(20))))
            .param("at", java.sql.Timestamp.from(at)).update()
    }

    @Test
    fun `primeiro toque - vale o primeiro parceiro e pagamento antes do cupom não conta`() {
        val arena = partner("Arena Beach Moema")
        val outra = partner("Quadra Central")
        val arenaDesconto = discount("ARENA30", arena)
        val arenaTeste = trialCoupon("ARENATESTE", arena)
        val outraDesconto = discount("CENTRAL20", outra)
        val semParceiro = discount("GERAL10", null)

        // Ana: cupom geral, depois teste da Arena, depois cupom da Central: origem é a Arena.
        val ana = user("Ana")
        redeem(semParceiro, ana, now.minus(day.multipliedBy(10)))
        trialStart(arenaTeste, ana, now.minus(day.multipliedBy(9)))
        redeem(outraDesconto, ana, now.minus(day.multipliedBy(8)))
        sitePayment(ana, now.minus(day.multipliedBy(7)), "41.93")

        // Bia: pagou antes de usar o cupom da Arena; só o pagamento depois conta.
        val bia = user("Bia")
        sitePayment(bia, now.minus(day.multipliedBy(20)), "59.90")
        redeem(arenaDesconto, bia, now.minus(day.multipliedBy(5)))

        val report = partners.report(arena, null, null, now)!!
        assertEquals(2, report.metrics.users)
        assertEquals(1, report.metrics.payers)
        assertEquals(4_193, report.metrics.revenueCents)
        assertEquals(mapOf("SITE" to 4_193L), report.metrics.revenueBySource)
        val payer = report.payers.single()
        assertEquals("Ana", payer.name)
        assertEquals("ana@saqz.test", payer.email)
        assertEquals("ORGANIZADOR", payer.plan)
        assertEquals("SITE", payer.source)
        assertEquals("ACTIVE", payer.status)
        assertEquals(1, report.metrics.activeSubscribers)
        assertEquals(setOf("ARENA30", "ARENATESTE"), report.coupons.map { it.code }.toSet())

        val central = partners.report(outra, null, null, now)!!
        assertEquals(0, central.metrics.users)

        val list = partners.list(now).associateBy { it.partner.name }
        assertEquals(2, list["Arena Beach Moema"]!!.metrics.users)
        assertEquals(0, list["Quadra Central"]!!.metrics.users)
    }

    @Test
    fun `compra pela Apple e pelo Play atribuída pela oferta - Play sem valor conta como desconhecido`() {
        val arena = partner("Arena")
        val cupom = discount("ARENA30", arena, stores = true)
        val offers = JdbcCouponStoreOfferRepository(database.dataSource)
        val attribution = CouponStoreAttribution(offers, JdbcCouponRepository(database.dataSource))

        val carla = user("Carla")
        val compra = now.minus(day.multipliedBy(3))
        jdbc.sql(
            "INSERT INTO app_store_subscriptions (original_transaction_id, owner_user_id, environment, product_id, plan, cycle, " +
                "latest_transaction_id, latest_purchase_date, latest_signed_at, expires_at) " +
                "VALUES ('otx-1', :o, 'PRODUCTION', 'app.saqz.organizador.mensal', 'ORGANIZADOR', 'MONTHLY', 'tx-1', :at, :at, :exp)",
        ).param("o", carla).param("at", java.sql.Timestamp.from(compra)).param("exp", java.sql.Timestamp.from(now.plus(day.multipliedBy(27)))).update()
        jdbc.sql(
            "INSERT INTO app_store_transactions (transaction_id, original_transaction_id, owner_user_id, product_id, purchase_date, price_millis, currency, environment) " +
                "VALUES ('tx-1', 'otx-1', :o, 'app.saqz.organizador.mensal', :at, 41900, 'BRL', 'PRODUCTION')",
        ).param("o", carla).param("at", java.sql.Timestamp.from(compra)).update()
        attribution.appStore(
            AppStoreTransaction("tx-1", "otx-1", "app.saqz.organizador.mensal", compra, now.plus(day), null, null,
                AppStoreEnvironment.PRODUCTION, 41_900, "BRL", compra, offerCodeIdentifier = "ARENA30OM"),
            carla,
        )

        val davi = user("Davi")
        jdbc.sql(
            "INSERT INTO google_play_subscriptions (purchase_token, owner_user_id, product_id, base_plan_id, plan, cycle, state, expires_at) " +
                "VALUES ('tok-1', :o, 'app.saqz.organizador', 'mensal', 'ORGANIZADOR', 'MONTHLY', 'CANCELED', :exp)",
        ).param("o", davi).param("exp", java.sql.Timestamp.from(now.minus(day))).update()
        attribution.googlePlay(
            GooglePlayPurchase("tok-1", "app.saqz.organizador", "mensal", GooglePlayState.ACTIVE, now.plus(day), true, null, null, null,
                "GPA.1", true, false, offerId = "cupom-arena30"),
            GooglePlayProduct.ORGANIZADOR_MENSAL, davi, compra.minusSeconds(60),
        )
        jdbc.sql(
            "INSERT INTO google_play_orders (order_id, purchase_token, owner_user_id, product_id, base_plan_id, recorded_at) " +
                "VALUES ('GPA.1', 'tok-1', :o, 'app.saqz.organizador', 'mensal', :at)",
        ).param("o", davi).param("at", java.sql.Timestamp.from(compra)).update()

        val report = partners.report(arena, null, null, now)!!
        assertEquals(2, report.metrics.payers)
        assertEquals(4_190, report.metrics.revenueCents)
        assertEquals(mapOf("APP_STORE" to 4_190L), report.metrics.revenueBySource)
        assertEquals(1, report.metrics.paymentsWithoutValue)
        val byName = report.payers.associateBy { it.name }
        assertEquals("APP_STORE", byName["Carla"]!!.source)
        assertEquals("ACTIVE", byName["Carla"]!!.status)
        assertEquals("GOOGLE_PLAY", byName["Davi"]!!.source)
        assertEquals(1, byName["Davi"]!!.paymentsWithoutValue)
        assertEquals("CANCELED", byName["Davi"]!!.status)
        assertEquals(1, report.metrics.activeSubscribers)

        // Período: só quem usou o cupom e pagou dentro dele.
        assertEquals(0, partners.report(arena, now.minus(day), null, now)!!.metrics.users)
    }

    @Test
    fun `nome repetido, encerrar e parceiro inexistente`() {
        val arena = partner("Arena")
        assertEquals(PartnerCreateResult.DuplicateName, partners.create(PartnerCreate("arena")))
        assertTrue(partners.exists(arena))
        assertTrue(partners.end(arena, now))
        assertFalse(partners.exists(arena))
        assertFalse(partners.list(now).single().partner.active)
        assertNull(partners.report(UUID.randomUUID(), null, null, now))
        assertIs<PartnerCreateResult.Created>(partners.create(PartnerCreate("Outra")))
    }
}
