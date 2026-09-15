package br.com.saqz.groups.data.communication

import br.com.saqz.domain.GroupId
import br.com.saqz.groups.domain.communication.ChargeReminderGateway
import br.com.saqz.groups.domain.communication.ChargeReminderReceipt
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable private data class ChargeReminderRequestDto(val requestId: String, val chargeIds: List<String>)
@Serializable private data class ChargeReminderReceiptDto(val notificationCount: Int)
class KtorChargeReminderGateway(private val network: AuthenticatedNetworkClient) : ChargeReminderGateway {
    override suspend fun send(groupId: GroupId, requestId: String, chargeIds: List<String>) = network.execute(
        HttpMethod.Post, "api/groups/${groupId.value}/charges/notify", ChargeReminderReceiptDto.serializer(),
        NetworkRequest(Json.encodeToString(ChargeReminderRequestDto(requestId, chargeIds))),
    ).communicationResult { if (it.notificationCount == chargeIds.size) ChargeReminderReceipt(it.notificationCount) else null }
}
