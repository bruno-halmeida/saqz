package br.com.saqz.composeapp.receivables

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Cross-feature session wiring belongs to the composition root, never to receivables. */
internal class ReceivablesSessionBinding(
    private val session: StateFlow<SessionAccessState>,
    private val receivables: ReceivablesCoordinator,
    scope: CoroutineScope,
) {
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            session.collect { state ->
                receivables.onSessionChanged((state as? SessionAccessState.Ready)?.session?.user?.id)
            }
        }
    }

    fun onResume() {
        receivables.onSessionChanged((session.value as? SessionAccessState.Ready)?.session?.user?.id)
        receivables.refresh()
    }

    fun onPause() = receivables.onBackground()
}
