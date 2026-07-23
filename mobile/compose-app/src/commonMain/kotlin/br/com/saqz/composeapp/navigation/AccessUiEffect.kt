package br.com.saqz.composeapp.navigation

/** Orchestrator one-off effects (T24): only the deferred-attendance deep link remains. */
sealed interface AccessUiEffect {
    data class OpenAttendanceGame(val gameId: String) : AccessUiEffect
}
