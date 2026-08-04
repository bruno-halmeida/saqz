package br.com.saqz.subscriptions.application

import br.com.saqz.subscriptions.domain.Coupon
import java.time.Instant
import java.util.UUID

data class AdminCoupon(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int?,
    val validUntil: Instant?,
    val redemptions: Long,
    /** Assinaturas não canceladas com desconto ainda ativo deste cupom. */
    val activeSubscriptions: Long,
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

    fun create(code: String, discountPercent: Int, durationCycles: Int?, validUntil: Instant?): AdminCouponCreateResult

    /** Desativa expirando valid_until para agora (não há flag no schema); idempotente. */
    fun deactivate(couponId: UUID, now: Instant): Boolean
}
