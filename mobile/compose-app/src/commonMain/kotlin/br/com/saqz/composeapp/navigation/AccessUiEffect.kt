package br.com.saqz.composeapp.navigation

sealed interface AccessUiEffect {
    data class RequestShare(val text: String) : AccessUiEffect

    data class OpenAttendanceGame(val gameId: String) : AccessUiEffect
}
