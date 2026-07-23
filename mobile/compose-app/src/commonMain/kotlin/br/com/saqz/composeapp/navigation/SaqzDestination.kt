package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface SaqzDestination : NavKey {
    @Serializable
    data object Home : SaqzDestination

    @Serializable
    data object Catalog : SaqzDestination
}
