package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.AdminUserDetail
import br.com.saqz.access.application.admin.AdminUserDirectory
import br.com.saqz.access.application.admin.AdminUserPage
import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Seção "Usuários" do adm-web. Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminUsersController(
    private val directory: AdminUserDirectory,
    private val adminLookup: PlatformAdminLookup,
) {
    @GetMapping("/admin/users")
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) plan: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<AdminUserPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        if (plan != null && plan !in VALID_PLANS) return ResponseEntity.badRequest().build()
        if (status != null && status !in VALID_STATUSES) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(directory.list(query, plan, status, page, size))
    }

    @GetMapping("/admin/users/{id}")
    fun detail(@PathVariable id: UUID): ResponseEntity<AdminUserDetail> =
        directory.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

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

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val VALID_PLANS = setOf("FREE", "TITULAR", "ORGANIZADOR", "ILIMITADO")
        val VALID_STATUSES = setOf("active", "suspended")
    }
}
