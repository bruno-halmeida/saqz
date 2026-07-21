package br.com.saqz.groups.presentation.games.list

sealed interface GamesEffect {
    data class OpenGame(val groupId: String, val gameId: String) : GamesEffect

    data class OpenCreate(val groupId: String) : GamesEffect
}
