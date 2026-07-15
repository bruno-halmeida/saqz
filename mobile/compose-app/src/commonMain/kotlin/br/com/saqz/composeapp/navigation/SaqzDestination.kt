package br.com.saqz.composeapp.navigation

import kotlinx.serialization.Serializable

sealed interface SaqzDestination {
    @Serializable
    data object Home : SaqzDestination

    @Serializable
    data object Catalog : SaqzDestination
}
