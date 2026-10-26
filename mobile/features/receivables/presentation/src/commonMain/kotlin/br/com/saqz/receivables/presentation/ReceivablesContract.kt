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
    val launchEnabled: Boolean = false,
) {
    /**
     * Só as entradas do gestor (cadastro, ativação). O atleta não passa por aqui: a disponibilidade é por pessoa e, no
     * piloto, só libera os gestores; quem decide se ele paga é o grupo (`receivablesActive`) e o `active` do `/me`.
     */
    val configurationEntryAvailable get() = signedIn && (discoveryAvailable || hasAccount)

    // Maintenance is independent of rollout and network failures; resource authorization is server-side.
    val maintenanceAvailable: Boolean get() = signedIn
}

/** App-owned destinations for the next delivery. Null means no implemented destination: render no entry. */
data class ReceivablesEntryCallbacks(
    val onNewJourney: (() -> Unit)? = null,
    val onMaintenance: ((resourceId: String) -> Unit)? = null,
)
