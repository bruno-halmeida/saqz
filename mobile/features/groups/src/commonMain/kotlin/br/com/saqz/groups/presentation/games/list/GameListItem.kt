package br.com.saqz.groups.presentation.games.list

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.game.GameStatus

@Immutable
data class GameListItem(
    val id: String,
    val title: String,
    val dateText: String,
    val timeText: String,
    val venueText: String,
    val status: GameStatus,
    val availableSpots: Int,
    val waitlistCount: Int,
    val startsAt: String,
)
