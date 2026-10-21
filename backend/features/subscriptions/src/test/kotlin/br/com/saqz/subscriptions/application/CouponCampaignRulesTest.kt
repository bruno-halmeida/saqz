package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CouponCampaignRulesTest {
    private val monthly = listOf(CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.MONTHLY))
    private val annual = listOf(CouponProduct(Plan.ORGANIZADOR, SubscriptionCycle.ANNUAL))

    @Test
    fun `limites das lojas recusados com motivo`() {
        assertNotNull(CouponCampaignRules.storeRejection(30, null, monthly, null))
        assertNotNull(CouponCampaignRules.storeRejection(100, 3, monthly, null))
        assertNull(CouponCampaignRules.storeRejection(30, 12, monthly, null))
        assertNotNull(CouponCampaignRules.storeRejection(30, 13, monthly, null))
        assertNull(CouponCampaignRules.storeRejection(30, 1, annual, null))
        assertNotNull(CouponCampaignRules.storeRejection(30, 2, annual, null))
        assertNotNull(CouponCampaignRules.storeRejection(30, 3, monthly, 25_001))
    }

    @Test
    fun `ids nas lojas e preço`() {
        assertEquals("cupom-volei30", CouponCampaignRules.googlePlayOfferId("VOLEI30"))
        assertEquals("VOLEI30OM", CouponCampaignRules.appStoreCustomCode("volei30", monthly.single()))
        assertEquals("VOLEI30IA", CouponCampaignRules.appStoreCustomCode("VOLEI30", CouponProduct(Plan.ILIMITADO, SubscriptionCycle.ANNUAL)))
        assertEquals("volei-de-praia-moema", CouponCampaignRules.offerTag("Vôlei de Praia — Moema!"))
        assertEquals("c-2026", CouponCampaignRules.offerTag("2026"))
        assertEquals(4_193, CouponCampaignRules.googlePlayPriceCents(monthly.single(), 30))
        // Anual da loja (R$ 539,90), não o do site (R$ 539,10).
        assertEquals(37_793, CouponCampaignRules.googlePlayPriceCents(annual.single(), 30))
        val points = listOf(AppStorePricePoint("a", 3_990), AppStorePricePoint("b", 4_190), AppStorePricePoint("c", 4_490))
        assertEquals("b", CouponCampaignRules.nearestPricePoint(points, 4_193)?.id)
    }

    @Test
    fun `cupom das lojas gera uma oferta por loja e produto`() {
        val coupon = Coupon(UUID.randomUUID(), "VOLEI30", 30, durationCycles = 3, stores = true)
        val offers = CouponCampaignRules.offersFor(coupon)
        assertEquals(12, offers.size)
        assertEquals(6, offers.count { it.store == CouponStore.APP_STORE })
        // Apple: um código personalizado por produto (único no app); Play: o mesmo offerId em cada plano base.
        assertEquals(6, offers.mapNotNull { it.customCode }.toSet().size)
        assertEquals(setOf("cupom-volei30"), offers.mapNotNull { it.offerId }.toSet())
        assertEquals(emptyList(), CouponCampaignRules.offersFor(coupon.copy(stores = false)))
    }
}
