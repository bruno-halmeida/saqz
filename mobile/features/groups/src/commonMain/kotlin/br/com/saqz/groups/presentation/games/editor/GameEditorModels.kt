package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
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
