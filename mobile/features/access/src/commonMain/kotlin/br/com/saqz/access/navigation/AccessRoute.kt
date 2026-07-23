package br.com.saqz.access.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Feature-owned, serializable Navigation Compose 3 route keys for the Access flow
 * (login, registration, password reset, verification, name completion, bootstrap).
 *
 * `:features:access` depends only on the lightweight `navigation3-runtime` artifact
 * for the [NavKey] contract; it never depends on `:navigation` or Navigation
 * Compose 3 UI (`navigation3-ui`).
 */
@Serializable
sealed interface AccessRoute : NavKey {

    @Serializable
    data object Starting : AccessRoute

    @Serializable
    data object Login : AccessRoute

    @Serializable
    data object Registration : AccessRoute

    @Serializable
    data object PasswordReset : AccessRoute

    @Serializable
    data object Verification : AccessRoute

    @Serializable
    data object NameCompletion : AccessRoute

    @Serializable
    data object Bootstrap : AccessRoute
}
