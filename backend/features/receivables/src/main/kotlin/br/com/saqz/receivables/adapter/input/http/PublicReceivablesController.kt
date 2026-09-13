package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.FinancialTerms
import br.com.saqz.receivables.application.OperationalNotice
import br.com.saqz.receivables.application.OperationalNoticeAudience
import br.com.saqz.receivables.application.OperationalNotices
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class PublicFinancialTerms(
    val version: String,
    val content: String,
    val effectiveAt: Instant,
    val publishedAt: Instant,
)

data class PublicCheckoutReturn(
    val status: String = "PENDING_VERIFICATION",
    val message: String = "O retorno do navegador não confirma o pagamento. Consulte o histórico no Saqz.",
)

@RestController
@RequestMapping("/public/receivables")
class PublicReceivablesController(
    private val conditions: FinancialConditions,
    private val clock: Clock,
) {
    @GetMapping("/terms/current")
    fun currentTerms(): ResponseEntity<PublicFinancialTerms> = conditions.currentTerms(clock.instant())
        ?.let { ResponseEntity.ok().header("Cache-Control", "no-store").body(it.publicView()) }
        ?: ResponseEntity.notFound().header("Cache-Control", "no-store").build()

    @GetMapping("/terms/{version}")
    fun terms(@PathVariable version: String): ResponseEntity<PublicFinancialTerms> {
        if (version.isBlank() || version.length > 64) return ResponseEntity.notFound().build()
        val now = clock.instant()
        val terms = conditions.terms(version, now)?.takeIf { it.effectiveAt <= now }
            ?: return ResponseEntity.notFound().header("Cache-Control", "no-store").build()
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(terms.publicView())
    }

    @GetMapping("/checkout-return")
    fun checkoutReturn(): ResponseEntity<PublicCheckoutReturn> = ResponseEntity.ok()
        .header("Cache-Control", "no-store").body(PublicCheckoutReturn())

    private fun FinancialTerms.publicView() = PublicFinancialTerms(version, content, effectiveAt, publishedAt)
}

fun interface ReceivablesNoticeActorResolver {
    fun resolve(identity: RequestIdentity): UUID
}

fun interface PlanNoticeAudience {
    fun includes(actorUserId: UUID): Boolean
}

@RestController
class ReceivablesNoticesController(
    private val actors: ReceivablesNoticeActorResolver,
    private val audience: PlanNoticeAudience,
    private val notices: OperationalNotices,
    private val clock: Clock,
) {
    @GetMapping("/api/receivables/notices")
    fun active(@AuthenticationPrincipal identity: RequestIdentity): ResponseEntity<List<OperationalNotice>> {
        val actor = actors.resolve(identity)
        val body = if (audience.includes(actor)) notices.active(OperationalNoticeAudience.PLAN_OWNERS, clock.instant()) else emptyList()
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(body)
    }
}
