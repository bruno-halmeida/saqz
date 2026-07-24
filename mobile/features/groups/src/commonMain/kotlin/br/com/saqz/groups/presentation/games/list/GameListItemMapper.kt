package br.com.saqz.groups.presentation.games.list

import br.com.saqz.core.common.formatting.SaqzDateTimeFormatter
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.presentation.games.deviceLocalDate
import br.com.saqz.groups.presentation.games.scheduleText

internal fun Game.toGameListItem(formatter: SaqzDateTimeFormatter): GameListItem = GameListItem(
    id = id,
    title = title,
    scheduleText = scheduleText(formatter),
    localDateIso = deviceLocalDate(formatter),
    venueText = venue.name,
    status = status,
    availableSpots = availableSpots,
    waitlistCount = waitlistCount,
    startsAt = startsAt,
)
