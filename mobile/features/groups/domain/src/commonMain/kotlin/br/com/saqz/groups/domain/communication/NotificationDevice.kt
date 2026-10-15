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

/** Estado da permissão de aviso no aparelho. [Unsupported]: plataforma sem aviso (web); a folha e o atalho não aparecem. */
enum class NotificationPermission { NotAsked, Granted, Denied, Unsupported }

/** Quantas vezes a pessoa disse "Agora não" na folha do app e quando foi a última (ms desde 1970). */
data class NotificationPromptMemory(val declined: Int = 0, val lastDeclinedAtMillis: Long = 0)

interface NativeNotificationPort {
    /** Registro do aparelho: devolve nulo sem permissão. Nunca abre o pedido do sistema; quem pede é [requestPermission]. */
    fun device(done: (NotificationDevice?) -> Unit)
    fun permission(done: (NotificationPermission) -> Unit) = done(NotificationPermission.Unsupported)
    /** Abre o pedido do sistema (só vale em [NotificationPermission.NotAsked]); devolve o estado depois da resposta. */
    fun requestPermission(done: (NotificationPermission) -> Unit) = done(NotificationPermission.Unsupported)
    /** Abre os ajustes de aviso do app no sistema, para quem recusou. */
    fun openSettings() {}
    fun promptMemory(done: (NotificationPromptMemory) -> Unit) = done(NotificationPromptMemory())
    fun savePromptMemory(memory: NotificationPromptMemory) {}
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
