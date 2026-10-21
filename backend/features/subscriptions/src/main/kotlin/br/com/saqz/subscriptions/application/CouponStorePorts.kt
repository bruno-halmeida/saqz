package br.com.saqz.subscriptions.application

/**
 * Falha ao falar com a loja. [permanent] = a loja recusou o pedido (4xx de validação): tentar de
 * novo igual não adianta, a oferta vai a FAILED com [message]. Senão, o job tenta de novo depois.
 */
class CouponStoreException(message: String, val permanent: Boolean, cause: Throwable? = null) : RuntimeException(message, cause)

enum class GooglePlayOfferState { DRAFT, ACTIVE, INACTIVE, UNKNOWN }

data class GooglePlayOfferSpec(
    val productId: String,
    val basePlanId: String,
    val offerId: String,
    /** Número de cobranças com desconto (`recurrenceCount`). */
    val cycles: Int,
    /** Período do plano base em ISO 8601 (P1M ou P1Y). */
    val period: String,
    /** Fração de desconto, entre 0 e 1 (exclusivos). */
    val relativeDiscount: Double,
    val tags: List<String>,
)

/** `monetization.subscriptions.basePlans.offers` da Android Publisher API v3. */
interface GooglePlayOffersApi {
    /** null quando a oferta não existe. */
    fun get(productId: String, basePlanId: String, offerId: String): GooglePlayOfferState?

    fun create(spec: GooglePlayOfferSpec): GooglePlayOfferState

    fun activate(productId: String, basePlanId: String, offerId: String): GooglePlayOfferState

    fun deactivate(productId: String, basePlanId: String, offerId: String): GooglePlayOfferState
}

data class AppStoreOfferCodeSpec(
    val subscriptionId: String,
    /** Nome de referência; a Apple o devolve como `offerIdentifier` na transação. */
    val name: String,
    /** NEW e/ou EXPIRED. */
    val customerEligibilities: List<String>,
    /** ONE_MONTH ou ONE_YEAR. */
    val duration: String,
    val numberOfPeriods: Int,
    val pricePointId: String,
)

/** App Store Connect API: códigos de oferta de assinatura. */
interface AppStoreOffersApi {
    /** Id da assinatura no App Store Connect pelo product ID. */
    fun subscriptionId(productId: String): String

    /** Price points da assinatura no Brasil (BRA). */
    fun brazilPricePoints(subscriptionId: String): List<AppStorePricePoint>

    /** Cria o código de oferta (pay as you go, só BRA). Devolve o id. */
    fun createOfferCode(spec: AppStoreOfferCodeSpec): String

    /** Cria o código personalizado da oferta. Devolve o id. */
    fun createCustomCode(offerCodeId: String, customCode: String, numberOfCodes: Int): String

    fun deactivateOfferCode(offerCodeId: String)

    fun deactivateCustomCode(customCodeId: String)
}
