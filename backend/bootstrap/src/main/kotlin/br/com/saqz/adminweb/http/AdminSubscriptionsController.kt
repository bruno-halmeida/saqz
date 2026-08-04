package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminSubscriptionCanceler
import br.com.saqz.subscriptions.application.AdminSubscriptionDetail
import br.com.saqz.subscriptions.application.AdminSubscriptionDirectory
import br.com.saqz.subscriptions.application.AdminSubscriptionPage
import br.com.saqz.subscriptions.application.CancelSubscriptionResult
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Seção "Receita · Assinaturas" do adm-web. Fora do component scan; fiação explícita. */
@RestController
class AdminSubscriptionsController(
    private val directory: AdminSubscriptionDirectory,
    private val canceler: AdminSubscriptionCanceler,
) {
    @GetMapping("/admin/subscriptions")
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) plan: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<AdminSubscriptionPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        if (plan != null && plan !in VALID_PLANS) return ResponseEntity.badRequest().build()
        if (status != null && status !in VALID_STATUSES) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(directory.list(query, plan, status, page, size))
    }

    @GetMapping("/admin/subscriptions/{ownerId}")
    fun detail(@PathVariable ownerId: UUID): ResponseEntity<AdminSubscriptionDetail> =
        directory.find(ownerId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PostMapping("/admin/subscriptions/{ownerId}/cancel")
    fun cancel(@PathVariable ownerId: UUID): ResponseEntity<Void> =
        when (canceler.cancel(ownerId)) {
            null -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            is CancelSubscriptionResult.Success -> ResponseEntity.noContent().build()
            CancelSubscriptionResult.NotFound -> ResponseEntity.notFound().build()
            CancelSubscriptionResult.AlreadyCanceled -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val VALID_PLANS = setOf("TITULAR", "ORGANIZADOR", "ILIMITADO")
        val VALID_STATUSES = setOf("ACTIVE", "PAST_DUE", "CANCELED")
    }
}
