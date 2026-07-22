package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.core.common.formatting.formatBrlPlain

internal fun GameEditorInput.toGameEditorDraft(commandKey: String): GameEditorDraft {
    val game = existing?.game
    val form = if (game == null) {
        GameEditorForm(
            title = defaults.title,
            venue = defaults.venue,
            zoneId = defaults.zoneId,
            durationMinutes = defaults.durationMinutes?.toString().orEmpty(),
            capacity = defaults.capacity?.toString().orEmpty(),
            gameFeeBrl = defaults.gameFeeCents?.let(::formatBrlPlain).orEmpty(),
        )
    } else {
        GameEditorForm(
            title = game.title,
            venue = game.venue,
            localDate = game.localDate,
            localTime = game.localTime,
            zoneId = game.zoneId,
            startsAt = game.startsAt,
            durationMinutes = game.durationMinutes.toString(),
            capacity = game.capacity.toString(),
            confirmationDeadline = game.confirmationDeadline,
            gameFeeBrl = game.gameFeeCents?.let(::formatBrlPlain).orEmpty(),
            notes = game.notes.orEmpty(),
        )
    }
    return GameEditorDraft(
        groupId = groupId,
        gameId = game?.id,
        seriesId = series?.series?.id,
        commandKey = commandKey,
        etag = existing?.etag ?: series?.etag,
        mode = if (series == null) GameEditorMode.ONE_TIME else GameEditorMode.WEEKLY,
        form = form,
    )
}
