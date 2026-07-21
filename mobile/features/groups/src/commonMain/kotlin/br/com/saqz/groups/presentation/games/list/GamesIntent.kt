package br.com.saqz.groups.presentation.games.list

import br.com.saqz.groups.data.GroupRoleDto

sealed interface GamesIntent {
    data class SelectGroup(
        val groupId: String,
        val role: GroupRoleDto,
        val today: String,
    ) : GamesIntent

    data object Refresh : GamesIntent

    data class OpenGame(val gameId: String) : GamesIntent

    data object OpenCreate : GamesIntent
}
