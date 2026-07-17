package br.com.saqz.access.adapter.input.http

import br.com.saqz.access.application.invite.redeem.RedeemInvite
import br.com.saqz.access.application.invite.redeem.RedeemInviteResult
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.observability.AccessMetricEvent
import br.com.saqz.access.application.observability.AccessMetrics
import br.com.saqz.access.domain.GroupRole
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
    val groupId: UUID,
    val role: GroupRole,
)

class InviteInvalidOrExpiredException : RuntimeException()

class InviteAttemptLimitException(val retryAfterSeconds: Int) : RuntimeException()

@RestController
class AccessInviteRedemptionController(
    private val bootstrapSession: BootstrapSession,
    private val redeemInvite: RedeemInvite,
    private val metrics: AccessMetrics = AccessMetrics.NONE,
) {
    @PostMapping("/api/invites/redeem")
    fun redeem(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: RedeemInviteRequest,
    ): RedeemedInviteResponse = when (
        val result = redeemInvite.execute(actor(identity), request.code.orEmpty())
    ) {
        RedeemInviteResult.InvalidOrExpired -> throw InviteInvalidOrExpiredException()
        is RedeemInviteResult.AttemptLimit -> throw InviteAttemptLimitException(result.retryAfterSeconds)
        is RedeemInviteResult.Success -> {
            metrics.record(AccessMetricEvent("invite", "redeemed", "200"))
            RedeemedInviteResponse(result.groupId, result.role)
        }
    }

    private fun actor(identity: RequestIdentity): UUID = when (val result = bootstrapSession.execute(identity)) {
        BootstrapSessionResult.EmailNotVerified -> throw EmailNotVerifiedException()
        BootstrapSessionResult.InvalidDisplayName -> throw InvalidDisplayNameException()
        is BootstrapSessionResult.Success -> result.session.user.id
    }
}
