package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import br.com.saqz.access.navigation.AccessRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * C1: the app-owned [SavedStateConfiguration] for the single acesso→shell back stack,
 * replacing `:navigation`'s product-wide one (that module dies in C3). Reflection-based
 * route serialization is unavailable on iOS, so every concrete key is registered
 * explicitly under [NavKey] — omit a leaf and restoration fails for that route.
 */
val saqzLocalNavConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(AccessRoute.Starting::class, AccessRoute.Starting.serializer())
            subclass(AccessRoute.Login::class, AccessRoute.Login.serializer())
            subclass(AccessRoute.Registration::class, AccessRoute.Registration.serializer())
            subclass(AccessRoute.PasswordReset::class, AccessRoute.PasswordReset.serializer())
            subclass(AccessRoute.Verification::class, AccessRoute.Verification.serializer())
            subclass(AccessRoute.NameCompletion::class, AccessRoute.NameCompletion.serializer())
            subclass(AccessRoute.PhoneCompletion::class, AccessRoute.PhoneCompletion.serializer())
            subclass(AccessRoute.Bootstrap::class, AccessRoute.Bootstrap.serializer())
            subclass(SaqzShellDestination::class, SaqzShellDestination.serializer())
        }
    }
}
