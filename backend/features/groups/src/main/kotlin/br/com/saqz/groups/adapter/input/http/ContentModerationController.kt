package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.moderation.BlockedUser
import br.com.saqz.groups.application.moderation.ContentModerationService
import br.com.saqz.groups.application.moderation.ContentReportRequest
import br.com.saqz.groups.application.moderation.ModerationError
import br.com.saqz.groups.application.moderation.ModerationResult
import br.com.saqz.groups.application.moderation.ReportReason
import br.com.saqz.groups.application.moderation.ReportTarget
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class ContentReportHttpRequest @JsonCreator constructor(
    @JsonProperty("groupId") val groupId: String?,
    @JsonProperty("targetType") val targetType: String?,
    @JsonProperty("targetId") val targetId: String?,
    @JsonProperty("reason") val reason: String?,
    @JsonProperty("details") val details: String? = null,
)

data class BlockUserHttpRequest @JsonCreator constructor(@JsonProperty("groupId") val groupId: String?)

/** Denúncia e bloqueio feitos pelo app. O motivo BLOCKED é do servidor, nunca do cliente. */
@RestController
class ContentModerationController(
    private val actors: VerifiedGroupActorResolver,
    private val service: ContentModerationService,
) {
    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun report(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody request: ContentReportHttpRequest) {
        val reason = enumOrNull<ReportReason>(request.reason)?.takeUnless { it == ReportReason.BLOCKED }
            ?: invalid("reason")
        service.report(actors.resolve(identity), ContentReportRequest(
            groupId = uuid(request.groupId) ?: invalid("groupId"),
            target = enumOrNull<ReportTarget>(request.targetType) ?: invalid("targetType"),
            targetId = uuid(request.targetId) ?: invalid("targetId"),
            reason = reason,
            details = request.details,
        )).orThrow()
    }

    @GetMapping("/api/me/blocks")
    fun blocks(@AuthenticationPrincipal identity: RequestIdentity): List<BlockedUser> =
        service.blocks(actors.resolve(identity))

    @PutMapping("/api/me/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun block(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable userId: String,
        @RequestBody request: BlockUserHttpRequest,
    ) {
        val blocked = uuid(userId) ?: throw GroupNotFoundException()
        service.block(actors.resolve(identity), blocked, uuid(request.groupId) ?: invalid("groupId")).orThrow()
    }

    @DeleteMapping("/api/me/blocks/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable userId: String) {
        val blocked = uuid(userId) ?: return
        service.unblock(actors.resolve(identity), blocked)
    }

    private fun ModerationResult.orThrow() {
        if (this is ModerationResult.Failure) when (reason) {
            ModerationError.NOT_FOUND -> throw GroupNotFoundException()
            ModerationError.INVALID -> invalid("request")
        }
    }

    private fun uuid(raw: String?) = raw?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private inline fun <reified E : Enum<E>> enumOrNull(raw: String?) = raw?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is invalid")), 422)
}
