package br.com.saqz.receivables.presentation

import br.com.saqz.receivables.domain.ReceivablesError

/** Never persist this snapshot. Only [ReceivablesCoordinator.prepareNewJourney] may start a new journey. */
data class ReceivablesState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val discoveryAvailable: Boolean = false,
    val error: ReceivablesError? = null,
    val hasAccount: Boolean = false,
    val accountLookupFailed: Boolean = false,
    /**
     * A trava do servidor (`backendEnabled`). O atleta paga em grupo com recebimentos ativos mesmo fora do rollout,
     * que é do gestor; quem decide se o grupo está ativo é o próprio grupo (`receivablesActive`) e o `/me`.
     */
    val launchEnabled: Boolean = false,
) {
    val configurationEntryAvailable get() = signedIn && (discoveryAvailable || hasAccount)
    val paymentEntryAvailable get() = signedIn && launchEnabled

    // Maintenance is independent of rollout and network failures; resource authorization is server-side.
    val maintenanceAvailable: Boolean get() = signedIn
}

/** App-owned destinations for the next delivery. Null means no implemented destination: render no entry. */
data class ReceivablesEntryCallbacks(
    val onNewJourney: (() -> Unit)? = null,
    val onMaintenance: ((resourceId: String) -> Unit)? = null,
)
