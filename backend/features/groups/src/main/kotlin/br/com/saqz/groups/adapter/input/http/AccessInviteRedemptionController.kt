package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class RedeemInviteRequest @JsonCreator constructor(
    @JsonProperty("code") val code: String?,
)

data class RedeemedInviteResponse(
    val status: RedeemedInviteStatus,
    val groupId: UUID,
    val role: GroupRole?,
)

enum class RedeemedInviteStatus {
    JOINED,
    PENDING,
}

class InviteInvalidOrExpiredException : RuntimeException()

class InviteGroupDeletedException : RuntimeException()

class InviteAttemptLimitException(val retryAfterSeconds: Int) : RuntimeException()

class AthleteLimitExceededException : RuntimeException()

@RestController
class AccessInviteRedemptionController(
    private val actorResolver: VerifiedGroupActorResolver,
    private val redeemInvite: RedeemInvite,
) {
    @PostMapping("/api/invites/redeem")
    fun redeem(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: RedeemInviteRequest,
    ): RedeemedInviteResponse = when (
        val result = redeemInvite.execute(actor(identity), request.code.orEmpty())
    ) {
        RedeemInviteResult.InvalidOrExpired -> throw InviteInvalidOrExpiredException()
        RedeemInviteResult.GroupDeleted -> throw InviteGroupDeletedException()
        is RedeemInviteResult.AttemptLimit -> throw InviteAttemptLimitException(result.retryAfterSeconds)
        RedeemInviteResult.AthleteLimitExceeded -> throw AthleteLimitExceededException()
        is RedeemInviteResult.Success -> RedeemedInviteResponse(
            RedeemedInviteStatus.JOINED,
            result.groupId,
            result.role,
        )
        is RedeemInviteResult.Pending -> RedeemedInviteResponse(
            RedeemedInviteStatus.PENDING,
            result.groupId,
            null,
        )
    }

    private fun actor(identity: RequestIdentity): UUID = actorResolver.resolve(identity)
}
