package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.groups.data.game.GameVenueDto
import br.com.saqz.groups.data.game.GameWriteCommand
import br.com.saqz.groups.data.game.SeriesBoundaryCommand
import br.com.saqz.groups.data.game.SeriesBoundaryScopeDto
import br.com.saqz.groups.data.game.SeriesBoundaryActionDto
import br.com.saqz.groups.data.game.VersionedSeriesDto
import br.com.saqz.groups.data.game.WeekdayDto
import br.com.saqz.groups.data.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.data.game.WeeklySlotDto

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

internal fun GameEditorForm.newWeeklySlot(commandKey: String): WeeklySlotDto = WeeklySlotDto(
    slotKey = commandKey,
    weekday = WeekdayDto.MONDAY,
    localTime = "19:00:00",
    durationMinutes = durationMinutes.toIntOrNull() ?: 90,
    venue = venue ?: GameVenueDto(null, "", ""),
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

internal fun GameEditorDraft.toBoundaryCommand(series: VersionedSeriesDto): SeriesBoundaryCommand =
    SeriesBoundaryCommand(
        requestId = commandKey,
        scope = requireNotNull(scope),
        action = SeriesBoundaryActionDto.EDIT,
        gameId = gameId,
        boundary = form.localDate,
        currentRevisionId = series.series.revisionId,
        successor = if (scope == SeriesBoundaryScopeDto.THIS_AND_FUTURE) {
            toSeriesWriteCommand()
        } else {
            null
        },
        replacement = if (scope == SeriesBoundaryScopeDto.ONLY_THIS) {
            form.toGameWriteCommand()
        } else {
            null
        },
    )

