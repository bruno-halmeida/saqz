package br.com.saqz.composeapp.receivables

import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Cross-feature session wiring belongs to the composition root, never to receivables. */
internal class ReceivablesSessionBinding(
    private val session: StateFlow<String?>,
    private val receivables: ReceivablesCoordinator,
    scope: CoroutineScope,
) {
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.collect { key ->
                receivables.onSessionChanged(key)
            }
        }
    }

    fun onResume() {
        receivables.onSessionChanged(session.value)
        receivables.refresh()
    }

    fun onPause() = receivables.onBackground()
}
