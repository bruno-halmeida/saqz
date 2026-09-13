package br.com.saqz.receivables.presentation

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-scoped, main-thread coordinator. Session keys are local context, never request parameters. */
class ReceivablesCoordinator(
    private val gateway: ReceivablesAvailabilityGateway,
    private val scope: CoroutineScope,
    private val sessionContext: ReceivablesSessionContext,
) {
    private val mutableState = MutableStateFlow(ReceivablesState())
    val state = mutableState.asStateFlow()
    private var sessionKey: String? = null
    private var generation = 0L
    private var request: Job? = null

    fun onSessionChanged(key: String?) {
        if (key == sessionKey) return
        invalidate()
        sessionKey = key
        mutableState.value = ReceivablesState(signedIn = key != null)
        if (key != null) refresh()
    }

    fun refresh() = query(onAvailable = null)

    /**
     * Reconsulta para NOVO cadastro, ativação, emissão ou contratação; não é autorização financeira.
     * Pagar ou renovar instrumento de ordem já emitida é acesso preservado, assim como carteira
     * e histórico: esses fluxos NÃO passam por este gate, mesmo com rollout OFF.
     */
    fun prepareNewJourney(onAvailable: () -> Unit) = query(onAvailable)

    /** Revoke discovery while backgrounded and discard pending callbacks before resuming. */
    fun onBackground() {
        invalidate()
        mutableState.value = ReceivablesState(signedIn = sessionKey != null)
    }

    private fun query(onAvailable: (() -> Unit)?) {
        if (sessionKey == null) return
        if (sessionContext.currentKey() != sessionKey) {
            onSessionChanged(null)
            return
        }
        invalidate()
        val expectedGeneration = generation
        mutableState.value = ReceivablesState(signedIn = true, loading = true)
        request = scope.launch {
            val result = gateway.get()
            if (generation != expectedGeneration) return@launch
            if (sessionContext.currentKey() != sessionKey) {
                onSessionChanged(null)
                return@launch
            }
            when (result) {
                is SaqzResult.Success -> {
                    val available = result.value.newJourneysAvailable
                    mutableState.value = ReceivablesState(signedIn = true, discoveryAvailable = available)
                    if (available) onAvailable?.invoke()
                }
                is SaqzResult.Failure -> {
                    mutableState.value = ReceivablesState(signedIn = true, error = result.error)
                }
            }
        }
    }

    private fun invalidate() {
        generation++
        request?.cancel()
        request = null
    }
}
