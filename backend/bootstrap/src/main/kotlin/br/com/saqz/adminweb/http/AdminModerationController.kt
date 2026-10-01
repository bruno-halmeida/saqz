package br.com.saqz.adminweb.http

import br.com.saqz.groups.application.moderation.ContentModerationService
import br.com.saqz.groups.application.moderation.ContentReportSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Fila de denúncias (Apple 1.2: resposta em até 24 h). A equipe recebe cada denúncia por
 * e-mail; daqui ela remove o aviso denunciado e marca a denúncia como resolvida. Suspender a
 * conta responsável é o `POST /admin/users/{id}/suspend`. Só GET/POST: é o que o CORS do
 * adm-web libera. Fora do component scan; fiação em PlatformAdminConfiguration.
 */
@RestController
class AdminModerationController(private val moderation: ContentModerationService) {
    @GetMapping("/admin/reports")
    fun reports(
        @RequestParam(defaultValue = "open") status: String,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ResponseEntity<List<ContentReportSummary>> {
        if (status !in setOf("open", "all") || limit !in 1..MAX_LIMIT) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(moderation.reports(openOnly = status == "open", limit = limit))
    }

    @PostMapping("/admin/reports/{id}/resolve")
    fun resolve(@PathVariable id: UUID): ResponseEntity<Void> =
        if (moderation.resolve(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping("/admin/groups/{groupId}/messages/{messageId}/remove")
    fun removeMessage(@PathVariable groupId: UUID, @PathVariable messageId: UUID): ResponseEntity<Void> =
        if (moderation.deleteMessage(groupId, messageId)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private companion object {
        const val MAX_LIMIT = 500
    }
}
