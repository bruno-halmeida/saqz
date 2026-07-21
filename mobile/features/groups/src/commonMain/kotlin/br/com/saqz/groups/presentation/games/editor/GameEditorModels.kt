package br.com.saqz.groups.presentation.games.editor

import br.com.saqz.groups.data.game.GameVenueDto
import br.com.saqz.groups.data.game.SeriesBoundaryScopeDto
import br.com.saqz.groups.data.game.VersionedGameDto
import br.com.saqz.groups.data.game.VersionedSeriesDto
import br.com.saqz.groups.data.game.WeeklySlotDto
import kotlinx.serialization.Serializable

@Serializable
enum class GameEditorMode {
    ONE_TIME,
    WEEKLY,
}

@Serializable
data class GameEditorForm(
    val title: String = "",
    val venue: GameVenueDto? = null,
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
    val slots: List<WeeklySlotDto> = emptyList(),
)

@Serializable
data class GameEditorDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val gameId: String?,
    val seriesId: String?,
    val commandKey: String,
    val etag: String?,
    val mode: GameEditorMode,
    val form: GameEditorForm,
    val scope: SeriesBoundaryScopeDto? = null,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

data class GameEditorDefaults(
    val title: String,
    val venue: GameVenueDto?,
    val zoneId: String,
    val durationMinutes: Int?,
    val capacity: Int?,
    val confirmationLeadMinutes: Int?,
    val gameFeeCents: Long?,
)

data class GameEditorInput(
    val groupId: String,
    val defaults: GameEditorDefaults,
    val existing: VersionedGameDto? = null,
    val series: VersionedSeriesDto? = null,
)
