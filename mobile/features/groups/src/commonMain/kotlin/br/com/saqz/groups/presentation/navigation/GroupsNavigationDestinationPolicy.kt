package br.com.saqz.groups.presentation.navigation

fun GroupsDestination.showsGroupChrome(): Boolean = when (this) {
    GroupsDestination.HOME,
    GroupsDestination.PROFILE_COMPLETION,
    GroupsDestination.PEOPLE,
    GroupsDestination.GAMES,
    GroupsDestination.GAME_DETAIL,
    GroupsDestination.FINANCE,
    GroupsDestination.OWN_CHARGES,
    GroupsDestination.NOTICES,
    GroupsDestination.MORE,
    -> true

    GroupsDestination.SETUP,
    GroupsDestination.SELECTOR,
    GroupsDestination.LOADING,
    GroupsDestination.LOAD_ERROR,
    -> false
}

fun GroupsDestination.bottomMenuDestination(): GroupsDestination = when (this) {
    GroupsDestination.GAME_DETAIL -> GroupsDestination.GAMES
    else -> this
}

fun GroupsDestination.isGroupScoped(): Boolean = when (this) {
    GroupsDestination.HOME,
    GroupsDestination.PROFILE_COMPLETION,
    GroupsDestination.PEOPLE,
    GroupsDestination.GAMES,
    GroupsDestination.GAME_DETAIL,
    GroupsDestination.FINANCE,
    GroupsDestination.OWN_CHARGES,
    GroupsDestination.NOTICES,
    GroupsDestination.MORE,
    -> true

    GroupsDestination.SETUP,
    GroupsDestination.SELECTOR,
    GroupsDestination.LOADING,
    GroupsDestination.LOAD_ERROR,
    -> false
}
