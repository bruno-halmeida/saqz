package br.com.saqz.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Host-owned route keys. `AppHome` is not feature-owned because its screen remains
 * in `:compose-app` and no dedicated Home feature module exists (see design.md,
 * "Data Models" -> "Feature-owned route keys").
 */
@Serializable
sealed interface ProductRoute : NavKey {

    @Serializable
    data object AppHome : ProductRoute
}
