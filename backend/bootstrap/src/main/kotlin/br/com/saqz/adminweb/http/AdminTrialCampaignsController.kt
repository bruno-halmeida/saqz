package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.TrialCampaignStore
import br.com.saqz.subscriptions.application.TrialOfferMode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

class TrialModeRequest { var mode: String? = null }
class TrialCouponRequest {
    var code: String? = null
    var campaign: String? = null
    var trialDays: BigDecimal? = null
    var validUntil: Instant? = null
    var maxUses: BigDecimal? = null
}
data class TrialModeResponse(val mode: TrialOfferMode)

/** Admin access is enforced by PlatformAdminGuardFilter for every /admin route. */
@RestController
class AdminTrialCampaignsController(private val store: TrialCampaignStore, private val clock: Clock) {
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun invalidBody(): ResponseEntity<Void> = ResponseEntity.badRequest().build()

    @GetMapping("/admin/trial-offer")
    fun mode() = TrialModeResponse(store.mode())

    @PutMapping("/admin/trial-offer")
    fun update(@RequestBody body: TrialModeRequest): ResponseEntity<TrialModeResponse> {
        val mode = body.mode?.let { value -> TrialOfferMode.entries.find { it.name == value } }
            ?: return ResponseEntity.badRequest().build()
        store.setMode(mode)
        return ResponseEntity.ok(TrialModeResponse(mode))
    }
    @GetMapping("/admin/trial-coupons")
    fun list() = store.list()

    @PostMapping("/admin/trial-coupons")
    fun create(@RequestBody body: TrialCouponRequest): ResponseEntity<*> {
        val code = body.code?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val days = runCatching { body.trialDays?.intValueExact() }.getOrNull()
        val max = runCatching { body.maxUses?.intValueExact() }.getOrNull()
        val campaign = body.campaign?.trim()?.takeIf { it.isNotEmpty() }
        if (!code.matches(Regex("[A-Z0-9]{1,32}")) || days == null || days !in 1..365 ||
            (body.maxUses != null && (max == null || max < 1)) || (campaign?.length ?: 0) > 120 ||
            (body.validUntil != null && !requireNotNull(body.validUntil).isAfter(clock.instant()))
        ) return ResponseEntity.badRequest().build<Void>()
        val created = store.create(code, campaign, days, body.validUntil, max)
            ?: return ResponseEntity.status(409).build<Void>()
        return ResponseEntity.status(201).body(created)
    }
    @PostMapping("/admin/trial-coupons/{id}/deactivate")
    fun deactivate(@PathVariable id: UUID): ResponseEntity<Void> =
        if (store.deactivate(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}
