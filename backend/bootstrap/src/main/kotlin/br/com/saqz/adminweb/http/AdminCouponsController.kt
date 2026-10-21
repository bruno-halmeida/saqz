package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreate
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
import br.com.saqz.subscriptions.application.CouponCampaignRules
import br.com.saqz.subscriptions.application.CouponPricePreview
import br.com.saqz.subscriptions.application.PreviewCouponPrices
import br.com.saqz.subscriptions.domain.CouponEligibility
import br.com.saqz.subscriptions.domain.CouponProduct
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** POJO mutável: binding sem módulo Kotlin do Jackson (mesmo padrão do UpdateSessionProfileRequest). */
class CreateCouponRequest {
    var code: String? = null
    var campaign: String? = null
    var discountPercent: Int? = null
    var durationCycles: Int? = null
    var validUntil: Instant? = null
    var maxUses: Int? = null

    /** NEVER_SUBSCRIBED e/ou FORMER_SUBSCRIBER. Ausente (cliente antigo) = qualquer pessoa. */
    var eligibility: List<String>? = null

    /** PLANO:CICLO (ex.: ORGANIZADOR:MONTHLY). Ausente ou vazio = todos. */
    var products: List<String>? = null

    /** Parceiro dono do cupom (seção Parceiros); opcional. */
    var partnerId: UUID? = null
}

data class CreatedCouponResponse(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int?,
    val validUntil: Instant?,
    val campaign: String? = null,
    val stores: Boolean = false,
)

/** Recusa com o motivo em português para o admin mostrar. */
data class CouponRejection(val message: String)

/** Seção "Cupons" do adm-web. Fora do component scan; fiação explícita. */
@RestController
class AdminCouponsController(
    private val directory: AdminCouponDirectory,
    private val now: () -> Instant = Instant::now,
    private val preview: PreviewCouponPrices = PreviewCouponPrices(null),
) {
    @GetMapping("/admin/coupons")
    fun list(): List<AdminCoupon> = directory.list()

    @PostMapping("/admin/coupons/preview")
    fun preview(@RequestBody request: CreateCouponRequest): ResponseEntity<Any> {
        val parsed = parse(request, requireCode = false)
        if (parsed is Parsed.Rejected) return ResponseEntity.badRequest().body(CouponRejection(parsed.message))
        val command = (parsed as Parsed.Ok).command
        return ResponseEntity.ok(
            preview.execute(command.discountPercent, command.durationCycles, command.products, command.maxUses) as CouponPricePreview,
        )
    }

    @PostMapping("/admin/coupons")
    fun create(@RequestBody request: CreateCouponRequest): ResponseEntity<Any> {
        val parsed = parse(request, requireCode = true)
        if (parsed is Parsed.Rejected) return ResponseEntity.badRequest().body(CouponRejection(parsed.message))
        val command = (parsed as Parsed.Ok).command
        return when (val result = directory.create(command)) {
            is AdminCouponCreateResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                CreatedCouponResponse(
                    id = result.coupon.id,
                    code = result.coupon.code,
                    discountPercent = result.coupon.discountPercent,
                    durationCycles = result.coupon.durationCycles,
                    validUntil = result.coupon.validUntil,
                    campaign = result.coupon.campaign,
                    stores = result.coupon.stores,
                ),
            )
            AdminCouponCreateResult.DuplicateCode -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @PostMapping("/admin/coupons/{id}/deactivate")
    fun deactivate(@PathVariable id: UUID): ResponseEntity<Void> =
        if (directory.deactivate(id, now())) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/admin/coupons/{id}/retry")
    fun retry(@PathVariable id: UUID): ResponseEntity<Map<String, Int>> =
        ResponseEntity.ok(mapOf("retried" to directory.retryFailedOffers(id)))

    private sealed interface Parsed {
        data class Ok(val command: AdminCouponCreate) : Parsed
        data class Rejected(val message: String) : Parsed
    }

    private fun parse(request: CreateCouponRequest, requireCode: Boolean): Parsed {
        val code = request.code?.trim()?.uppercase().orEmpty()
        if (requireCode && !CouponCampaignRules.CODE.matches(code)) {
            return Parsed.Rejected("Código com 3 a $MAX_CODE_LENGTH letras ou números, sem espaço.")
        }
        val discount = request.discountPercent ?: 0
        if (discount !in 1..100) return Parsed.Rejected("Desconto entre 1% e 100%.")
        val durationCycles = request.durationCycles
        if (durationCycles != null && durationCycles < 1) return Parsed.Rejected("Duração de pelo menos 1 ciclo.")
        val validUntil = request.validUntil
        if (validUntil != null && validUntil.isBefore(now())) return Parsed.Rejected("A validade já passou.")
        val maxUses = request.maxUses
        if (maxUses != null && maxUses < 1) return Parsed.Rejected("Limite de usos de pelo menos 1.")
        val campaign = request.campaign?.trim()?.takeIf { it.isNotEmpty() }
        if (requireCode && request.eligibility != null && campaign == null) return Parsed.Rejected("Dê um nome à campanha.")
        if (campaign != null && campaign.length > MAX_CAMPAIGN_LENGTH) return Parsed.Rejected("Nome da campanha com até $MAX_CAMPAIGN_LENGTH letras.")
        val eligibility = request.eligibility?.map { raw ->
            CouponEligibility.entries.firstOrNull { it.name == raw } ?: return Parsed.Rejected("Quem pode usar inválido: $raw")
        }?.toSet()
        if (eligibility != null && eligibility.isEmpty()) return Parsed.Rejected("Marque ao menos uma opção em \"Quem pode usar\".")
        val products = request.products.orEmpty().map { raw ->
            CouponProduct.parse(raw) ?: return Parsed.Rejected("Plano inválido: $raw")
        }.toSet()
        val covered = if (products.isEmpty()) CouponProduct.ALL else products.toList()
        // Com duração, o cupom vai às lojas: o que a loja não aceita é recusado aqui, com o motivo.
        val stores = durationCycles != null
        if (stores) CouponCampaignRules.storeRejection(discount, durationCycles, covered, maxUses, validUntil, now())?.let { return Parsed.Rejected(it) }
        return Parsed.Ok(
            AdminCouponCreate(
                code = code,
                campaign = campaign,
                discountPercent = discount,
                durationCycles = durationCycles,
                validUntil = validUntil,
                maxUses = maxUses,
                eligibility = eligibility.orEmpty(),
                products = products.takeIf { it.size < CouponProduct.ALL.size }.orEmpty(),
                stores = stores,
                partnerId = request.partnerId,
            ),
        )
    }

    private companion object {
        const val MAX_CODE_LENGTH = 32
        const val MAX_CAMPAIGN_LENGTH = 120
    }
}
