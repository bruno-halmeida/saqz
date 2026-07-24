package br.com.saqz.composeapp.navigation

import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.SessionIntent

internal sealed interface AccessRuntimeIntent {
    data object Start : AccessRuntimeIntent

    data object Close : AccessRuntimeIntent

    data class Authentication(val intent: AuthenticationIntent) : AccessRuntimeIntent

    data class Session(val intent: SessionIntent) : AccessRuntimeIntent
}
