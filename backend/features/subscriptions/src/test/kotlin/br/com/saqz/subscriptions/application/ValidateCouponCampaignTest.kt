package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponRedemption
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateCouponCampaignTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val user = UUID.randomUUID()
    private val campaign = Coupon(
        id = UUID.randomUUID(), code = "VOLEI30", discountPercent = 30, durationCycles = 3, campaign = "Vôlei de praia",
        eligibility = setOf(CouponEligibility.NEVER_SUBSCRIBED), products = setOf(CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY)),
        stores = true, maxUses = 2,
    )

    private fun setup(coupon: Coupon = campaign, paidBefore: Boolean = false, paidNow: Boolean = false, active: Boolean = true): Pair<ValidateCoupon, InMemoryCoupons> {
        val coupons = InMemoryCoupons(coupon)
        val offers = InMemoryOffers(CouponCampaignRules.offersFor(coupon).map { if (active) it.copy(state = CouponStoreOfferState.ACTIVE, storePriceCents = it.storePriceCents ?: 4_190) else it })
        return ValidateCoupon(coupons, clock, offers, FixedEligibility(paidBefore, paidNow)) to coupons
    }

    @Test
    fun `android recebe a oferta do Play e o preço na loja`() {
        val (validate, _) = setup()
        val applied = assertIs<ValidateCouponResult.Applied>(validate.execute("volei30", "ORGANIZADOR", "MONTHLY", CouponPlatform.ANDROID, user))
        assertEquals("cupom-volei30", applied.googlePlayOfferId)
        assertEquals(4_193, applied.storePriceCents)
        assertEquals("Vôlei de praia", applied.campaign)
        assertEquals(3, applied.durationCycles)
        assertEquals(null, applied.appStoreOfferCode)
    }

    @Test
    fun `iphone recebe o código personalizado do produto`() {
        val (validate, _) = setup()
        val applied = assertIs<ValidateCouponResult.Applied>(validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.IOS, user))
        assertEquals("VOLEI30OM", applied.appStoreOfferCode)
        assertEquals(4_190, applied.storePriceCents)
    }

    @Test
    fun `oferta ainda não ativa na loja é PENDING_ON_PLATFORM`() {
        val (validate, _) = setup(active = false)
        assertEquals(ValidateCouponResult.PendingOnPlatform, validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.IOS, user))
        // O site não depende das lojas.
        assertIs<ValidateCouponResult.Applied>(validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
    }

    @Test
    fun `cupom só do site ou produto fora do cupom não vale na loja`() {
        val (siteOnly, _) = setup(campaign.copy(durationCycles = null, stores = false))
        assertEquals(ValidateCouponResult.NotAvailableOnPlatform, siteOnly.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.ANDROID, user))
        val (validate, _) = setup()
        assertEquals(ValidateCouponResult.NotAvailableOnPlatform, validate.execute("VOLEI30", "TITULAR", "MONTHLY", CouponPlatform.ANDROID, user))
        assertEquals(ValidateCouponResult.PlanNotCovered, validate.execute("VOLEI30", "TITULAR", "MONTHLY", CouponPlatform.WEB, user))
        assertEquals(ValidateCouponResult.CycleNotCovered, validate.execute("VOLEI30", "ORGANIZADOR", "ANNUAL", CouponPlatform.WEB, user))
    }

    @Test
    fun `quem pode usar - nunca assinou e já cancelou`() {
        assertEquals(ValidateCouponResult.NotEligible, setup(paidBefore = true).first.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
        val former = campaign.copy(eligibility = setOf(CouponEligibility.FORMER_SUBSCRIBER))
        assertIs<ValidateCouponResult.Applied>(setup(former, paidBefore = true).first.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
        assertEquals(ValidateCouponResult.NotEligible, setup(former, paidBefore = true, paidNow = true).first.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
        assertEquals(ValidateCouponResult.NotEligible, setup(former).first.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
    }

    @Test
    fun `limite de usos em todas as plataformas e quem já usou`() {
        val (validate, coupons) = setup()
        coupons.saveRedemption(CouponRedemption(campaign.id, user, now, CouponPlatform.IOS))
        assertEquals(ValidateCouponResult.NotEligible, validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
        coupons.saveRedemption(CouponRedemption(campaign.id, UUID.randomUUID(), now, CouponPlatform.ANDROID))
        assertEquals(ValidateCouponResult.Exhausted, validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, UUID.randomUUID()))
    }

    @Test
    fun `encerrado conta como expirado`() {
        val (validate, _) = setup(campaign.copy(endedAt = now.minusSeconds(1)))
        assertIs<ValidateCouponResult.Expired>(validate.execute("VOLEI30", "ORGANIZADOR", "MONTHLY", CouponPlatform.WEB, user))
    }

    @Test
    fun `atribuição - compra pela oferta do Play e pelo código da Apple vira resgate com a plataforma`() {
        val coupons = InMemoryCoupons(campaign)
        val offers = InMemoryOffers(CouponCampaignRules.offersFor(campaign))
        val attribution = CouponStoreAttribution(offers, coupons)
        val apple = br.com.saqz.subscriptions.domain.AppStoreTransaction(
            transactionId = "t1", originalTransactionId = "o1", productId = "app.saqz.organizador.mensal",
            purchaseDate = now, expiresDate = now.plusSeconds(86_400), revocationDate = null, appAccountToken = null,
            environment = br.com.saqz.subscriptions.domain.AppStoreEnvironment.SANDBOX, priceMillis = 41_900, currency = "BRL",
            signedAt = now, offerCodeIdentifier = "VOLEI30OM",
        )
        attribution.appStore(apple, user)
        attribution.appStore(apple.copy(offerCodeIdentifier = null), UUID.randomUUID())
        assertEquals(listOf(CouponPlatform.IOS), coupons.redemptions.map { it.platform })

        val other = UUID.randomUUID()
        val purchase = br.com.saqz.subscriptions.domain.GooglePlayPurchase(
            purchaseToken = "tok", productId = "app.saqz.organizador", basePlanId = "mensal",
            state = br.com.saqz.subscriptions.domain.GooglePlayState.ACTIVE, expiresAt = now.plusSeconds(86_400), autoRenew = true,
            canceledAt = null, obfuscatedAccountId = null, linkedPurchaseToken = null, latestOrderId = "GPA.1",
            acknowledged = true, testPurchase = false, offerId = "cupom-volei30",
        )
        attribution.googlePlay(purchase, br.com.saqz.subscriptions.domain.GooglePlayProduct.ORGANIZADOR_MENSAL, other, now)
        attribution.googlePlay(purchase, br.com.saqz.subscriptions.domain.GooglePlayProduct.ORGANIZADOR_MENSAL, other, now)
        assertEquals(listOf(CouponPlatform.IOS, CouponPlatform.ANDROID), coupons.redemptions.map { it.platform })
        assertEquals(CouponStore.GOOGLE_PLAY, offers.findByGooglePlayOffer("app.saqz.organizador", "mensal", "cupom-volei30")?.store)
    }
}
