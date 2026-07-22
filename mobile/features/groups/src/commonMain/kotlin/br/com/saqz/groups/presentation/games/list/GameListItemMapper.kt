package br.com.saqz.groups.presentation.games.list

import br.com.saqz.groups.domain.game.Game

internal fun Game.toGameListItem(): GameListItem = GameListItem(
    id = id,
    title = title,
    dateText = localDate.toPtBrDate(),
    timeText = localTime.take(5),
    venueText = venue.name,
    status = status,
    availableSpots = availableSpots,
    waitlistCount = waitlistCount,
    startsAt = startsAt,
)

internal fun GameListItem.isoLocalDate(): String = dateText
    .split('/')
    .let { (day, month, year) -> "$year-$month-$day" }

private fun String.toPtBrDate(): String = split('-')
    .let { (year, month, day) -> "$day/$month/$year" }
