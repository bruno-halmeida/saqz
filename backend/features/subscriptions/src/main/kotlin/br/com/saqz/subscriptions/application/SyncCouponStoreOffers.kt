package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Leva as ofertas dos cupons às lojas, um passo por vez, gravando cada id externo assim que a loja
 * devolve (uma queda no meio retoma do passo seguinte). Com [enabled] desligado (dev, testes,
 * local) nada sai daqui: dev e produção usam as mesmas lojas.
 */
class SyncCouponStoreOffers(
    private val offers: CouponStoreOfferRepository,
    private val coupons: CouponRepository,
    private val googlePlay: GooglePlayOffersApi?,
    private val appStore: AppStoreOffersApi?,
    private val enabled: Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(SyncCouponStoreOffers::class.java)

    /** @return quantas ofertas mudaram de estado ou avançaram um passo. */
    fun run(): Int {
        val now = clock.instant()
        var touched = 0
        for (offer in offers.findWork(now, BATCH)) {
            val coupon = coupons.findById(offer.couponId) ?: continue
            val next = if (!enabled) disabled(offer) else step(offer, coupon, now)
            if (next != offer) {
                offers.save(next)
                touched++
            }
        }
        return touched
    }

    private fun disabled(offer: CouponStoreOffer): CouponStoreOffer = when (offer.state) {
        CouponStoreOfferState.PENDING, CouponStoreOfferState.CREATING -> offer.copy(message = DISABLED_MESSAGE)
        else -> offer
    }

    private fun step(offer: CouponStoreOffer, coupon: Coupon, now: Instant): CouponStoreOffer {
        // Cupom vencido sem ninguém clicar "Encerrar": as lojas também param de aceitar.
        val closed = CouponCampaignRules.isClosed(coupon, now)
        val current = when {
            closed && offer.state in CREATABLE -> offer.copy(state = CouponStoreOfferState.DEACTIVATING)
            offer.state == CouponStoreOfferState.PENDING -> offer.copy(state = CouponStoreOfferState.CREATING)
            else -> offer
        }
        return try {
            when (current.state) {
                CouponStoreOfferState.CREATING -> create(current, coupon, now)
                CouponStoreOfferState.WAITING_STORE -> waiting(current, now)
                CouponStoreOfferState.DEACTIVATING -> deactivate(current, now)
                else -> current
            }
        } catch (failure: CouponStoreException) {
            failed(current, failure, now)
        }
    }

    private fun create(offer: CouponStoreOffer, coupon: Coupon, now: Instant): CouponStoreOffer = when (offer.store) {
        CouponStore.GOOGLE_PLAY -> createOnGooglePlay(offer, coupon, now)
        CouponStore.APP_STORE -> createOnAppStore(offer, coupon, now)
    }

    private fun createOnGooglePlay(offer: CouponStoreOffer, coupon: Coupon, now: Instant): CouponStoreOffer {
        val api = googlePlay ?: throw CouponStoreException("Google Play não configurado neste ambiente", permanent = false)
        val productId = offer.productId
        val basePlanId = requireNotNull(offer.basePlanId)
        val offerId = requireNotNull(offer.offerId)
        val existing = api.get(productId, basePlanId, offerId) ?: api.create(
            GooglePlayOfferSpec(
                productId = productId,
                basePlanId = basePlanId,
                offerId = offerId,
                cycles = requireNotNull(coupon.durationCycles),
                period = if (offer.product.cycle == SubscriptionCycle.MONTHLY) "P1M" else "P1Y",
                relativeDiscount = coupon.discountPercent / 100.0,
                tags = listOfNotNull("cupom", coupon.campaign?.let(CouponCampaignRules::offerTag)).distinct(),
            ),
        )
        val created = offer.copy(storeCreatedAt = offer.storeCreatedAt ?: now)
        val state = if (existing == GooglePlayOfferState.ACTIVE) existing else api.activate(productId, basePlanId, offerId)
        return if (state == GooglePlayOfferState.ACTIVE) active(created, now) else waitingStore(created)
    }

    private fun createOnAppStore(offer: CouponStoreOffer, coupon: Coupon, now: Instant): CouponStoreOffer {
        val api = appStore ?: throw CouponStoreException("App Store Connect não configurada neste ambiente", permanent = false)
        var current = offer
        if (current.ascSubscriptionId == null) {
            current = current.copy(ascSubscriptionId = api.subscriptionId(current.productId))
            offers.save(current)
        }
        val subscriptionId = requireNotNull(current.ascSubscriptionId)
        if (current.ascOfferCodeId == null) {
            val target = CouponCampaignRules.appStoreTargetCents(current.product, coupon.discountPercent)
            val point = CouponCampaignRules.nearestPricePoint(api.brazilPricePoints(subscriptionId), target)
                ?: throw CouponStoreException("App Store: sem price point no Brasil para ${current.productId}", permanent = true)
            val codeId = api.createOfferCode(
                AppStoreOfferCodeSpec(
                    subscriptionId = subscriptionId,
                    name = requireNotNull(current.customCode),
                    customerEligibilities = appleEligibilities(coupon),
                    duration = if (current.product.cycle == SubscriptionCycle.MONTHLY) "ONE_MONTH" else "ONE_YEAR",
                    numberOfPeriods = requireNotNull(coupon.durationCycles),
                    pricePointId = point.id,
                ),
            )
            current = current.copy(ascOfferCodeId = codeId, storePriceCents = point.customerPriceCents)
            offers.save(current)
        }
        if (current.ascCustomCodeId == null) {
            val uses = (coupon.maxUses ?: CouponCampaignRules.APPLE_MAX_CUSTOM_CODE_USES)
                .coerceAtMost(CouponCampaignRules.APPLE_MAX_CUSTOM_CODE_USES)
            current = current.copy(
                ascCustomCodeId = api.createCustomCode(
                    requireNotNull(current.ascOfferCodeId),
                    requireNotNull(current.customCode),
                    uses,
                    coupon.validUntil?.let(CouponCampaignRules::appStoreExpirationDate),
                ),
                storeCreatedAt = now,
            )
        }
        // A Apple não avisa quando o código passa a valer (até 1 hora).
        return waitingStore(current)
    }

    private fun waiting(offer: CouponStoreOffer, now: Instant): CouponStoreOffer = when (offer.store) {
        CouponStore.APP_STORE -> {
            val since = offer.storeCreatedAt ?: now
            if (!now.isBefore(since.plus(APPLE_PROPAGATION))) active(offer, now) else offer.copy(nextAttemptAt = since.plus(APPLE_PROPAGATION))
        }
        CouponStore.GOOGLE_PLAY -> {
            val api = googlePlay ?: throw CouponStoreException("Google Play não configurado neste ambiente", permanent = false)
            val state = api.get(offer.productId, requireNotNull(offer.basePlanId), requireNotNull(offer.offerId))
            when (state) {
                GooglePlayOfferState.ACTIVE -> active(offer, now)
                GooglePlayOfferState.DRAFT -> {
                    val activated = api.activate(offer.productId, requireNotNull(offer.basePlanId), requireNotNull(offer.offerId))
                    if (activated == GooglePlayOfferState.ACTIVE) active(offer, now) else offer.copy(nextAttemptAt = now.plus(RETRY_BASE))
                }
                null -> offer.copy(state = CouponStoreOfferState.PENDING, message = null)
                else -> offer.copy(nextAttemptAt = now.plus(RETRY_BASE))
            }
        }
    }

    private fun deactivate(offer: CouponStoreOffer, now: Instant): CouponStoreOffer {
        when (offer.store) {
            CouponStore.GOOGLE_PLAY -> {
                val api = googlePlay ?: throw CouponStoreException("Google Play não configurado neste ambiente", permanent = false)
                val productId = offer.productId
                val basePlanId = requireNotNull(offer.basePlanId)
                val offerId = requireNotNull(offer.offerId)
                // Rascunho não chega a ninguém; só a ativa precisa ser desativada.
                if (api.get(productId, basePlanId, offerId) == GooglePlayOfferState.ACTIVE) api.deactivate(productId, basePlanId, offerId)
            }
            CouponStore.APP_STORE -> {
                val api = appStore ?: throw CouponStoreException("App Store Connect não configurada neste ambiente", permanent = false)
                offer.ascCustomCodeId?.let(api::deactivateCustomCode)
                offer.ascOfferCodeId?.let(api::deactivateOfferCode)
            }
        }
        return offer.copy(state = CouponStoreOfferState.DEACTIVATED, message = null, nextAttemptAt = null, deactivatedAt = now)
    }

    private fun failed(offer: CouponStoreOffer, failure: CouponStoreException, now: Instant): CouponStoreOffer {
        val attempts = offer.attempts + 1
        log.warn("coupon_store_offer_failed offer={} store={} attempt={} permanent={} reason={}", offer.id, offer.store, attempts, failure.permanent, failure.message)
        // Falha ao desativar não vira FAILED: o cupom já não vale no backend, o job insiste devagar.
        if (offer.state == CouponStoreOfferState.DEACTIVATING) {
            return offer.copy(attempts = attempts, message = failure.message, nextAttemptAt = now.plus(backoff(attempts)))
        }
        if (failure.permanent || attempts >= MAX_ATTEMPTS) {
            return offer.copy(state = CouponStoreOfferState.FAILED, attempts = attempts, message = failure.message, nextAttemptAt = null)
        }
        return offer.copy(attempts = attempts, message = failure.message, nextAttemptAt = now.plus(backoff(attempts)))
    }

    private fun active(offer: CouponStoreOffer, now: Instant) =
        offer.copy(state = CouponStoreOfferState.ACTIVE, message = null, nextAttemptAt = null, activatedAt = offer.activatedAt ?: now)

    private fun waitingStore(offer: CouponStoreOffer) =
        offer.copy(state = CouponStoreOfferState.WAITING_STORE, message = null, attempts = 0)

    private fun backoff(attempts: Int): Duration = RETRY_BASE.multipliedBy(1L shl (attempts - 1).coerceIn(0, 6))

    private fun appleEligibilities(coupon: Coupon): List<String> {
        if (coupon.eligibility.isEmpty()) return listOf("NEW", "EXPIRED")
        return buildList {
            if (CouponEligibility.NEVER_SUBSCRIBED in coupon.eligibility) add("NEW")
            if (CouponEligibility.FORMER_SUBSCRIBER in coupon.eligibility) add("EXPIRED")
        }
    }

    companion object {
        const val DISABLED_MESSAGE = "Integração com as lojas desligada neste ambiente"
        const val MAX_ATTEMPTS = 5
        val APPLE_PROPAGATION: Duration = Duration.ofMinutes(60)
        val RETRY_BASE: Duration = Duration.ofMinutes(2)
        private const val BATCH = 50
        /** Estados que passam a DEACTIVATING quando o cupom vence ou é encerrado. */
        private val CREATABLE = setOf(
            CouponStoreOfferState.PENDING, CouponStoreOfferState.CREATING, CouponStoreOfferState.WAITING_STORE, CouponStoreOfferState.ACTIVE,
        )
    }
}
