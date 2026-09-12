package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.GroupRole

sealed interface GroupOnboarding {
    data object CreateGame : GroupOnboarding
    data class InviteAthletes(val gameId: String) : GroupOnboarding
    data class ReviewFinances(val gameId: String) : GroupOnboarding
}

internal fun groupOnboarding(role: GroupRole, games: List<Game>, nextGameId: String?): GroupOnboarding? {
    if (role == GroupRole.ATHLETE) return null
    val completed = games.filter { it.status == GameStatus.Completed }
    return when {
        completed.size > 1 -> null
        completed.size == 1 -> GroupOnboarding.ReviewFinances(completed.single().id)
        games.isEmpty() -> GroupOnboarding.CreateGame
        nextGameId != null -> GroupOnboarding.InviteAthletes(nextGameId)
        else -> null
    }
}
