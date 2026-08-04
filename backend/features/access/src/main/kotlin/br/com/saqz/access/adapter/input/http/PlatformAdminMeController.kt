package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class PlatformAdminMeResponse(
    val userId: UUID,
    val email: String?,
    val displayName: String?,
)

@RestController
class PlatformAdminMeController(
    private val lookup: PlatformAdminLookup,
) {
    @GetMapping("/admin/me")
    fun me(
        @AuthenticationPrincipal identity: RequestIdentity,
    ): ResponseEntity<PlatformAdminMeResponse> {
        val admin = lookup.findBySubject(identity.subject)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        return ResponseEntity.ok(PlatformAdminMeResponse(admin.userId, admin.email, admin.displayName))
    }
}
