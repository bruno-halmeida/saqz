package br.com.saqz.adminweb.http

import br.com.saqz.groups.application.admin.AdminGroupDetail
import br.com.saqz.groups.application.admin.AdminGroupDirectory
import br.com.saqz.groups.application.admin.AdminGroupPage
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Seção "Grupos" do adm-web. Fora do component scan; fiação em PlatformAdminConfiguration. */
@RestController
class AdminGroupsController(
    private val directory: AdminGroupDirectory,
) {
    @GetMapping("/admin/groups")
    fun list(
        @RequestParam(required = false) query: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<AdminGroupPage> {
        if (page < 1 || size !in 1..MAX_PAGE_SIZE) return ResponseEntity.badRequest().build()
        if (status != null && status !in VALID_STATUSES) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(directory.list(query, status, page, size))
    }

    @GetMapping("/admin/groups/{id}")
    fun detail(@PathVariable id: UUID): ResponseEntity<AdminGroupDetail> =
        directory.find(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    private companion object {
        const val MAX_PAGE_SIZE = 100
        val VALID_STATUSES = setOf("active", "deleted")
    }
}
