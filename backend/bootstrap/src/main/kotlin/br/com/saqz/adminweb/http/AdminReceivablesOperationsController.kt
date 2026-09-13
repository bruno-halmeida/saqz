package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RecoverOperationalRequest {
    var requestId: UUID? = null
    var reason: String? = null
}

class PublishOperationalNoticeRequest {
    var requestId: UUID? = null
    var audience: OperationalNoticeAudience? = null
    var title: String? = null
    var message: String? = null
    var startsAt: Instant? = null
    var endsAt: Instant? = null
}

@RestController
@RequestMapping("/admin/receivables")
class AdminReceivablesOperationsController(
    private val admins: PlatformAdminLookup,
    private val store: OperationalReceivablesStore,
    private val recovery: RecoverOperationalFailure,
    private val notices: OperationalNotices,
    private val clock: Clock,
) {
    @GetMapping("/operations")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(required = false) status: OperationStatus?,
        @RequestParam(required = false) kind: OperationKind?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<*> = admin(identity) {
        if (page < 1 || size !in 1..100 || status == OperationStatus.SUCCEEDED) return@admin invalid()
        ok(store.list(status, kind, page, size))
    }

    @GetMapping("/operations/{operationId}")
    fun detail(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable operationId: UUID,
    ): ResponseEntity<*> = admin(identity) { store.detail(operationId)?.let(::ok) ?: notFound() }

    @PostMapping("/operations/{operationId}/recovery")
    fun recover(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable operationId: UUID,
        @RequestBody body: RecoverOperationalRequest,
    ): ResponseEntity<*> = admin(identity) { actor ->
        val requestId = body.requestId ?: return@admin invalid()
        val reason = body.reason ?: return@admin invalid()
        when (val result = recovery.execute(OperationalRecoveryCommand(requestId, operationId, actor, reason))) {
            is OperationalRecoveryResultEnvelope.Done -> ResponseEntity.status(
                if (result.outcome.result == OperationalRecoveryResult.STILL_UNKNOWN) 503 else 200,
            ).header("Cache-Control", "no-store").body(result.outcome)
            OperationalRecoveryResultEnvelope.Invalid -> invalid()
            OperationalRecoveryResultEnvelope.NotFound -> notFound()
            OperationalRecoveryResultEnvelope.NotRecoverable -> ResponseEntity.status(409).header("Cache-Control", "no-store")
                .body(mapOf("requestId" to requestId, "operationId" to operationId, "result" to OperationalRecoveryResult.NOT_RECOVERABLE))
            OperationalRecoveryResultEnvelope.Conflict, OperationalRecoveryResultEnvelope.Busy ->
                ResponseEntity.status(409).header("Cache-Control", "no-store").build<Void>()
        }
    }

    @GetMapping("/notices")
    fun notices(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(required = false) audience: OperationalNoticeAudience?,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<*> = admin(identity) {
        if (page < 1 || size !in 1..100) return@admin invalid()
        ok(notices.list(audience, page, size))
    }

    @PostMapping("/notices")
    fun publishNotice(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody body: PublishOperationalNoticeRequest,
    ): ResponseEntity<*> = admin(identity) { actor ->
        val requestId = body.requestId ?: return@admin invalid()
        val command = try {
            PublishOperationalNotice(requestId, actor, requireNotNull(body.audience), requireNotNull(body.title),
                requireNotNull(body.message), requireNotNull(body.startsAt), body.endsAt)
        } catch (_: IllegalArgumentException) { return@admin invalid() }
        try {
            ResponseEntity.status(201).header("Cache-Control", "no-store").body(notices.publish(command, clock.instant()))
        } catch (_: IllegalArgumentException) { invalid() }
        catch (_: OperationalNoticeConflict) { ResponseEntity.status(409).header("Cache-Control", "no-store").build<Void>() }
    }

    private fun admin(identity: RequestIdentity, block: (UUID) -> ResponseEntity<*>): ResponseEntity<*> {
        val actor = admins.findBySubject(identity.subject)?.userId
            ?: return ResponseEntity.status(403).header("Cache-Control", "no-store").build<Void>()
        return block(actor)
    }

    private fun ok(value: Any): ResponseEntity<*> = ResponseEntity.ok().header("Cache-Control", "no-store").body(value)
    private fun invalid(): ResponseEntity<*> = ResponseEntity.badRequest().header("Cache-Control", "no-store").build<Void>()
    private fun notFound(): ResponseEntity<*> = ResponseEntity.notFound().header("Cache-Control", "no-store").build<Void>()
}
