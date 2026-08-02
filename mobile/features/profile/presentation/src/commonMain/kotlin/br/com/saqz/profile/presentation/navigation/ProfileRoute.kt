package br.com.saqz.profile.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProfileRoute : NavKey {
    @Serializable
    data object Edit : ProfileRoute

    @Serializable
    data class Exit(val email: String) : ProfileRoute
}
