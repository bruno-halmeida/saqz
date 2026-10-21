package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncCouponStoreOffersTest {
    private var now = Instant.parse("2026-10-20T12:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val coupon = Coupon(
        UUID.randomUUID(), "VOLEI30", 30, durationCycles = 3, campaign = "Vôlei", stores = true, maxUses = 50,
        eligibility = setOf(CouponEligibility.FORMER_SUBSCRIBER), products = setOf(CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY)),
        validUntil = Instant.parse("2026-11-30T23:59:59Z"),
    )

    private class FakePlay : GooglePlayOffersApi {
        val states = mutableMapOf<String, GooglePlayOfferState>()
        val created = mutableListOf<GooglePlayOfferSpec>()
        val calls = mutableListOf<String>()
        var failWith: CouponStoreException? = null
        override fun get(productId: String, basePlanId: String, offerId: String) = states[offerId].also { calls += "get" }
        override fun create(spec: GooglePlayOfferSpec): GooglePlayOfferState {
            failWith?.let { throw it }
            created += spec; states[spec.offerId] = GooglePlayOfferState.DRAFT; calls += "create"
            return GooglePlayOfferState.DRAFT
        }
        override fun activate(productId: String, basePlanId: String, offerId: String) =
            GooglePlayOfferState.ACTIVE.also { states[offerId] = it; calls += "activate" }
        override fun deactivate(productId: String, basePlanId: String, offerId: String) =
            GooglePlayOfferState.INACTIVE.also { states[offerId] = it; calls += "deactivate" }
    }

    private class FakeApple : AppStoreOffersApi {
        val calls = mutableListOf<String>()
        var spec: AppStoreOfferCodeSpec? = null
        var customCode: Triple<String, Int, LocalDate?>? = null
        override fun subscriptionId(productId: String) = "sub-$productId".also { calls += "subscription" }
        override fun brazilPricePoints(subscriptionId: String) = listOf(AppStorePricePoint("pp-41", 4_190), AppStorePricePoint("pp-44", 4_490))
        override fun createOfferCode(spec: AppStoreOfferCodeSpec) = "oc-1".also { this.spec = spec; calls += "offerCode" }
        override fun createCustomCode(offerCodeId: String, customCode: String, numberOfCodes: Int, expirationDate: LocalDate?) =
            "cc-1".also { this.customCode = Triple(customCode, numberOfCodes, expirationDate); calls += "customCode" }
        override fun deactivateOfferCode(offerCodeId: String) { calls += "deactivateOfferCode" }
        override fun deactivateCustomCode(customCodeId: String) { calls += "deactivateCustomCode" }
    }

    private fun setup(enabled: Boolean = true, c: Coupon = coupon): Triple<SyncCouponStoreOffers, InMemoryOffers, Pair<FakePlay, FakeApple>> {
        val offers = InMemoryOffers(CouponCampaignRules.offersFor(c))
        val play = FakePlay()
        val apple = FakeApple()
        return Triple(SyncCouponStoreOffers(offers, InMemoryCoupons(c), play, apple, enabled, clock), offers, play to apple)
    }

    private fun InMemoryOffers.of(store: CouponStore) = offers.values.single { it.store == store }

    @Test
    fun `desligado - nenhuma chamada e a oferta fica pendente com o aviso`() {
        val (sync, offers, apis) = setup(enabled = false)
        sync.run()
        assertEquals(emptyList(), apis.first.calls)
        assertEquals(emptyList(), apis.second.calls)
        assertTrue(offers.offers.values.all { it.state == CouponStoreOfferState.PENDING && it.message == SyncCouponStoreOffers.DISABLED_MESSAGE })
    }

    @Test
    fun `play cria a oferta com desconto relativo e ativa`() {
        val (sync, offers, apis) = setup()
        sync.run()
        val spec = apis.first.created.single()
        assertEquals("cupom-volei30", spec.offerId)
        assertEquals(3, spec.cycles)
        assertEquals("P1M", spec.period)
        assertEquals(0.3, spec.relativeDiscount)
        assertEquals(listOf("cupom", "volei"), spec.tags)
        assertEquals(CouponStoreOfferState.ACTIVE, offers.of(CouponStore.GOOGLE_PLAY).state)
    }

    @Test
    fun `apple cria código com price point mais próximo, validade e fica 1 hora liberando`() {
        val (sync, offers, apis) = setup()
        sync.run()
        val apple = apis.second
        assertEquals(listOf("EXPIRED"), apple.spec?.customerEligibilities)
        assertEquals("pp-41", apple.spec?.pricePointId)
        assertEquals("ONE_MONTH", apple.spec?.duration)
        assertEquals("VOLEI30OM", apple.spec?.name)
        // 23h59 UTC de 30/11 = 15h59 no Pacífico do mesmo dia: o código desliga à 0h PT de 30/11.
        assertEquals(Triple("VOLEI30OM", 50, LocalDate.parse("2026-11-30")), apple.customCode)
        val waiting = offers.of(CouponStore.APP_STORE)
        assertEquals(CouponStoreOfferState.WAITING_STORE, waiting.state)
        assertEquals(4_190, waiting.storePriceCents)

        now = now.plus(Duration.ofMinutes(30)); sync.run()
        assertEquals(CouponStoreOfferState.WAITING_STORE, offers.of(CouponStore.APP_STORE).state)
        now = now.plus(Duration.ofMinutes(31)); sync.run()
        assertEquals(CouponStoreOfferState.ACTIVE, offers.of(CouponStore.APP_STORE).state)
    }

    @Test
    fun `cupom sem validade não manda expirationDate`() {
        val (sync, _, apis) = setup(c = coupon.copy(validUntil = null))
        sync.run()
        assertEquals(null, apis.second.customCode?.third)
    }

    @Test
    fun `recusa da loja vai a FAILED com o motivo, falha passageira tenta de novo`() {
        val (sync, offers, apis) = setup()
        apis.first.failWith = CouponStoreException("Google Play: offers.create respondeu 400: offerId inválido", permanent = true)
        sync.run()
        val failed = offers.of(CouponStore.GOOGLE_PLAY)
        assertEquals(CouponStoreOfferState.FAILED, failed.state)
        assertEquals("Google Play: offers.create respondeu 400: offerId inválido", failed.message)

        val (sync2, offers2, apis2) = setup()
        apis2.first.failWith = CouponStoreException("Google Play Developer API fora do ar", permanent = false)
        sync2.run()
        val retry = offers2.of(CouponStore.GOOGLE_PLAY)
        assertEquals(CouponStoreOfferState.CREATING, retry.state)
        assertEquals(1, retry.attempts)
        assertTrue(retry.nextAttemptAt!!.isAfter(now))
    }

    @Test
    fun `oferta ACTIVE de cupom vencido é desativada nas duas lojas`() {
        val (sync, offers, apis) = setup()
        sync.run()
        now = now.plus(Duration.ofMinutes(61)); sync.run()
        assertTrue(offers.offers.values.all { it.state == CouponStoreOfferState.ACTIVE })

        now = Instant.parse("2026-12-01T00:00:00Z"); sync.run()
        assertTrue(offers.offers.values.all { it.state == CouponStoreOfferState.DEACTIVATED })
        assertTrue("deactivate" in apis.first.calls)
        assertTrue(apis.second.calls.containsAll(listOf("deactivateCustomCode", "deactivateOfferCode")))
    }
}
