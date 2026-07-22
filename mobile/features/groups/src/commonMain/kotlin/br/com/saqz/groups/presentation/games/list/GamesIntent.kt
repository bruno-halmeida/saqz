package br.com.saqz.groups.presentation.games.list

import br.com.saqz.groups.domain.group.GroupRole

sealed interface GamesIntent {
    data class SelectGroup(
        val groupId: String,
        val role: GroupRole,
        val today: String,
    ) : GamesIntent

    data object Refresh : GamesIntent

    data class OpenGame(val gameId: String) : GamesIntent

    data object OpenCreate : GamesIntent
}
