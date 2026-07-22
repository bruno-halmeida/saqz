package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryAction
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.domain.game.WeeklySlot

internal fun GameEditorForm.toGameWriteCommand(commandKey: String? = null): GameWriteCommand = GameWriteCommand(
    commandKey,
    title,
    venue,
    localDate,
    localTime,
    zoneId,
    startsAt,
    durationMinutes.toIntOrNull(),
    capacity.toIntOrNull(),
    confirmationDeadline,
    parseBrlToCents(gameFeeBrl),
    false,
    notes.trim().ifBlank { null },
)

internal fun GameEditorForm.newWeeklySlot(commandKey: String): WeeklySlot = WeeklySlot(
    slotKey = commandKey,
    weekday = Weekday.Monday,
    localTime = "19:00:00",
    durationMinutes = durationMinutes.toIntOrNull() ?: 90,
    venue = venue ?: GameVenue(null, "", ""),
    capacity = capacity.toIntOrNull() ?: 12,
    confirmationLeadMinutes = 180,
    gameFeeCents = parseBrlToCents(gameFeeBrl),
    title = title,
)

internal fun GameEditorDraft.toSeriesWriteCommand(): WeeklySeriesWriteCommand = WeeklySeriesWriteCommand(
    seriesId ?: commandKey,
    commandKey,
    form.zoneId,
    form.localDate,
    form.localEndDate.ifBlank { null },
    form.slots,
)

internal fun GameEditorDraft.toBoundaryCommand(series: VersionedSeries): SeriesBoundaryCommand =
    SeriesBoundaryCommand(
        requestId = commandKey,
        scope = requireNotNull(scope),
        action = SeriesBoundaryAction.Edit,
        gameId = gameId,
        boundary = form.localDate,
        currentRevisionId = series.series.revisionId,
        successor = if (scope == SeriesBoundaryScope.ThisAndFuture) {
            toSeriesWriteCommand()
        } else {
            null
        },
        replacement = if (scope == SeriesBoundaryScope.OnlyThis) {
            form.toGameWriteCommand()
        } else {
            null
        },
    )
