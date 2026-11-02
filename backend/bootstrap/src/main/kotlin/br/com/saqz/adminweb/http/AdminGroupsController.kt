package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.groups.application.admin.AdminGroupDetail
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupMemberPage
import br.com.saqz.groups.application.admin.AdminGroupPage
import br.com.saqz.sharedkernel.RequestIdentity
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Seção "Grupos" do adm-web. Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminGroupsController(
    private val directory: AdminGroupDirectory,
    private val adminLookup: PlatformAdminLookup,
) {
    private val log = LoggerFactory.getLogger("br.com.saqz.admin.groups")

    @GetMapping("/admin/groups")
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) dir: String?,
    ): ResponseEntity<AdminGroupPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        if (status != null && status !in VALID_STATUSES) return ResponseEntity.badRequest().build()
        if (sort != null && sort !in AdminGroupDirectory.SORT_KEYS) return ResponseEntity.badRequest().build()
        if (dir != null && dir !in SORT_DIRECTIONS) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(directory.list(query, status, page, size, sort, dir == "desc"))
    }

    @GetMapping("/admin/groups/{id}")
    fun detail(
        @PathVariable id: UUID,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminGroupDetail> {
        val group = directory.find(id) ?: return ResponseEntity.notFound().build()
        record("opened", identity, id)
        return ResponseEntity.ok(group)
    }

    @GetMapping("/admin/groups/{id}/members")
    fun members(
        @PathVariable id: UUID,
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<AdminGroupMemberPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        val members = directory.members(id, query, page, size) ?: return ResponseEntity.notFound().build()
        record("members_listed", identity, id)
        return ResponseEntity.ok(members)
    }

    // A ficha traz telefones e chave Pix: fica registrado quem abriu, sem nenhum dado dela.
    private fun record(action: String, identity: RequestIdentity, groupId: UUID) =
        log.info("admin_group action={} admin={} group={}", action, adminLookup.findBySubject(identity.subject)?.userId, groupId)

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val VALID_STATUSES = setOf("active", "deleted")
    }
}
