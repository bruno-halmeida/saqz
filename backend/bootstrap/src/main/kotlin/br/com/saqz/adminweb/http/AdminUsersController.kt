package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.AdminUserPage
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.access.application.admin.AdminUserContact
import br.com.saqz.access.application.admin.AdminUserCourtesy
import br.com.saqz.access.application.admin.AdminUserDetail
import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.PlanCourtesies
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Corpo de entrada é classe mutável: o Jackson do projeto não instancia data class. */
class AdminUserContactBody {
    var result: String? = null
    var note: String? = null
}

/** Seção "Usuários" do adm-web. Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminUsersController(
    private val directory: AdminUserDirectory,
    private val adminLookup: PlatformAdminLookup,
    private val courtesies: PlanCourtesies? = null,
) {
    private val log = LoggerFactory.getLogger("br.com.saqz.admin.users")

    @GetMapping("/admin/users")
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) plan: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) dir: String?,
        @RequestParam(required = false) trial: String?,
    ): ResponseEntity<AdminUserPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        if (plan != null && plan !in VALID_PLANS) return ResponseEntity.badRequest().build()
        if (status != null && status !in VALID_STATUSES) return ResponseEntity.badRequest().build()
        if (sort != null && sort !in AdminUserDirectory.SORT_KEYS) return ResponseEntity.badRequest().build()
        if (dir != null && dir !in SORT_DIRECTIONS) return ResponseEntity.badRequest().build()
        if (trial != null && trial !in VALID_TRIALS) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(directory.list(query, plan, status, page, size, sort, dir == "desc", trial))
    }

    @GetMapping("/admin/users/{id}")
    fun detail(
        @PathVariable id: UUID,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminUserDetail> {
        val user = directory.find(id) ?: return ResponseEntity.notFound().build()
        // A ficha traz telefone e anotações: fica registrado quem abriu, sem nenhum dado dela.
        record("opened", identity, id)
        // Cortesia de plano ativa (DEB-097), para o painel; ausente quando não há.
        val courtesy = courtesies?.active(id)?.let { AdminUserCourtesy(it.plan.name, it.endsAt, it.reason, it.createdAt) }
        return ResponseEntity.ok(user.copy(courtesy = courtesy))
    }

    /** Registro de um contato da equipe com a pessoa (DEB-162). O resultado mais recente decide se ela pode ser procurada. */
    @PostMapping("/admin/users/{id}/contacts")
    fun addContact(
        @PathVariable id: UUID,
        @RequestBody body: AdminUserContactBody,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminUserContact> {
        val result = body.result?.takeIf { it in AdminUserDirectory.CONTACT_RESULTS }
            ?: return ResponseEntity.badRequest().build()
        val note = body.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > AdminUserDirectory.CONTACT_NOTE_MAX) return ResponseEntity.badRequest().build()
        val author = adminLookup.findBySubject(identity.subject)?.userId
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val contact = directory.addContact(id, author, result, note) ?: return ResponseEntity.notFound().build()
        record("contact_added", identity, id)
        return ResponseEntity.status(HttpStatus.CREATED).body(contact)
    }

    @PostMapping("/admin/users/{id}/suspend")
    fun suspend(
        @PathVariable id: UUID,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<Void> {
        // Auto-suspensão trancaria o (possivelmente único) admin para fora do painel:
        // o lookup da guarda ignora contas suspensas e ninguém conseguiria reativar.
        if (adminLookup.findBySubject(identity.subject)?.userId == id) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        return if (directory.suspend(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/admin/users/{id}/reactivate")
    fun reactivate(@PathVariable id: UUID): ResponseEntity<Void> =
        if (directory.reactivate(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun record(action: String, identity: RequestIdentity, userId: UUID) =
        log.info("admin_user action={} admin={} user={}", action, adminLookup.findBySubject(identity.subject)?.userId, userId)

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val VALID_PLANS = setOf("FREE", "TITULAR", "ORGANIZADOR", "ILIMITADO")
        val VALID_STATUSES = setOf("active", "suspended")
        val VALID_TRIALS = setOf("active", "ended")
    }
}
