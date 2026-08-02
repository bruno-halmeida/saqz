package br.com.saqz.groups.data.invite

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InviteNextGame
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeem
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.domain.membership.InviteRegularSlot
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class InviteCodeRequestTransport(val code: String)

@Serializable
private data class InviteRegularSlotTransport(
    val weekday: String = "",
    val startTime: String = "",
)

@Serializable
private data class InviteNextGameTransport(
    val startsAt: String = "",
    val venueName: String = "",
    val court: String? = null,
)

@Serializable
private data class InvitePreviewTransport(
    val groupName: String = "",
    val city: String? = null,
    val composition: String? = null,
    val level: String? = null,
    val memberCount: Int = 0,
    val regularSlots: List<InviteRegularSlotTransport> = emptyList(),
    val inviterName: String? = null,
    val entryRequiresApproval: Boolean = false,
    val expiresAt: String? = null,
    val nextGame: InviteNextGameTransport? = null,
)

@Serializable
private enum class InviteRedeemStatusTransport {
    JOINED,
    PENDING,
}

@Serializable
private data class InviteRedeemTransport(
    val status: InviteRedeemStatusTransport? = null,
    val groupId: String = "",
    val role: String? = null,
)

class KtorInviteGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : InviteGateway {
    override suspend fun preview(code: InviteCode): SaqzResult<InvitePreview, InviteError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "api/invites/preview",
                InvitePreviewTransport.serializer(),
                NetworkRequest(json.encodeToString(InviteCodeRequestTransport(code.value))),
            )
        }.toPreviewResult()

    override suspend fun redeem(code: InviteCode): SaqzResult<InviteRedeem, InviteError> = network.execute(
        HttpMethod.Post,
        "api/invites/redeem",
        InviteRedeemTransport.serializer(),
        NetworkRequest(json.encodeToString(InviteCodeRequestTransport(code.value))),
    ).toRedeemResult()
}

private fun NetworkResult<InvitePreviewTransport>.toPreviewResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toInviteError())
    is NetworkResult.Success -> value.toDomain()?.let(SaqzResult<InvitePreview, InviteError>::Success)
        ?: invalidResponse()
}

private fun NetworkResult<InviteRedeemTransport>.toRedeemResult() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toInviteError())
    is NetworkResult.Success -> value.toDomain()?.let(SaqzResult<InviteRedeem, InviteError>::Success)
        ?: invalidResponse()
}

private fun InvitePreviewTransport.toDomain(): InvitePreview? {
    val inviter = inviterName?.takeIf(String::isNotBlank)
    val game = nextGame?.let {
        it.takeIf { game -> game.startsAt.isNotBlank() && game.venueName.isNotBlank() }
            ?.let { game -> InviteNextGame(game.startsAt, game.venueName, game.court) }
    }
    val validSlots = regularSlots.none { it.weekday.isBlank() || it.startTime.isBlank() }
    val validGame = nextGame == null || game != null
    val validPreview = inviter != null && groupName.isNotBlank() && memberCount >= 0 && validSlots && validGame
    return if (validPreview) {
        InvitePreview(
            groupName = groupName,
            inviterName = inviter,
            entryRequiresApproval = entryRequiresApproval,
            city = city,
            composition = composition,
            level = level,
            memberCount = memberCount,
            regularSlots = regularSlots.map { InviteRegularSlot(it.weekday, it.startTime) },
            expiresAt = expiresAt,
            nextGame = game,
        )
    } else {
        null
    }
}

private fun InviteRedeemTransport.toDomain(): InviteRedeem? {
    val domainStatus = status?.let { InviteRedeemStatus.valueOf(it.name) }
    val normalizedRole = role?.takeIf(String::isNotBlank)
    return if (
        domainStatus != null &&
        groupId.isNotBlank() &&
        (domainStatus == InviteRedeemStatus.JOINED) == (normalizedRole != null)
    ) {
        InviteRedeem(domainStatus, GroupId(groupId), normalizedRole)
    } else {
        null
    }
}

private fun NetworkError.toInviteError(): InviteError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "INVITE_INVALID",
        "INVITE_INVALID_OR_EXPIRED",
        -> InviteError.InvalidOrExpired
        "INVITE_EXPIRED" -> problem.expiredAt?.let(InviteError::Expired)
            ?: InviteError.DataFailure(DataError.InvalidResponse)
        "INVITE_GROUP_DELETED" -> InviteError.GroupDeleted
        "INVITE_ATTEMPT_LIMIT" -> InviteError.RateLimited(problem.retryAfterSeconds)
        "ATHLETE_LIMIT_EXCEEDED" -> InviteError.PlanLimit
        else -> InviteError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> InviteError.DataFailure(status.toDataError())
    NetworkError.Timeout -> InviteError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> InviteError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> InviteError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> InviteError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> InviteError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun invalidResponse(): SaqzResult.Failure<InviteError> =
    SaqzResult.Failure(InviteError.DataFailure(DataError.InvalidResponse))
