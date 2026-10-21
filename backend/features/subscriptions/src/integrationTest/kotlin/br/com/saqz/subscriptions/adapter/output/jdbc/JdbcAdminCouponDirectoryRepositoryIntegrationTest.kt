package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcAdminCouponDirectoryRepositoryIntegrationTest {
    private val database = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations())
    private lateinit var repository: JdbcAdminCouponDirectoryRepository

    private val now: Instant = Instant.parse("2026-08-03T12:00:00Z")

    @BeforeAll
    fun startDatabase() {
        repository = JdbcAdminCouponDirectoryRepository(database.dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE subscription_events, coupon_store_offers, coupon_redemptions, subscriptions, coupons, access_users CASCADE")
    }

    @Test
    fun `cria lista com usos e assinaturas ativas e recusa codigo duplicado`() {
        val created = repository.create("GALERA10", 10, durationCycles = 6, validUntil = now.plusSeconds(86_400))
        assertIs<AdminCouponCreateResult.Created>(created)
        assertEquals(AdminCouponCreateResult.DuplicateCode, repository.create("GALERA10", 20, null, null))

        val couponId = created.coupon.id
        val comCupom = insertUser()
        val cupomEsgotado = insertUser()
        insertRedemption(couponId, comCupom)
        insertRedemption(couponId, cupomEsgotado)
        insertSubscription(comCupom, couponId, cyclesRemaining = 2)
        // Cancelada localmente (canceled_at sem o webhook) não conta como desconto ativo.
        val canceladaLocal = insertUser()
        insertRedemption(couponId, canceladaLocal)
        insertSubscription(canceladaLocal, couponId, cyclesRemaining = 2)
        execute("UPDATE subscriptions SET canceled_at = now() WHERE owner_user_id = '$canceladaLocal'")
        // Checkout nunca pago com cupom também não.
        val nuncaPagou = insertUser()
        insertRedemption(couponId, nuncaPagou)
        insertSubscription(nuncaPagou, couponId, cyclesRemaining = 2)
        execute("UPDATE subscriptions SET status = 'PAST_DUE', first_confirmed_at = NULL WHERE owner_user_id = '$nuncaPagou'")
        // Estado real pós-esgotamento: remaining NULL com cupom de duração finita.
        insertSubscription(cupomEsgotado, couponId, cyclesRemaining = null)

        val list = repository.list()

        assertEquals(1, list.size)
        assertEquals("GALERA10", list.single().code)
        assertEquals(4, list.single().redemptions)
        assertEquals(1, list.single().activeSubscriptions)
    }

    @Test
    fun `duplicidade e case-insensitive como o resto do fluxo 8`() {
        // O fluxo 8 busca cupom com lower(code) e espera no máximo uma linha.
        execute(
            "INSERT INTO coupons (id, code, discount_percent, created_at) " +
                "VALUES ('${UUID.randomUUID()}', 'promo', 5, now())",
        )

        assertEquals(AdminCouponCreateResult.DuplicateCode, repository.create("PROMO", 10, null, null))
    }

    @Test
    fun `desativar expira o cupom agora e preserva expiracao ja passada`() {
        val created = repository.create("SAQUE20", 20, null, validUntil = null) as AdminCouponCreateResult.Created

        assertTrue(repository.deactivate(created.coupon.id, now))
        assertEquals(now, repository.list().single().validUntil)

        // Já expirado antes: desativar de novo não empurra a validade para frente.
        assertTrue(repository.deactivate(created.coupon.id, now.plusSeconds(86_400)))
        assertEquals(now, repository.list().single().validUntil)

        assertFalse(repository.deactivate(UUID.randomUUID(), now))
    }

    @Test
    fun `campanha nas lojas - ofertas pendentes, encerrar, tentar de novo e usos por plataforma`() {
        val created = repository.create(
            AdminCouponCreate(
                code = "VOLEI30", campaign = "Vôlei", discountPercent = 30, durationCycles = 3, validUntil = null, maxUses = 10,
                eligibility = setOf(CouponEligibility.NEVER_SUBSCRIBED),
                products = setOf(CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY), CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.ANNUAL)),
                stores = true,
            ),
        ) as AdminCouponCreateResult.Created
        val id = created.coupon.id
        val offers = JdbcCouponStoreOfferRepository(database.dataSource)
        val coupons = JdbcCouponRepository(database.dataSource)
        assertEquals(4, offers.findByCoupon(id).size)
        val stored = coupons.findById(id)!!
        assertEquals(setOf(CouponEligibility.NEVER_SUBSCRIBED), stored.eligibility)
        assertEquals(2, stored.products.size)
        assertEquals("Vôlei", stored.campaign)
        assertTrue(stored.stores)

        // Uma falha e uma ativa; o pior estado é o da plataforma.
        val apple = offers.find(id, CouponStore.APP_STORE, "ORGANIZADOR", "MONTHLY")!!
        offers.save(apple.copy(state = CouponStoreOfferState.FAILED, message = "App Store: recusou", ascOfferCodeId = "oc-1"))
        val play = offers.find(id, CouponStore.GOOGLE_PLAY, "ORGANIZADOR", "MONTHLY")!!
        offers.save(play.copy(state = CouponStoreOfferState.ACTIVE, storeCreatedAt = now))
        val row = repository.list().single()
        assertEquals(CouponStoreOfferState.FAILED, row.platforms[CouponPlatform.IOS]?.state)
        assertEquals("App Store: recusou", row.platforms[CouponPlatform.IOS]?.message)
        assertEquals(CouponStoreOfferState.PENDING, row.platforms[CouponPlatform.ANDROID]?.state)
        assertEquals(1, repository.retryFailedOffers(id))
        assertEquals(CouponStoreOfferState.PENDING, offers.find(id, CouponStore.APP_STORE, "ORGANIZADOR", "MONTHLY")!!.state)

        val user = insertUser()
        coupons.saveRedemption(CouponRedemption(id, user, now, CouponPlatform.IOS))
        coupons.saveRedemption(CouponRedemption(id, user, now, CouponPlatform.WEB))
        assertEquals(1, coupons.countRedemptions(id))
        assertEquals(mapOf(CouponPlatform.IOS to 1L), repository.list().single().redemptionsByPlatform)
        assertEquals(id, offers.findByAppStoreOffer("app.saqz.organizador.mensal", "volei30om")?.couponId)
        assertEquals(id, offers.findByGooglePlayOffer("app.saqz.organizador", "mensal", "cupom-volei30")?.couponId)

        // Encerrar: o que chegou à loja vai a DEACTIVATING; o que nunca saiu do backend, DEACTIVATED.
        assertTrue(repository.deactivate(id, now))
        val after = offers.findByCoupon(id).associate { "${it.store}:${it.product.cycle}" to it.state }
        assertEquals(CouponStoreOfferState.DEACTIVATING, after["GOOGLE_PLAY:MONTHLY"])
        assertEquals(CouponStoreOfferState.DEACTIVATING, after["APP_STORE:MONTHLY"])
        assertEquals(CouponStoreOfferState.DEACTIVATED, after["GOOGLE_PLAY:ANNUAL"])
        assertEquals(CouponStoreOfferState.DEACTIVATED, after["APP_STORE:ANNUAL"])
        assertEquals(0, repository.retryFailedOffers(id))
    }

    @Test
    fun `job acha oferta ACTIVE de cupom vencido, e não a de cupom em vigor`() {
        val offers = JdbcCouponStoreOfferRepository(database.dataSource)
        fun campaign(code: String, until: Instant) = (
            repository.create(
                AdminCouponCreate(code, "C", 30, 3, until, products = setOf(CouponProduct(Plan.TITULAR, SubscriptionCycle.MONTHLY)), stores = true),
            ) as AdminCouponCreateResult.Created
            ).coupon.id
        val vigente = campaign("VIGENTE", now.plusSeconds(86_400))
        val vencido = campaign("VENCIDO", now.plusSeconds(3_600))
        (offers.findByCoupon(vigente) + offers.findByCoupon(vencido)).forEach { offers.save(it.copy(state = CouponStoreOfferState.ACTIVE)) }

        assertEquals(emptyList(), offers.findWork(now, 50))
        val work = offers.findWork(now.plusSeconds(7_200), 50)
        assertEquals(setOf(vencido), work.map { it.couponId }.toSet())
        assertEquals(2, work.size)
    }

    private fun insertUser(): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', 'sub-$id', true, 'Nome Valido', now(), now())",
        )
        return id
    }

    private fun insertRedemption(couponId: UUID, userId: UUID) {
        execute("INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES ('$couponId', '$userId', now())")
    }

    private fun insertSubscription(ownerId: UUID, couponId: UUID, cyclesRemaining: Int?) {
        execute(
            "INSERT INTO subscriptions (owner_user_id, plan, cycle, status, asaas_customer_id, " +
                "asaas_subscription_id, current_period_end, first_confirmed_at, coupon_id, coupon_cycles_remaining, " +
                "created_at, updated_at) " +
                "VALUES ('$ownerId', 'TITULAR', 'MONTHLY', 'ACTIVE', 'cus-$ownerId', 'sub-$ownerId', " +
                "'${now.plusSeconds(2_592_000)}', now(), '$couponId', ${cyclesRemaining ?: "NULL"}, now(), now())",
        )
    }

    private fun execute(sql: String) {
        DriverManager.getConnection(database.jdbcUrl, database.username, database.password).use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }
}
