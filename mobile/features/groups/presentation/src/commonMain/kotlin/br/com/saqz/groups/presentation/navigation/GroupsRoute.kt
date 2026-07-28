package br.com.saqz.groups.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface GroupsRoute : NavKey {
    @Serializable
    data object List : GroupsRoute

    @Serializable
    data object Create : GroupsRoute

    @Serializable
    data class Details(val groupId: String) : GroupsRoute

    @Serializable
    data class Edit(val groupId: String) : GroupsRoute

    @Serializable
    data class Members(val groupId: String) : GroupsRoute

    @Serializable
    data class Schedule(val groupId: String) : GroupsRoute
}
