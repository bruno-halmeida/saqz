package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.WeeklySlot

enum class GameEditorMode {
    ONE_TIME,
    WEEKLY,
}

data class GameEditorForm(
    val title: String = "",
    val venue: GameVenue? = null,
    val localDate: String = "",
    val localTime: String = "",
    val zoneId: String = "",
    val startsAt: String = "",
    val durationMinutes: String = "",
    val capacity: String = "",
    val confirmationDeadline: String = "",
    val gameFeeBrl: String = "",
    val notes: String = "",
    val localEndDate: String = "",
    val slots: List<WeeklySlot> = emptyList(),
)

data class GameEditorDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val gameId: String?,
    val seriesId: String?,
    val commandKey: String,
    val version: GameVersionToken?,
    val mode: GameEditorMode,
    val form: GameEditorForm,
    val scope: SeriesBoundaryScope? = null,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

data class GameEditorDefaults(
    val title: String,
    val venue: GameVenue?,
    val zoneId: String,
    val durationMinutes: Int?,
    val capacity: Int?,
    val confirmationLeadMinutes: Int?,
    val gameFeeCents: Long?,
)

data class GameEditorInput(
    val groupId: String,
    val defaults: GameEditorDefaults,
    val existing: VersionedGame? = null,
    val series: VersionedSeries? = null,
)

/**
 * Seeds a new game from the group's own profile/finance defaults, so the create route
 * opens pre-filled with the group's usual venue, capacity, duration and fee instead of
 * an empty form. Every field is optional at the group level and stays empty when unset.
 */
fun Group.toGameEditorDefaults(): GameEditorDefaults = GameEditorDefaults(
    title = name,
    venue = profile?.defaultVenue?.let { GameVenue(it.id, it.name, it.address, it.court) },
    zoneId = timeZone.id,
    durationMinutes = profile?.regularSlots?.firstOrNull()?.durationMinutes,
    capacity = profile?.defaultCapacity,
    confirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes,
    gameFeeCents = financeDefaults?.defaultGameFeeCents,
)
