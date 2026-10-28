package br.com.saqz.access.presentation.appupdate

import br.com.saqz.access.domain.appupdate.AppUpdateGateway
import br.com.saqz.access.domain.appupdate.AppUpdatePrompt
import br.com.saqz.access.domain.appupdate.decideAppUpdate
import br.com.saqz.access.domain.port.AppUpdateMemoryPort
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * O pop-up de atualização (DEB-117, FLG-007), do tamanho do app: vale antes do login e por cima da splash.
 *
 * [check] roda ao abrir e a cada volta ao primeiro plano, nunca no meio do uso. Cada checagem decide na
 * hora com a última mínima guardada no aparelho (sem rede ela continua valendo), busca as versões e,
 * com resposta, guarda a mínima nova (ou apaga) e decide de novo. Checagem nova descarta a anterior
 * (guarda de geração); falha de rede deixa valer a decisão guardada.
 */
class AppUpdateCheck(
    private val gateway: AppUpdateGateway,
    private val memory: AppUpdateMemoryPort,
    private val installedVersion: String,
    private val scope: CoroutineScope,
) {
    private val current = MutableStateFlow<AppUpdatePrompt>(AppUpdatePrompt.None)
    private var generation = 0
    private var running: Job? = null

    val prompt: StateFlow<AppUpdatePrompt> = current.asStateFlow()

    fun check() {
        val mine = ++generation
        running?.cancel()
        running = scope.launch {
            // A guardada só decide a obrigatória: a recomendada visível fica até a rede responder.
            val stored = decide(readMinimum(), recommended = null)
            if (mine != generation) return@launch
            if (stored == AppUpdatePrompt.Required) {
                current.value = stored
            } else if (current.value == AppUpdatePrompt.Required) {
                current.value = AppUpdatePrompt.None
            }

            val versions = (gateway.versions() as? SaqzResult.Success)?.value ?: return@launch
            if (mine != generation) return@launch
            writeMinimum(versions.minimum)
            val fresh = decide(versions.minimum, versions.recommended)
            if (mine == generation) current.value = fresh
        }
    }

    /** "Agora não", X, toque fora, voltar ou "Atualizar" na recomendada: não volta para a mesma versão. */
    fun dismissRecommended() {
        val shown = current.value as? AppUpdatePrompt.Recommended ?: return
        current.value = AppUpdatePrompt.None
        memory.markRecommendedUpdateDismissed(shown.version) {}
    }

    private suspend fun decide(minimum: String?, recommended: String?): AppUpdatePrompt {
        val decided = decideAppUpdate(installedVersion, minimum, recommended) { false }
        if (decided !is AppUpdatePrompt.Recommended) return decided
        return if (isDismissed(decided.version)) AppUpdatePrompt.None else decided
    }

    private suspend fun readMinimum(): String? =
        suspendCancellableCoroutine { continuation -> memory.readUpdateMinimum { continuation.resume(it) } }

    private suspend fun writeMinimum(value: String?) {
        suspendCancellableCoroutine { continuation -> memory.writeUpdateMinimum(value) { continuation.resume(it) } }
    }

    private suspend fun isDismissed(version: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            memory.isRecommendedUpdateDismissed(version) { continuation.resume(it) }
        }
}
