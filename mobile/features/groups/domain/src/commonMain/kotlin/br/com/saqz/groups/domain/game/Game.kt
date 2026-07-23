package br.com.saqz.groups.domain.game

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.GroupId

enum class GameStatus { Draft, Published, Cancelled, Completed }

enum class SeriesBoundaryScope { OnlyThis, ThisAndFuture }

enum class SeriesBoundaryAction { Edit, Cancel }

enum class Weekday { Monday, Tuesday, Wednesday, Thursday, Friday, Saturday, Sunday }

// SPEC_DEVIATION: data class, not @JvmInline value class.
// Reason: GameEditorDraft carries this version token and is constructed field-by-field by the
// native iOS draft codec/tests; Kotlin/Native erases value-class members to an opaque `id`,
// so Swift cannot build a model whose fields are value classes. A data class exports as a real
// Objective-C type Swift can construct. Value-class semantics are not needed for this token.
data class GameVersionToken(val value: String)

data class GameVenue(
    val venueId: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

data class Game(
    val id: String,
    val groupId: GroupId,
    val title: String,
    val venue: GameVenue,
    val localDate: String,
    val localTime: String,
    val zoneId: String,
    val startsAt: String,
    val durationMinutes: Int,
    val capacity: Int,
    val confirmationDeadline: String,
    val gameFeeCents: Long? = null,
    val notes: String? = null,
    val status: GameStatus,
    val version: Long,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val financeReviewRequired: Boolean = false,
)

data class VersionedGame(val game: Game, val version: GameVersionToken)

data class WeeklySlot(
    val slotKey: String,
    val weekday: Weekday,
    val localTime: String,
    val durationMinutes: Int,
    val venue: GameVenue,
    val capacity: Int,
    val confirmationLeadMinutes: Int,
    val gameFeeCents: Long? = null,
    val title: String,
)

data class SeriesOccurrence(
    val id: String,
    val localDate: String,
    val localTime: String,
    val startsAt: String,
    val status: GameStatus,
    val version: Long,
)

data class WeeklySeries(
    val id: String,
    val revisionId: String,
    val revisionNumber: Int,
    val zoneId: String,
    val localStartDate: String,
    val localEndDate: String? = null,
    val activeThroughDate: String? = null,
    val slots: List<WeeklySlot>,
    val occurrences: List<SeriesOccurrence>,
    val version: Long,
)

data class VersionedSeries(val series: WeeklySeries, val version: GameVersionToken)

data class GameWriteCommand(
    val requestId: String? = null,
    val title: String? = null,
    val venue: GameVenue? = null,
    val localDate: String?,
    val localTime: String?,
    val zoneId: String?,
    val startsAt: String?,
    val durationMinutes: Int? = null,
    val capacity: Int? = null,
    val confirmationDeadline: String? = null,
    val gameFeeCents: Long? = null,
    val useDefaultGameFee: Boolean = true,
    val notes: String? = null,
)

data class WeeklySeriesWriteCommand(
    val requestId: String?,
    val revisionId: String?,
    val zoneId: String?,
    val localStartDate: String?,
    val localEndDate: String? = null,
    val slots: List<WeeklySlot>?,
)

data class SeriesBoundaryCommand(
    val requestId: String,
    val scope: SeriesBoundaryScope,
    val action: SeriesBoundaryAction,
    val gameId: String? = null,
    val boundary: String? = null,
    val currentRevisionId: String? = null,
    val successor: WeeklySeriesWriteCommand? = null,
    val replacement: GameWriteCommand? = null,
)

enum class GameLifecycleAction { Publish, Cancel, Complete }

sealed interface GameError : SaqzError {
    data class Validation(val error: DataError.Validation) : GameError
    data object HiddenResource : GameError
    data object Conflict : GameError
    data object InvalidLifecycle : GameError
    data object Authentication : GameError
    data class Data(val error: DataError) : GameError
}

interface GameGateway {
    suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError>
    suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError>
    suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError>
    suspend fun edit(groupId: GroupId, gameId: String, version: GameVersionToken, command: GameWriteCommand): SaqzResult<VersionedGame, GameError>
    suspend fun lifecycle(groupId: GroupId, gameId: String, version: GameVersionToken, action: GameLifecycleAction): SaqzResult<VersionedGame, GameError>
    suspend fun createSeries(groupId: GroupId, command: WeeklySeriesWriteCommand): SaqzResult<VersionedSeries, GameError>
    suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError>
    suspend fun boundary(groupId: GroupId, seriesId: String, version: GameVersionToken, command: SeriesBoundaryCommand): SaqzResult<VersionedSeries, GameError>
}
