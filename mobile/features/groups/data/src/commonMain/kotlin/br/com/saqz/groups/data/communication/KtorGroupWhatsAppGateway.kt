package br.com.saqz.groups.data.communication

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.communication.CommunicationError
import br.com.saqz.groups.domain.communication.GroupWhatsAppBinding
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * GET devolve `{bound:false}` ou `{bound:true, groupJid, groupName, status}`; PUT/PATCH
 * devolvem o mesmo shape do GET com vínculo (`bound` é implícito true no corpo deles).
 */
@Serializable
private data class WhatsAppBindingDto(
    val bound: Boolean = true,
    val groupJid: String? = null,
    val groupName: String? = null,
    val status: String? = null,
) {
    fun domain(): GroupWhatsAppBinding? {
        if (!bound) return GroupWhatsAppBinding(bound = false)
        val jid = groupJid?.takeIf(String::isNotBlank)
        val name = groupName?.takeIf(String::isNotBlank)
        val parsed = GroupWhatsAppStatus.entries.find { it.name == status }?.takeIf { it != GroupWhatsAppStatus.NONE }
        return if (jid != null && name != null && parsed != null) {
            GroupWhatsAppBinding(bound = true, groupJid = jid, groupName = name, status = parsed)
        } else {
            null
        }
    }
}

@Serializable private data class InviteLinkDto(val inviteLink: String)

@Serializable private data class EnabledDto(val enabled: Boolean)

class KtorGroupWhatsAppGateway(private val network: AuthenticatedNetworkClient) : GroupWhatsAppGateway {
    override suspend fun binding(groupId: GroupId) = network.execute(
        HttpMethod.Get, "api/groups/${groupId.value}/whatsapp-binding", WhatsAppBindingDto.serializer(),
    ).whatsAppBindingResult()

    override suspend fun link(groupId: GroupId, inviteLink: String) = network.execute(
        HttpMethod.Put, "api/groups/${groupId.value}/whatsapp-binding", WhatsAppBindingDto.serializer(),
        NetworkRequest(Json.encodeToString(InviteLinkDto(inviteLink))),
    ).whatsAppBindingResult()

    override suspend fun setEnabled(groupId: GroupId, enabled: Boolean) = network.execute(
        HttpMethod.Patch, "api/groups/${groupId.value}/whatsapp-binding", WhatsAppBindingDto.serializer(),
        NetworkRequest(Json.encodeToString(EnabledDto(enabled))),
    ).whatsAppBindingResult()
}

private fun NetworkResult<WhatsAppBindingDto>.whatsAppBindingResult(): SaqzResult<GroupWhatsAppBinding, CommunicationError> =
    when (this) {
        is NetworkResult.Success -> value.domain()?.let { SaqzResult.Success(it) }
            ?: SaqzResult.Failure(CommunicationError(DataError.InvalidResponse))
        is NetworkResult.Failure -> SaqzResult.Failure(CommunicationError(error.toBindingError()))
    }

private fun NetworkError.toBindingError(): DataError = when (this) {
    is NetworkError.ApiProblemError -> if (problem.status == 422) {
        bindingValidation(problem.fieldErrors)
    } else {
        problem.status.toBindingDataError()
    }
    is NetworkError.HttpStatus -> if (status == 422) bindingValidation(null) else status.toBindingDataError()
    NetworkError.Timeout -> DataError.Timeout
    NetworkError.Connectivity -> DataError.Connectivity
    NetworkError.InvalidResponse -> DataError.InvalidResponse
    NetworkError.PayloadTooLarge -> DataError.PayloadTooLarge
    NetworkError.Unknown, NetworkError.Unavailable -> DataError.Unknown
}

private fun bindingValidation(fieldErrors: Map<String, List<String>>?) =
    DataError.Validation(ValidationDetails(globalMessages = emptyList(), fieldMessages = fieldErrors.orEmpty()))

private fun Int.toBindingDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}
