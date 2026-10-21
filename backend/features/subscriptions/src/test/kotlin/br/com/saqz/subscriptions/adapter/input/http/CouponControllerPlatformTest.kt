package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.CouponCampaignRules
import br.com.saqz.subscriptions.application.InMemoryCoupons
import br.com.saqz.subscriptions.application.InMemoryOffers
import br.com.saqz.subscriptions.application.ValidateCoupon
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CouponControllerPlatformTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC)
    private val campaign = Coupon(UUID.randomUUID(), "VOLEI30", 30, durationCycles = 3, campaign = "Vôlei", stores = true)
    private val siteOnly = Coupon(UUID.randomUUID(), "SITE10", 10)
    private val controller = CouponController(
        ValidateCoupon(
            InMemoryCoupons(campaign, siteOnly),
            clock,
            InMemoryOffers(CouponCampaignRules.offersFor(campaign).map { it.copy(state = CouponStoreOfferState.ACTIVE, storePriceCents = 4_190) }),
        ),
        appAppleId = "6740000000",
    )

    @Test
    fun `iphone recebe código e link de resgate da App Store`() {
        val response = controller.validate(null, ValidateCouponRequest("VOLEI30", "ORGANIZADOR", "MONTHLY", "IOS"))
        assertEquals("APPLIED", response.status)
        assertEquals("VOLEI30OM", response.appStoreOfferCode)
        assertEquals("https://apps.apple.com/redeem?ctx=offercodes&id=6740000000&code=VOLEI30OM", response.appStoreRedeemUrl)
        assertEquals(4_190, response.storePriceCents)
        assertEquals("Vôlei", response.campaign)
        assertNull(response.googlePlayOfferId)
    }

    @Test
    fun `android recebe a oferta e site sem platform segue igual`() {
        assertEquals("cupom-volei30", controller.validate(null, ValidateCouponRequest("VOLEI30", "TITULAR", "ANNUAL", "android")).googlePlayOfferId)
        val web = controller.validate(null, ValidateCouponRequest("VOLEI30", "TITULAR"))
        assertEquals("APPLIED", web.status)
        assertNull(web.storePriceCents)
    }

    @Test
    fun `cupom só do site na loja e plataforma inválida`() {
        assertEquals("NOT_AVAILABLE_ON_PLATFORM", controller.validate(null, ValidateCouponRequest("SITE10", "TITULAR", null, "ANDROID")).status)
        assertThrows<InvalidCouponRequestException> { controller.validate(null, ValidateCouponRequest("SITE10", "TITULAR", null, "WINDOWS")) }
    }
}
