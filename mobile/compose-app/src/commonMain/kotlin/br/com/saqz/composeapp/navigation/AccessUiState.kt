package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Immutable
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.SessionAccessState

/**
 * Core orchestrator projection (T24, sliced for C1): auth/authentication/session only.
 * Selection/administration projections are gone with the screens that owned them.
 */
@Immutable
internal data class AccessUiState(
    val authObserved: Boolean = false,
    val authentication: AuthenticationState = AuthenticationState(),
    val session: SessionAccessState = SessionAccessState.SignedOut,
)
