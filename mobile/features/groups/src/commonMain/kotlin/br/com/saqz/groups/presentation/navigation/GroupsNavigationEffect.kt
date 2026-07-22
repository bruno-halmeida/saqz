package br.com.saqz.groups.presentation.navigation

sealed interface GroupsNavigationEffect {
    data class DestinationChanged(
        val destination: GroupsDestination,
        val groupId: String,
    ) : GroupsNavigationEffect
}
