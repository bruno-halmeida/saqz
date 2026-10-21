package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminPartnerDirectory
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.application.PartnerReport
import br.com.saqz.subscriptions.application.CloseCommissionResult
import br.com.saqz.subscriptions.application.CommissionDuration
import br.com.saqz.subscriptions.application.CommissionMonth
import br.com.saqz.subscriptions.application.CommissionRule
import br.com.saqz.subscriptions.application.PartnerCommissionStore
import br.com.saqz.subscriptions.application.PartnerCommissionTotals
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** POJO mutável: binding sem módulo Kotlin do Jackson (mesmo padrão do CreateCouponRequest). */
class CreatePartnerRequest {
    var name: String? = null
    var contactName: String? = null
    var contactPhone: String? = null
    var contactEmail: String? = null
    var notes: String? = null
}

class CommissionRuleRequest {
    /** Nulo = sem comissão. */
    var percent: java.math.BigDecimal? = null
    var duration: String? = null
    var months: Int? = null
}

class MarkCommissionPaidRequest {
    var paidOn: java.time.LocalDate? = null
    var note: String? = null
}

/** Item da lista: o resumo do parceiro com a comissão em aberto e a fechada ainda não paga. */
data class PartnerListItem(
    val partner: br.com.saqz.subscriptions.application.Partner,
    val coupons: List<br.com.saqz.subscriptions.application.PartnerCoupon>,
    val metrics: br.com.saqz.subscriptions.application.PartnerMetrics,
    val commissionRule: CommissionRule?,
    val commission: PartnerCommissionTotals,
)

/**
 * Seção "Parceiros" do adm-web. Dados pessoais (nome e e-mail de quem pagou) só aqui, atrás do
 * PlatformAdminGuardFilter de todo /admin; sem exportação.
 */
@RestController
class AdminPartnersController(
    private val directory: AdminPartnerDirectory,
    private val commissions: PartnerCommissionStore? = null,
    private val now: () -> Instant = Instant::now,
) {
    @GetMapping("/admin/partners")
    fun list(): List<PartnerListItem> {
        val at = now()
        return directory.list(at).map { s ->
            PartnerListItem(
                s.partner, s.coupons, s.metrics,
                commissions?.rule(s.partner.id),
                commissions?.totals(s.partner.id, at) ?: PartnerCommissionTotals(0, 0),
            )
        }
    }

    @PutMapping("/admin/partners/{id}/commission-rule")
    fun setRule(@PathVariable id: UUID, @RequestBody request: CommissionRuleRequest): ResponseEntity<Any> {
        val store = commissions ?: return ResponseEntity.notFound().build()
        val rule = if (request.percent == null && request.duration == null) {
            null
        } else {
            val duration = CommissionDuration.entries.firstOrNull { it.name == request.duration }
                ?: return ResponseEntity.badRequest().body(CouponRejection("Escolha a duração da comissão."))
            val percent = request.percent?.stripTrailingZeros()?.takeIf { it.scale() <= 2 }?.setScale(2)
                ?: return ResponseEntity.badRequest().body(CouponRejection("Percentual com até duas casas."))
            runCatching { CommissionRule(percent, duration, request.months.takeIf { duration == CommissionDuration.MONTHS }) }.getOrNull()
                ?: return ResponseEntity.badRequest().body(CouponRejection("Percentual de 0,01% a 100% e meses de 1 a 60."))
        }
        return if (store.setRule(id, rule)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    /** Meses da comissão (fechados e em aberto) com os pagamentos; dados pessoais só aqui. */
    @GetMapping("/admin/partners/{id}/commission")
    fun commission(@PathVariable id: UUID): ResponseEntity<List<CommissionMonth>> {
        val months = commissions?.months(id, now()) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(months)
    }

    @PostMapping("/admin/partners/{id}/commission/{month}/close")
    fun close(@PathVariable id: UUID, @PathVariable month: String): ResponseEntity<Any> {
        val store = commissions ?: return ResponseEntity.notFound().build()
        val ym = runCatching { YearMonth.parse(month) }.getOrNull() ?: return ResponseEntity.badRequest().build()
        return when (val result = store.close(id, ym, now())) {
            is CloseCommissionResult.Closed -> ResponseEntity.ok(result.month)
            CloseCommissionResult.NotEnded -> ResponseEntity.badRequest().body(CouponRejection("Só dá para fechar mês que já terminou."))
            CloseCommissionResult.AlreadyClosed -> ResponseEntity.status(HttpStatus.CONFLICT).build()
            CloseCommissionResult.NotFound -> ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/admin/partners/{id}/commission/{month}/paid")
    fun markPaid(@PathVariable id: UUID, @PathVariable month: String, @RequestBody request: MarkCommissionPaidRequest): ResponseEntity<Any> {
        val store = commissions ?: return ResponseEntity.notFound().build()
        val ym = runCatching { YearMonth.parse(month) }.getOrNull() ?: return ResponseEntity.badRequest().build()
        val paidOn = request.paidOn ?: return ResponseEntity.badRequest().body(CouponRejection("Informe a data do pagamento."))
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTE)
        return if (store.markPaid(id, ym, paidOn, note)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/admin/partners")
    fun create(@RequestBody request: CreatePartnerRequest): ResponseEntity<Any> {
        val name = request.name?.trim().orEmpty()
        if (name.length !in 2..MAX_NAME) return ResponseEntity.badRequest().body(CouponRejection("Nome do parceiro com 2 a $MAX_NAME letras."))
        val phone = request.contactPhone?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
        if (phone != null && phone.length !in 10..11) return ResponseEntity.badRequest().body(CouponRejection("Telefone com DDD, 10 ou 11 dígitos."))
        val email = request.contactEmail?.trim()?.takeIf { it.isNotEmpty() }
        if (email != null && (email.length > MAX_EMAIL || !EMAIL.matches(email))) return ResponseEntity.badRequest().body(CouponRejection("E-mail inválido."))
        val command = PartnerCreate(
            name = name,
            contactName = request.contactName?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NAME),
            contactPhone = phone,
            contactEmail = email,
            notes = request.notes?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_NOTES),
        )
        return when (val result = directory.create(command)) {
            is PartnerCreateResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.partner)
            PartnerCreateResult.DuplicateName -> ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    @PostMapping("/admin/partners/{id}/end")
    fun end(@PathVariable id: UUID): ResponseEntity<Void> =
        if (directory.end(id, now())) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @GetMapping("/admin/partners/{id}/report")
    fun report(
        @PathVariable id: UUID,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
    ): ResponseEntity<PartnerReport> {
        if (from != null && to != null && to.isBefore(from)) return ResponseEntity.badRequest().build()
        return directory.report(id, from, to, now())?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()
    }

    private companion object {
        const val MAX_NAME = 120
        const val MAX_EMAIL = 254
        const val MAX_NOTES = 2_000
        const val MAX_NOTE = 500
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
