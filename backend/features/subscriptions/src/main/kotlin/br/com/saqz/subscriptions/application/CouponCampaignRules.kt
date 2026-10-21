package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.AppStoreProduct
import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponStore
import br.com.saqz.subscriptions.domain.CouponStoreOffer
import br.com.saqz.subscriptions.domain.GooglePlayProduct
import br.com.saqz.subscriptions.domain.Plan
import br.com.saqz.subscriptions.domain.StoreListPrices
import br.com.saqz.subscriptions.domain.SubscriptionCycle
import java.time.Instant
import java.util.UUID

/**
 * Regras do cupom de campanha que valem igual no site, nos apps e no admin. Os limites das lojas
 * estão em docs/subscriptions/app-store.md e google-play.md (seção Cupons).
 */
object CouponCampaignRules {
    /** Apple "pay as you go": assinatura de 1 mês aceita de 1 a 12 meses; de 1 ano, só 1 ano. */
    const val MAX_MONTHLY_STORE_CYCLES = 12
    const val MAX_ANNUAL_STORE_CYCLES = 1

    /** Limite de resgates por código personalizado da Apple (`numberOfCodes`). */
    const val APPLE_MAX_CUSTOM_CODE_USES = 25_000

    /** Código do cupom: letras e números, até 32 (cabe o sufixo do produto nos 64 da Apple). */
    val CODE = Regex("^[A-Z0-9]{3,32}$")

    /**
     * Motivo em português quando o cupom não pode ir às lojas como está; null quando pode. As
     * mensagens aparecem no admin.
     */
    fun storeRejection(discountPercent: Int, durationCycles: Int?, products: Collection<CouponProduct>, maxUses: Int?): String? {
        if (durationCycles == null) return "Desconto para sempre vale só no site: escolha a duração em meses para ir às lojas."
        if (discountPercent >= 100) return "As lojas não aceitam 100% de desconto por cupom: use até 99%."
        if (products.any { it.cycle == SubscriptionCycle.MONTHLY } && durationCycles > MAX_MONTHLY_STORE_CYCLES) {
            return "Nas lojas o desconto do plano mensal dura no máximo $MAX_MONTHLY_STORE_CYCLES meses."
        }
        if (products.any { it.cycle == SubscriptionCycle.ANNUAL } && durationCycles > MAX_ANNUAL_STORE_CYCLES) {
            return "Na App Store o desconto do plano anual vale só para o primeiro ano: tire o anual ou use 1 ciclo."
        }
        if (maxUses != null && maxUses > APPLE_MAX_CUSTOM_CODE_USES) {
            return "A Apple aceita no máximo $APPLE_MAX_CUSTOM_CODE_USES usos por código."
        }
        return null
    }

    fun googlePlayOfferId(code: String): String = "cupom-${code.lowercase()}"

    /** Código personalizado da Apple: único no app inteiro, por isso um por produto (VOLEI30 + OM). */
    fun appStoreCustomCode(code: String, product: CouponProduct): String =
        code.uppercase() + product.plan.name.first() + (if (product.cycle == SubscriptionCycle.MONTHLY) "M" else "A")

    /** Tag de oferta do Play: minúsculas, números e hífen, até 20. */
    fun offerTag(raw: String): String =
        raw.lowercase().let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFD) }
            .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z0-9]+"), "-").trim('-').take(20).trim('-')
            .ifEmpty { "campanha" }

    /** Google aplica o desconto relativo sobre o preço do plano base e arredonda para o centavo. */
    fun googlePlayPriceCents(product: CouponProduct, discountPercent: Int): Long =
        Math.round(StoreListPrices.cents(product) * (100 - discountPercent) / 100.0)

    /** Preço que a Apple deveria cobrar; o price point escolhido é o de BRA mais próximo. */
    fun appStoreTargetCents(product: CouponProduct, discountPercent: Int): Long = googlePlayPriceCents(product, discountPercent)

    fun nearestPricePoint(points: List<AppStorePricePoint>, targetCents: Long): AppStorePricePoint? =
        points.minWithOrNull(compareBy<AppStorePricePoint> { kotlin.math.abs(it.customerPriceCents - targetCents) }.thenBy { it.customerPriceCents })

    /** Ofertas novas de um cupom que vai às lojas: uma por loja × produto. */
    fun offersFor(coupon: Coupon, newId: () -> UUID = UUID::randomUUID): List<CouponStoreOffer> {
        if (!coupon.stores) return emptyList()
        return coupon.coveredProducts().flatMap { product ->
            val play = GooglePlayProduct.of(product.plan, product.cycle)
            val apple = AppStoreProduct.of(product.plan, product.cycle)
            listOf(
                CouponStoreOffer(
                    id = newId(), couponId = coupon.id, store = CouponStore.GOOGLE_PLAY, product = product,
                    productId = play.productId, basePlanId = play.basePlanId, offerId = googlePlayOfferId(coupon.code),
                    storePriceCents = googlePlayPriceCents(product, coupon.discountPercent),
                ),
                CouponStoreOffer(
                    id = newId(), couponId = coupon.id, store = CouponStore.APP_STORE, product = product,
                    productId = apple.productId, customCode = appStoreCustomCode(coupon.code, product),
                ),
            )
        }
    }

    sealed interface Eligibility {
        data object Ok : Eligibility
        data object NotEligible : Eligibility
    }

    fun eligibility(coupon: Coupon, userId: UUID?, lookup: CouponEligibilityLookup?): Eligibility {
        if (coupon.eligibility.isEmpty() || userId == null || lookup == null) return Eligibility.Ok
        val paidBefore = lookup.hasPaidHistory(userId)
        val ok = (CouponEligibility.NEVER_SUBSCRIBED in coupon.eligibility && !paidBefore) ||
            (CouponEligibility.FORMER_SUBSCRIBER in coupon.eligibility && paidBefore && !lookup.hasPaidAccessNow(userId))
        return if (ok) Eligibility.Ok else Eligibility.NotEligible
    }

    fun isClosed(coupon: Coupon, now: Instant): Boolean =
        coupon.endedAt?.let { !it.isAfter(now) } == true || coupon.validUntil?.let { !it.isAfter(now) } == true

    fun planOf(raw: String): Plan? = runCatching { Plan.valueOf(raw) }.getOrNull()
}

/** Price point de assinatura da App Store num território (preço ao cliente em centavos). */
data class AppStorePricePoint(val id: String, val customerPriceCents: Long)
