package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.SessionIntent

/**
 * Orchestrator command surface (T24, sliced for C1): auth/session pass-throughs plus
 * ConfirmLogout, the one cross-route event the empty shell can still raise. Selection,
 * administration and deferred invite/attendance commands are gone with their owners.
 */
sealed interface AccessIntent {
    data class Authentication(val intent: AuthenticationIntent) : AccessIntent

    data class Session(val intent: SessionIntent) : AccessIntent

    data object ConfirmLogout : AccessIntent
}
