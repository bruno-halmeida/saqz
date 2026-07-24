package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/**
 * MODNAV-04: the app-local Home/Catalog graph renders through Navigation Compose 3 with a
 * back stack owned in `:compose-app`. Its routes ([SaqzDestination]) live here, not in
 * `:navigation`, so they need their own explicit polymorphic [SavedStateConfiguration]
 * (design.md, "App-local Home/Catalog migration"): reflection-based route serialization is
 * unavailable on iOS, so both concrete keys are registered explicitly under [NavKey].
 */
val saqzLocalNavConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SaqzDestination.Home::class, SaqzDestination.Home.serializer())
            subclass(SaqzDestination.Catalog::class, SaqzDestination.Catalog.serializer())
        }
    }
}
