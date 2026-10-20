package br.com.saqz.groups.domain.membership

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult

/** Estado do link de mensalistas (CONV-011). */
enum class MonthlyInviteLinkState { NONE, ACTIVE, EXPIRED, INVALIDATED, UNKNOWN }

/** Estado de uma linha de Convidados (CONV-012). [EXPIRED] = enviada e o link venceu. */
enum class MonthlyInviteeStatus { QUEUED, WAITING, FAILED, NO_WHATSAPP, PAUSED, EXPIRED, UNKNOWN }

data class MonthlyInviteLink(val state: MonthlyInviteLinkState, val url: String?, val expiresAt: String?)

data class MonthlyInvitee(
    val id: String,
    val name: String,
    /** Os 11 dígitos, sem +55. */
    val phone: String,
    val status: MonthlyInviteeStatus,
    val createdAt: String,
)

data class MonthlyFeeDefault(val monthlyFeeCents: Long, val monthlyDueDay: Int)

data class MonthlyInvites(
    val link: MonthlyInviteLink,
    val invitees: List<MonthlyInvitee>,
    /** Instante da próxima tentativa quando a fila está pausada. */
    val pausedRetryAt: String?,
    val defaultFee: MonthlyFeeDefault?,
)

enum class MonthlyInvitePhoneCheck { FREE, ALREADY_MENSALISTA, ALREADY_AVULSO, ALREADY_INVITED, UNKNOWN }

data class MonthlyInvitePhoneCheckResult(val result: MonthlyInvitePhoneCheck, val name: String?, val userId: String?)

data class MonthlyInvitePerson(val name: String, val phone: String)

data class MonthlyInviteSent(val enqueued: Int, val url: String, val expiresAt: String)

data class MonthlyInviteCopiedLink(val url: String, val expiresAt: String)

enum class MonthlyInviteRejection {
    INVALID_NAME,
    INVALID_PHONE,
    DUPLICATE_IN_BATCH,
    ALREADY_INVITED,
    ALREADY_MENSALISTA,
    ALREADY_AVULSO,
    UNKNOWN,
}

data class MonthlyInvitePersonError(val index: Int, val reason: MonthlyInviteRejection)

data class MonthlyInvitePlanLimit(val limit: Int, val current: Int, val requested: Int, val free: Int)

sealed interface MonthlyInviteError : SaqzError {
    data object HiddenResource : MonthlyInviteError
    data object Forbidden : MonthlyInviteError
    data object InviteeNotFound : MonthlyInviteError
    data object MonthlyFeeRequired : MonthlyInviteError
    data object WhatsAppUnavailable : MonthlyInviteError
    data object InvalidPhone : MonthlyInviteError
    data object SubscriptionRequired : MonthlyInviteError
    data class InvalidPeople(val errors: List<MonthlyInvitePersonError>) : MonthlyInviteError
    data class PlanLimit(val limit: MonthlyInvitePlanLimit) : MonthlyInviteError
    data class DataFailure(val error: DataError) : MonthlyInviteError
}

/** Convite de mensalistas pelo WhatsApp (CONV-010 a 012), `/api/groups/{id}/monthly-invites`. */
interface MonthlyInviteGateway {
    suspend fun list(groupId: GroupId): SaqzResult<MonthlyInvites, MonthlyInviteError>

    suspend fun check(groupId: GroupId, phone: String): SaqzResult<MonthlyInvitePhoneCheckResult, MonthlyInviteError>

    suspend fun send(
        groupId: GroupId,
        requestId: String,
        people: List<MonthlyInvitePerson>,
    ): SaqzResult<MonthlyInviteSent, MonthlyInviteError>

    suspend fun copyLink(groupId: GroupId): SaqzResult<MonthlyInviteCopiedLink, MonthlyInviteError>

    suspend fun resend(groupId: GroupId, inviteeId: String): SaqzResult<Int, MonthlyInviteError>

    suspend fun resendAll(groupId: GroupId): SaqzResult<Int, MonthlyInviteError>

    suspend fun remove(groupId: GroupId, inviteeId: String): SaqzResult<Unit, MonthlyInviteError>

    suspend fun invalidateLink(groupId: GroupId): SaqzResult<Unit, MonthlyInviteError>
}
