package br.com.saqz.groups.domain.communication

import br.com.saqz.domain.SaqzResult

/** [liveActivityStartToken]: token de push-to-start da Live Activity (iOS 17.2+); nulo no Android e sem Live Activity. */
data class NotificationDevice(
    val installationId: String,
    val token: String,
    val platform: String,
    val liveActivityStartToken: String? = null,
)
fun interface NotificationSubscription { fun cancel() }
interface NativeNotificationPort {
    fun device(done: (NotificationDevice?) -> Unit)
    fun clear(done: (Boolean) -> Unit)
    /** Descarta as notificações já entregues: a conta que sai não deixa botão de presença para a próxima. */
    fun dismissAll()
    /**
     * A pessoa respondeu presença pelo app ou pelo link: some a janela das 24 h daquele jogo, que
     * não tem mais o que pedir. Vazio por padrão (fakes e plataformas sem janela).
     */
    fun dismissAttendance(gameId: String) {}
    fun observe(changed: () -> Unit): NotificationSubscription
}
interface NotificationDeviceGateway {
    suspend fun register(device: NotificationDevice): SaqzResult<Unit, CommunicationError>
    suspend fun unregister(installationId: String): SaqzResult<Unit, CommunicationError>
}
