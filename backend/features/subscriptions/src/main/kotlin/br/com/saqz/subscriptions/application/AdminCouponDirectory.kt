package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponPlatform
import br.com.saqz.subscriptions.domain.CouponProduct
import br.com.saqz.subscriptions.domain.CouponStoreOfferState
import java.time.Instant
import java.util.UUID

/** Estado de uma plataforma na lista do admin: o pior entre as ofertas dela, com o motivo da falha. */
data class AdminCouponPlatformState(
    val state: CouponStoreOfferState,
    val message: String?,
)

data class AdminCoupon(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int?,
    val validUntil: Instant?,
    val redemptions: Long,
    /** Assinaturas não canceladas com desconto ainda ativo deste cupom. */
    val activeSubscriptions: Long,
    val campaign: String? = null,
    val maxUses: Int? = null,
    val eligibility: Set<CouponEligibility> = emptySet(),
    val products: List<String> = emptyList(),
    val stores: Boolean = false,
    val endedAt: Instant? = null,
    val redemptionsByPlatform: Map<CouponPlatform, Long> = emptyMap(),
    /** ANDROID e IOS; ausente quando o cupom é só do site. */
    val platforms: Map<CouponPlatform, AdminCouponPlatformState> = emptyMap(),
    val partnerId: UUID? = null,
)

data class AdminCouponCreate(
    val code: String,
    val campaign: String?,
    val discountPercent: Int,
    val durationCycles: Int?,
    val validUntil: Instant?,
    val maxUses: Int? = null,
    val eligibility: Set<CouponEligibility> = emptySet(),
    val products: Set<CouponProduct> = emptySet(),
    val stores: Boolean = false,
    val partnerId: UUID? = null,
)

sealed interface AdminCouponCreateResult {
    data class Created(val coupon: Coupon) : AdminCouponCreateResult

    data object DuplicateCode : AdminCouponCreateResult
}

/**
 * Administração de cupons (adm-web). Sem paginação: cupons são unidades — se um dia
 * passarem de algumas dezenas, pagina-se.
 */
interface AdminCouponDirectory {
    fun list(): List<AdminCoupon>

    fun create(code: String, discountPercent: Int, durationCycles: Int?, validUntil: Instant?): AdminCouponCreateResult =
        create(AdminCouponCreate(code, null, discountPercent, durationCycles, validUntil))

    /** Cria o cupom e, se vai às lojas, as ofertas pendentes, na mesma transação. */
    fun create(command: AdminCouponCreate): AdminCouponCreateResult

    /**
     * Encerra: o cupom deixa de valer agora e as ofertas nas lojas passam a ser desativadas pelo job.
     * Idempotente.
     */
    fun deactivate(couponId: UUID, now: Instant): Boolean

    /** "Tentar de novo": ofertas que falharam voltam a pendente com as tentativas zeradas. */
    fun retryFailedOffers(couponId: UUID): Int = 0
}
