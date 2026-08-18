package br.com.saqz.profile.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface ProfileRoute : NavKey {
    @Serializable
    data object Edit : ProfileRoute

    @Serializable
    data object Exit : ProfileRoute
}
