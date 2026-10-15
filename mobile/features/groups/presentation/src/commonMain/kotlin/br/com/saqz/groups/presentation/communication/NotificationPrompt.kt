package br.com.saqz.groups.presentation.communication

import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.domain.communication.NotificationPermission
import br.com.saqz.groups.domain.communication.NotificationPromptMemory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Quem vai ler a folha: muda só o corpo do texto. */
enum class NotificationPromptAudience { Athlete, Organizer }

/** Os ViewModels só sabem dizer "chegou o momento certo"; a política mora em [NotificationPrompt]. */
fun interface NotificationPromptOffer {
    fun offer(audience: NotificationPromptAudience)
}

/**
 * Pré-pedido de aviso: uma folha do app que explica antes do pedido do sistema. O Android 13+ e o iPhone
 * só mostram o pedido do sistema uma vez, então ele só sai depois do "Ligar avisos" (VUL: recusa no
 * impulso apagava o lembrete de jogo, o principal valor do app para o atleta).
 *
 * Política: só em [NotificationPermission.NotAsked]; "Agora não" guarda a escolha no aparelho e a folha
 * volta no próximo momento certo, no máximo mais [MaxReturns] vezes, com [MinIntervalMillis] entre elas.
 * Recusa no sistema vira [permission] = Denied, e a central de avisos oferece o caminho dos ajustes.
 *
 * [onGranted] religa o registro do aparelho (o `NotificationSessionBinding` não pede mais nada sozinho).
 */
class NotificationPrompt(
    private val port: NativeNotificationPort,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val onGranted: () -> Unit = {},
) : NotificationPromptOffer {
    private val open = MutableStateFlow<NotificationPromptAudience?>(null)
    private val current = MutableStateFlow(NotificationPermission.Unsupported)

    /** A folha aberta e para quem; nulo = fechada. */
    val audience: StateFlow<NotificationPromptAudience?> = open.asStateFlow()

    /** Último estado lido; a central de avisos mostra o atalho dos ajustes quando é [NotificationPermission.Denied]. */
    val permission: StateFlow<NotificationPermission> = current.asStateFlow()

    override fun offer(audience: NotificationPromptAudience) {
        if (open.value != null) return
        scope.launch {
            val state = readPermission()
            current.value = state
            if (state != NotificationPermission.NotAsked) return@launch
            val memory = readMemory()
            if (canShowAgain(memory)) open.compareAndSet(null, audience)
        }
    }

    /** "Ligar avisos": fecha a folha e só então dispara o pedido do sistema. */
    fun enable() {
        if (open.value == null) return
        open.value = null
        scope.launch {
            val result = suspendCancellableCoroutine { continuation ->
                port.requestPermission { continuation.resume(it) }
            }
            current.value = result
            if (result == NotificationPermission.Granted) onGranted()
        }
    }

    /** "Agora não" (ou fechar a folha): conta uma recusa e guarda o horário. */
    fun dismiss() {
        if (open.value == null) return
        open.value = null
        scope.launch {
            val memory = readMemory()
            port.savePromptMemory(NotificationPromptMemory(memory.declined + 1, nowMillis()))
        }
    }

    /** Relê o estado do sistema (a pessoa pode ter mexido nos ajustes); chamada ao abrir a central de avisos. */
    fun refreshPermission() {
        scope.launch {
            val state = readPermission()
            current.value = state
            if (state == NotificationPermission.Granted) onGranted()
        }
    }

    fun openSettings() = port.openSettings()

    private fun canShowAgain(memory: NotificationPromptMemory): Boolean = when {
        memory.declined == 0 -> true
        memory.declined > MaxReturns -> false
        else -> nowMillis() - memory.lastDeclinedAtMillis >= MinIntervalMillis
    }

    private suspend fun readPermission(): NotificationPermission =
        suspendCancellableCoroutine { continuation -> port.permission { continuation.resume(it) } }

    private suspend fun readMemory(): NotificationPromptMemory =
        suspendCancellableCoroutine { continuation -> port.promptMemory { continuation.resume(it) } }

    companion object {
        const val MaxReturns = 2
        const val MinIntervalMillis = 3L * 24 * 60 * 60 * 1000
    }
}
