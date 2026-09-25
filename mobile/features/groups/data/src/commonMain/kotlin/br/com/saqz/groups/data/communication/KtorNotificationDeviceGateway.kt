package br.com.saqz.groups.data.communication

import br.com.saqz.groups.domain.communication.NotificationDevice
import br.com.saqz.groups.domain.communication.NotificationDeviceGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkRequest
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** `liveActivityStartToken` nulo sai do JSON (o `Json` padrão não codifica default): o backend lê ausência como "limpar". */
@Serializable private data class NotificationDeviceDto(val token: String, val platform: String, val liveActivityStartToken: String? = null)
class KtorNotificationDeviceGateway(private val network: AuthenticatedNetworkClient) : NotificationDeviceGateway {
    override suspend fun register(device: NotificationDevice) = network.executeNoContent(
        HttpMethod.Put, "api/me/notification-devices/${device.installationId}",
        NetworkRequest(Json.encodeToString(NotificationDeviceDto(device.token, device.platform, device.liveActivityStartToken))),
    ).communicationResult { Unit }
    override suspend fun unregister(installationId: String) = network.executeNoContent(
        HttpMethod.Delete, "api/me/notification-devices/$installationId",
    ).communicationResult { Unit }
}
