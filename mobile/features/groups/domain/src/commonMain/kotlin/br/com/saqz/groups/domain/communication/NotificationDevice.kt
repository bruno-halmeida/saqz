package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.SaqzResult

data class NotificationDevice(val installationId: String, val token: String, val platform: String)
fun interface NotificationSubscription { fun cancel() }
interface NativeNotificationPort {
    fun device(done: (NotificationDevice?) -> Unit)
    fun clear(done: (Boolean) -> Unit)
    /** Descarta as notificações já entregues: a conta que sai não deixa botão de presença para a próxima. */
    fun dismissAll()
    fun observe(changed: () -> Unit): NotificationSubscription
}
interface NotificationDeviceGateway {
    suspend fun register(device: NotificationDevice): SaqzResult<Unit, CommunicationError>
    suspend fun unregister(installationId: String): SaqzResult<Unit, CommunicationError>
}
