package br.com.saqz.groups.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recarrega sozinha a tela que ficou em erro de rede, sem a pessoa tocar em "Tentar novamente".
 * Não há monitor de conectividade nas plataformas: a volta da rede é detectada tentando de novo,
 * a cada [intervalMillis], até [maxAttempts] vezes. [reset] zera a conta e cancela a espera
 * (a pessoa pediu a recarga, ou a tela já carregou).
 */
internal class AutoRetry(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val retry: () -> Unit,
) {
    private var job: Job? = null
    private var attempts = 0

    fun schedule() {
        if (attempts >= maxAttempts) return
        attempts++
        job?.cancel()
        job = scope.launch {
            delay(intervalMillis)
            retry()
        }
    }

    fun reset() {
        job?.cancel()
        job = null
        attempts = 0
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 5_000L
        const val DEFAULT_MAX_ATTEMPTS = 24
    }
}
