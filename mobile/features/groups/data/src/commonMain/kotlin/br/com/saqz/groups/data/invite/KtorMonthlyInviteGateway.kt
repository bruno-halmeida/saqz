package br.com.saqz.groups.data.invite

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInviteCopiedLink
import br.com.saqz.groups.domain.membership.MonthlyInviteError
import br.com.saqz.groups.domain.membership.MonthlyInviteGateway
import br.com.saqz.groups.domain.membership.MonthlyInviteLink
import br.com.saqz.groups.domain.membership.MonthlyInviteLinkState
import br.com.saqz.groups.domain.membership.MonthlyInvitePerson
import br.com.saqz.groups.domain.membership.MonthlyInvitePersonError
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheck
import br.com.saqz.groups.domain.membership.MonthlyInvitePhoneCheckResult
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.domain.membership.MonthlyInviteRejection
import br.com.saqz.groups.domain.membership.MonthlyInviteSent
import br.com.saqz.groups.domain.membership.MonthlyInvitee
import br.com.saqz.groups.domain.membership.MonthlyInviteeStatus
import br.com.saqz.groups.domain.membership.MonthlyInvites
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
internal data class MonthlyInviteLinkTransport(val state: String = "", val url: String? = null, val expiresAt: String? = null)

@Serializable
internal data class MonthlyInviteeTransport(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val status: String = "",
    val createdAt: String = "",
)

@Serializable
internal data class MonthlyInvitePausedTransport(val retryAt: String = "")

@Serializable
internal data class MonthlyFeeDefaultTransport(val monthlyFeeCents: Long = 0, val monthlyDueDay: Int = 0)

@Serializable
internal data class MonthlyInvitesTransport(
    val link: MonthlyInviteLinkTransport = MonthlyInviteLinkTransport(),
    val invitees: List<MonthlyInviteeTransport> = emptyList(),
    val paused: MonthlyInvitePausedTransport? = null,
    val defaultFee: MonthlyFeeDefaultTransport? = null,
)

@Serializable
internal data class MonthlyInviteCheckTransport(val result: String = "", val name: String? = null, val userId: String? = null)

@Serializable
internal data class MonthlyInvitePersonTransport(val name: String, val phone: String)

@Serializable
internal data class MonthlyInviteSendRequestTransport(val requestId: String, val people: List<MonthlyInvitePersonTransport>)

@Serializable
internal data class MonthlyInviteUrlTransport(val url: String = "", val expiresAt: String = "")

@Serializable
internal data class MonthlyInviteSentTransport(val enqueued: Int = 0, val link: MonthlyInviteUrlTransport = MonthlyInviteUrlTransport())

@Serializable
internal data class MonthlyInviteEnqueuedTransport(val enqueued: Int = 0)

class KtorMonthlyInviteGateway(
    private val network: AuthenticatedNetworkClient,
    private val json: Json = Json { explicitNulls = false },
) : MonthlyInviteGateway {
    override suspend fun list(groupId: GroupId) = retryTransport(RetrySafety.Read) {
        network.execute(HttpMethod.Get, base(groupId), MonthlyInvitesTransport.serializer())
    }.mapped { it.toDomain() }

    override suspend fun check(groupId: GroupId, phone: String) = retryTransport(RetrySafety.Read) {
        network.execute(
            HttpMethod.Get, "${base(groupId)}/check", MonthlyInviteCheckTransport.serializer(),
            NetworkRequest(query = mapOf("phone" to phone)),
        )
    }.mapped { MonthlyInvitePhoneCheckResult(enumOr(it.result, MonthlyInvitePhoneCheck.UNKNOWN), it.name, it.userId) }

    // Com requestId o backend deduplica: repetir não cria linha nem mensagem.
    override suspend fun send(groupId: GroupId, requestId: String, people: List<MonthlyInvitePerson>) =
        retryTransport(RetrySafety.IdempotentWrite) {
            network.execute(
                HttpMethod.Post, base(groupId), MonthlyInviteSentTransport.serializer(),
                NetworkRequest(
                    json.encodeToString(
                        MonthlyInviteSendRequestTransport(requestId, people.map { MonthlyInvitePersonTransport(it.name, it.phone) }),
                    ),
                ),
            )
        }.mapped { MonthlyInviteSent(it.enqueued, it.link.url, it.link.expiresAt) }

    override suspend fun copyLink(groupId: GroupId) =
        network.execute(HttpMethod.Post, "${base(groupId)}/link", MonthlyInviteUrlTransport.serializer())
            .mapped { MonthlyInviteCopiedLink(it.url, it.expiresAt) }

    override suspend fun resend(groupId: GroupId, inviteeId: String) =
        network.execute(HttpMethod.Post, "${base(groupId)}/$inviteeId/resend", MonthlyInviteEnqueuedTransport.serializer())
            .mapped { it.enqueued }

    override suspend fun resendAll(groupId: GroupId) =
        network.execute(HttpMethod.Post, "${base(groupId)}/resend-all", MonthlyInviteEnqueuedTransport.serializer())
            .mapped { it.enqueued }

    override suspend fun remove(groupId: GroupId, inviteeId: String) =
        network.executeNoContent(HttpMethod.Delete, "${base(groupId)}/$inviteeId").mapped { }

    override suspend fun invalidateLink(groupId: GroupId) =
        network.executeNoContent(HttpMethod.Post, "${base(groupId)}/link/invalidate").mapped { }

    private fun base(groupId: GroupId) = "api/groups/${groupId.value}/monthly-invites"
}

private fun <T, R> NetworkResult<T>.mapped(transform: (T) -> R): SaqzResult<R, MonthlyInviteError> = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toMonthlyInviteError())
    is NetworkResult.Success -> SaqzResult.Success(transform(value))
}

private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: fallback

private fun MonthlyInvitesTransport.toDomain() = MonthlyInvites(
    link = MonthlyInviteLink(enumOr(link.state, MonthlyInviteLinkState.UNKNOWN), link.url, link.expiresAt),
    invitees = invitees.map {
        MonthlyInvitee(it.id, it.name, it.phone, enumOr(it.status, MonthlyInviteeStatus.UNKNOWN), it.createdAt)
    },
    pausedRetryAt = paused?.retryAt?.takeIf(String::isNotBlank),
    defaultFee = defaultFee?.let { MonthlyFeeDefault(it.monthlyFeeCents, it.monthlyDueDay) },
)

private fun NetworkError.toMonthlyInviteError(): MonthlyInviteError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "ACCESS_FORBIDDEN" -> MonthlyInviteError.Forbidden
        "GROUP_NOT_FOUND" -> MonthlyInviteError.HiddenResource
        "MONTHLY_INVITEE_NOT_FOUND" -> MonthlyInviteError.InviteeNotFound
        "MONTHLY_FEE_REQUIRED" -> MonthlyInviteError.MonthlyFeeRequired
        "WHATSAPP_UNAVAILABLE" -> MonthlyInviteError.WhatsAppUnavailable
        "SUBSCRIPTION_REQUIRED" -> MonthlyInviteError.SubscriptionRequired
        "INVALID_PEOPLE" -> MonthlyInviteError.InvalidPeople(
            problem.people.orEmpty().map { MonthlyInvitePersonError(it.index, enumOr(it.reason, MonthlyInviteRejection.UNKNOWN)) },
        )
        "PLAN_LIMIT" -> MonthlyInviteError.PlanLimit(
            MonthlyInvitePlanLimit(problem.limit ?: 0, problem.current ?: 0, problem.requested ?: 0, problem.free ?: 0),
        )
        "VALIDATION_FAILED" -> if (problem.fieldErrors?.containsKey("phone") == true) {
            MonthlyInviteError.InvalidPhone
        } else {
            MonthlyInviteError.DataFailure(DataError.Unknown)
        }
        else -> MonthlyInviteError.DataFailure(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> MonthlyInviteError.DataFailure(status.toDataError())
    NetworkError.Timeout -> MonthlyInviteError.DataFailure(DataError.Timeout)
    NetworkError.Connectivity -> MonthlyInviteError.DataFailure(DataError.Connectivity)
    NetworkError.InvalidResponse -> MonthlyInviteError.DataFailure(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> MonthlyInviteError.DataFailure(DataError.PayloadTooLarge)
    NetworkError.Unavailable,
    NetworkError.Unknown,
    -> MonthlyInviteError.DataFailure(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
