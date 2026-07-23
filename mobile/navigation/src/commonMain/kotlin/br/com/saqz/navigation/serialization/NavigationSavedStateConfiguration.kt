package br.com.saqz.navigation.serialization

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.navigation.ProductRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.polymorphic

/**
 * MODNAV-05 / RESTORE-01: the one [SavedStateConfiguration] consumed by every
 * product stack and snapshot serializer. It registers every concrete [NavKey]
 * leaf across Access, Groups, Finance, and host-owned Product routes under an
 * explicit polymorphic [SerializersModule] so restoration never relies on JVM
 * reflection (required on iOS/non-JVM targets).
 *
 * Omitting any leaf here fails the exhaustive round-trip test for that key.
 */
val navigationSavedStateConfiguration: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            registerAccessRoutes()
            registerGroupsRoutes()
            registerFinanceRoutes()
            registerProductRoutes()
        }
    }
}

private fun PolymorphicModuleBuilder<NavKey>.registerAccessRoutes() {
    subclass(AccessRoute.Starting::class, AccessRoute.Starting.serializer())
    subclass(AccessRoute.Login::class, AccessRoute.Login.serializer())
    subclass(AccessRoute.Registration::class, AccessRoute.Registration.serializer())
    subclass(AccessRoute.PasswordReset::class, AccessRoute.PasswordReset.serializer())
    subclass(AccessRoute.Verification::class, AccessRoute.Verification.serializer())
    subclass(AccessRoute.NameCompletion::class, AccessRoute.NameCompletion.serializer())
    subclass(AccessRoute.Bootstrap::class, AccessRoute.Bootstrap.serializer())
}

private fun PolymorphicModuleBuilder<NavKey>.registerGroupsRoutes() {
    subclass(GroupsRoute.Setup::class, GroupsRoute.Setup.serializer())
    subclass(GroupsRoute.Selector::class, GroupsRoute.Selector.serializer())
    subclass(GroupsRoute.Loading::class, GroupsRoute.Loading.serializer())
    subclass(GroupsRoute.LoadError::class, GroupsRoute.LoadError.serializer())
    subclass(GroupsRoute.GroupHome::class, GroupsRoute.GroupHome.serializer())
    subclass(GroupsRoute.ProfileCompletion::class, GroupsRoute.ProfileCompletion.serializer())
    subclass(GroupsRoute.People::class, GroupsRoute.People.serializer())
    subclass(GroupsRoute.Games::class, GroupsRoute.Games.serializer())
    subclass(GroupsRoute.GameDetail::class, GroupsRoute.GameDetail.serializer())
    subclass(GroupsRoute.Notices::class, GroupsRoute.Notices.serializer())
    subclass(GroupsRoute.More::class, GroupsRoute.More.serializer())
    subclass(GroupsRoute.Settings::class, GroupsRoute.Settings.serializer())
    subclass(GroupsRoute.Memberships::class, GroupsRoute.Memberships.serializer())
    subclass(GroupsRoute.Invite::class, GroupsRoute.Invite.serializer())
    subclass(GroupsRoute.CreateGroup::class, GroupsRoute.CreateGroup.serializer())
}

private fun PolymorphicModuleBuilder<NavKey>.registerFinanceRoutes() {
    subclass(FinanceRoute.Finance::class, FinanceRoute.Finance.serializer())
    subclass(FinanceRoute.OwnCharges::class, FinanceRoute.OwnCharges.serializer())
}

private fun PolymorphicModuleBuilder<NavKey>.registerProductRoutes() {
    subclass(ProductRoute.AppHome::class, ProductRoute.AppHome.serializer())
}
