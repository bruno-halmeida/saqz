package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminCoupon
import br.com.saqz.subscriptions.application.AdminCouponCreateResult
import br.com.saqz.subscriptions.application.AdminCouponDirectory
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
    var discountPercent: Int? = null
    var durationCycles: Int? = null
    var validUntil: Instant? = null
}

data class CreatedCouponResponse(
    val id: UUID,
    val code: String,
    val discountPercent: Int,
    val durationCycles: Int?,
    val validUntil: Instant?,
)

/** Seção "Cupons" do adm-web. Fora do component scan; fiação explícita. */
@RestController
class AdminCouponsController(
    private val directory: AdminCouponDirectory,
    private val now: () -> Instant = Instant::now,
) {
    @GetMapping("/admin/coupons")
    fun list(): List<AdminCoupon> = directory.list()

    @PostMapping("/admin/coupons")
    fun create(@RequestBody request: CreateCouponRequest): ResponseEntity<CreatedCouponResponse> {
        val code = request.code?.trim()?.uppercase().orEmpty()
        val discount = request.discountPercent ?: 0
        if (code.isEmpty() || code.length > MAX_CODE_LENGTH || !code.all { it.isLetterOrDigit() }) {
            return ResponseEntity.badRequest().build()
        }
        if (discount !in 1..100) return ResponseEntity.badRequest().build()
        val durationCycles = request.durationCycles
        val validUntil = request.validUntil
        if (durationCycles != null && durationCycles < 1) return ResponseEntity.badRequest().build()
        if (validUntil != null && validUntil.isBefore(now())) return ResponseEntity.badRequest().build()

        return when (val result = directory.create(code, discount, durationCycles, validUntil)) {
            is AdminCouponCreateResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(
                CreatedCouponResponse(
                    id = result.coupon.id,
                    code = result.coupon.code,
                    discountPercent = result.coupon.discountPercent,
                    durationCycles = result.coupon.durationCycles,
                    validUntil = result.coupon.validUntil,
                ),
            )
            AdminCouponCreateResult.DuplicateCode -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @PostMapping("/admin/coupons/{id}/deactivate")
    fun deactivate(@PathVariable id: UUID): ResponseEntity<Void> =
        if (directory.deactivate(id, now())) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private companion object {
        const val MAX_CODE_LENGTH = 32
    }
}
