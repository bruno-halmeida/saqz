package br.com.saqz.adminweb.http

import br.com.saqz.subscriptions.application.AdminPartnerDirectory
import br.com.saqz.subscriptions.application.PartnerCreate
import br.com.saqz.subscriptions.application.PartnerCreateResult
import br.com.saqz.subscriptions.application.PartnerReport
import br.com.saqz.subscriptions.application.PartnerSummary
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/** POJO mutável: binding sem módulo Kotlin do Jackson (mesmo padrão do CreateCouponRequest). */
class CreatePartnerRequest {
    var name: String? = null
    var contactName: String? = null
    var contactPhone: String? = null
    var contactEmail: String? = null
    var notes: String? = null
}

/**
 * Seção "Parceiros" do adm-web. Dados pessoais (nome e e-mail de quem pagou) só aqui, atrás do
 * PlatformAdminGuardFilter de todo /admin; sem exportação.
 */
@RestController
class AdminPartnersController(
    private val directory: AdminPartnerDirectory,
    private val now: () -> Instant = Instant::now,
) {
    @GetMapping("/admin/partners")
    fun list(): List<PartnerSummary> = directory.list(now())

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
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
