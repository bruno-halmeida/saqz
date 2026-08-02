package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.invite.preview.PreviewInvite
import br.com.saqz.groups.application.invite.preview.PreviewInviteCard
import br.com.saqz.groups.application.invite.preview.PreviewInviteResult
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.DayOfWeek
import java.time.Instant
import java.time.format.DateTimeFormatter

data class PreviewInviteRequest @JsonCreator constructor(
    @JsonProperty("code") val code: String?,
)

data class PreviewInviteResponse(
    val groupName: String,
    val city: String?,
    val composition: GroupComposition?,
    val level: GroupLevel?,
    val memberCount: Int,
    val regularSlots: List<PreviewRegularSlotResponse>,
    val inviterName: String?,
    val entryRequiresApproval: Boolean,
    val expiresAt: Instant?,
    val nextGame: PreviewNextGameResponse?,
) {
    companion object {
        fun from(card: PreviewInviteCard) = PreviewInviteResponse(
            groupName = card.groupName,
            city = card.city,
            composition = card.composition,
            level = card.level,
            memberCount = card.memberCount,
            regularSlots = card.regularSlots.map {
                PreviewRegularSlotResponse(it.weekday, it.startTime.format(OUTPUT_TIME))
            },
            inviterName = card.inviterName,
            entryRequiresApproval = card.entryRequiresApproval,
            expiresAt = card.expiresAt,
            nextGame = card.nextGame?.let {
                PreviewNextGameResponse(it.startsAt, it.venueName, it.court)
            },
        )

        private val OUTPUT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

data class PreviewRegularSlotResponse(
    val weekday: DayOfWeek,
    val startTime: String,
)

data class PreviewNextGameResponse(
    val startsAt: Instant,
    val venueName: String,
    val court: String?,
)

class InvitePreviewInvalidException : RuntimeException()

class InvitePreviewExpiredException(val expiredAt: Instant) : RuntimeException()

class InvitePreviewAttemptLimitException(val retryAfterSeconds: Int) : RuntimeException()

@RestController
class AccessInvitePreviewController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val previewInvite: PreviewInvite,
) {
    @PostMapping("/api/invites/preview")
    fun preview(
        @AuthenticationPrincipal identity: RequestIdentity?,
        request: HttpServletRequest,
        @RequestBody body: PreviewInviteRequest,
    ): PreviewInviteResponse = when (
        val result = previewInvite.execute(
            actor = identity?.let { actorResolver.resolve(it) },
            ipAddress = request.remoteAddr.orEmpty(),
            rawCode = body.code.orEmpty(),
        )
    ) {
        PreviewInviteResult.Invalid -> throw InvitePreviewInvalidException()
        is PreviewInviteResult.Expired -> throw InvitePreviewExpiredException(result.expiredAt)
        is PreviewInviteResult.AttemptLimit -> throw InvitePreviewAttemptLimitException(result.retryAfterSeconds)
        is PreviewInviteResult.Success -> PreviewInviteResponse.from(result.card)
    }
}
