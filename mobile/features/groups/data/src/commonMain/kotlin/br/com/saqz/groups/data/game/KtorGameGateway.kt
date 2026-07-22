package br.com.saqz.groups.data.game

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryAction
import br.com.saqz.groups.domain.game.SeriesBoundaryCommand
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.SeriesOccurrence
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySeries
import br.com.saqz.groups.domain.game.WeeklySeriesWriteCommand
import br.com.saqz.groups.domain.game.WeeklySlot
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkError
import br.com.saqz.network.NetworkRequest
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.RetrySafety
import br.com.saqz.network.retryTransport
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal enum class GameStatusTransport { DRAFT, PUBLISHED, CANCELLED, COMPLETED }

@Serializable
internal enum class BoundaryScopeTransport { ONLY_THIS, THIS_AND_FUTURE }

@Serializable
internal enum class BoundaryActionTransport { EDIT, CANCEL }

@Serializable
internal enum class WeekdayTransport { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

@Serializable
internal data class GameVenueTransport(
    val venueId: String? = null,
    val name: String,
    val address: String,
    val court: String? = null,
)

@Serializable
internal data class GameTransport(
    val id: String,
    val groupId: String,
    val title: String,
    val venue: GameVenueTransport,
    val localDate: String,
    val localTime: String,
    val zoneId: String,
    val startsAt: String,
    val durationMinutes: Int,
    val capacity: Int,
    val confirmationDeadline: String,
    val gameFeeCents: Long? = null,
    val notes: String? = null,
    val status: GameStatusTransport,
    val version: Long,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val financeReviewRequired: Boolean = false,
)

@Serializable
internal data class WeeklySlotTransport(
    val slotKey: String,
    val weekday: WeekdayTransport,
    val localTime: String,
    val durationMinutes: Int,
    val venue: GameVenueTransport,
    val capacity: Int,
    val confirmationLeadMinutes: Int,
    val gameFeeCents: Long? = null,
    val title: String,
)

@Serializable
internal data class SeriesOccurrenceTransport(
    val id: String,
    val localDate: String,
    val localTime: String,
    val startsAt: String,
    val status: GameStatusTransport,
    val version: Long,
)

@Serializable
internal data class WeeklySeriesTransport(
    val id: String,
    val revisionId: String,
    val revisionNumber: Int,
    val zoneId: String,
    val localStartDate: String,
    val localEndDate: String? = null,
    val activeThroughDate: String? = null,
    val slots: List<WeeklySlotTransport>,
    val occurrences: List<SeriesOccurrenceTransport>,
    val version: Long,
)

@Serializable
internal data class GameWriteRequest(
    val requestId: String? = null,
    val title: String? = null,
    val venue: GameVenueTransport? = null,
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

@Serializable
internal data class WeeklySeriesWriteRequest(
    val requestId: String?,
    val revisionId: String?,
    val zoneId: String?,
    val localStartDate: String?,
    val localEndDate: String? = null,
    val slots: List<WeeklySlotTransport>?,
)

@Serializable
internal data class SeriesBoundaryRequest(
    val requestId: String,
    val scope: BoundaryScopeTransport,
    val action: BoundaryActionTransport,
    val gameId: String? = null,
    val boundary: String? = null,
    val currentRevisionId: String? = null,
    val successor: WeeklySeriesWriteRequest? = null,
    val replacement: GameWriteRequest? = null,
)

class KtorGameGateway(
    private val network: AuthenticatedNetworkClient,
    private val retryDelay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : GameGateway {
    private val json = Json { explicitNulls = false }

    override suspend fun list(groupId: GroupId): SaqzResult<List<Game>, GameError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, "api/groups/${groupId.value}/games", ListSerializer(GameTransport.serializer()))
        }.map { games -> games.map(GameTransport::toDomain) }

    override suspend fun read(groupId: GroupId, gameId: String): SaqzResult<VersionedGame, GameError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, gameRoute(groupId, gameId), GameTransport.serializer())
        }.versionedGame()

    override suspend fun create(groupId: GroupId, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(HttpMethod.Post, "api/groups/${groupId.value}/games", GameTransport.serializer(), request(command))
        }.versionedGame()

    override suspend fun edit(groupId: GroupId, gameId: String, version: GameVersionToken, command: GameWriteCommand): SaqzResult<VersionedGame, GameError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(HttpMethod.Put, gameRoute(groupId, gameId), GameTransport.serializer(), request(command, version))
        }.versionedGame()

    override suspend fun lifecycle(groupId: GroupId, gameId: String, version: GameVersionToken, action: GameLifecycleAction): SaqzResult<VersionedGame, GameError> =
        network.execute(
            HttpMethod.Post,
            "${gameRoute(groupId, gameId)}/${action.route}",
            GameTransport.serializer(),
            NetworkRequest(headers = mapOf(HttpHeaders.IfMatch to version.value)),
        ).versionedGame()

    override suspend fun createSeries(groupId: GroupId, command: WeeklySeriesWriteCommand): SaqzResult<VersionedSeries, GameError> =
        retryTransport(command.requestId.safety(), delayMillis = retryDelay) {
            network.execute(HttpMethod.Post, seriesRoute(groupId), WeeklySeriesTransport.serializer(), request(command))
        }.versionedSeries()

    override suspend fun readSeries(groupId: GroupId, seriesId: String): SaqzResult<VersionedSeries, GameError> =
        retryTransport(RetrySafety.Read, delayMillis = retryDelay) {
            network.execute(HttpMethod.Get, "${seriesRoute(groupId)}/$seriesId", WeeklySeriesTransport.serializer())
        }.versionedSeries()

    override suspend fun boundary(groupId: GroupId, seriesId: String, version: GameVersionToken, command: SeriesBoundaryCommand): SaqzResult<VersionedSeries, GameError> =
        retryTransport(RetrySafety.IdempotentWrite, delayMillis = retryDelay) {
            network.execute(
                HttpMethod.Post,
                "${seriesRoute(groupId)}/$seriesId/boundaries",
                WeeklySeriesTransport.serializer(),
                NetworkRequest(json.encodeToString(command.toRequest()), mapOf(HttpHeaders.IfMatch to version.value)),
            )
        }.versionedSeries()

    private fun request(command: GameWriteCommand, version: GameVersionToken? = null) = NetworkRequest(
        body = json.encodeToString(command.toRequest()),
        headers = version?.let { mapOf(HttpHeaders.IfMatch to it.value) }.orEmpty(),
    )

    private fun request(command: WeeklySeriesWriteCommand) = NetworkRequest(json.encodeToString(command.toRequest()))
    private fun gameRoute(groupId: GroupId, gameId: String) = "api/groups/${groupId.value}/games/$gameId"
    private fun seriesRoute(groupId: GroupId) = "api/groups/${groupId.value}/game-series"
}

private val GameLifecycleAction.route: String
    get() = name.lowercase()

private fun String?.safety() = if (isNullOrBlank()) RetrySafety.Never else RetrySafety.IdempotentWrite

private fun NetworkResult<List<GameTransport>>.map(transform: (List<GameTransport>) -> List<Game>) = when (this) {
    is NetworkResult.Success -> SaqzResult.Success(transform(value))
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
}

private fun NetworkResult<GameTransport>.versionedGame() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
    is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)
        ?.takeIf(String::isNotBlank)
        ?.let(::GameVersionToken)
        ?.let { VersionedGame(value.toDomain(), it) }
        ?.let { SaqzResult.Success(it) }
        ?: invalidGameResponse()
}

private fun NetworkResult<WeeklySeriesTransport>.versionedSeries() = when (this) {
    is NetworkResult.Failure -> SaqzResult.Failure(error.toDomain())
    is NetworkResult.Success -> metadata.header(HttpHeaders.ETag)
        ?.takeIf(String::isNotBlank)
        ?.let(::GameVersionToken)
        ?.let { VersionedSeries(value.toDomain(), it) }
        ?.let { SaqzResult.Success(it) }
        ?: invalidGameResponse()
}

private fun NetworkError.toDomain(): GameError = when (this) {
    is NetworkError.ApiProblemError -> when (problem.code) {
        "VALIDATION_FAILED" -> GameError.Validation(DataError.Validation(ValidationDetails(emptyList(), problem.fieldErrors.orEmpty())))
        "GAME_NOT_FOUND", "GROUP_NOT_FOUND" -> GameError.HiddenResource
        "VERSION_CONFLICT" -> GameError.Conflict
        "INVALID_GAME_TRANSITION" -> GameError.InvalidLifecycle
        "AUTHENTICATION_REQUIRED" -> GameError.Authentication
        else -> GameError.Data(problem.status.toDataError())
    }
    is NetworkError.HttpStatus -> GameError.Data(status.toDataError())
    NetworkError.Timeout -> GameError.Data(DataError.Timeout)
    NetworkError.Connectivity -> GameError.Data(DataError.Connectivity)
    NetworkError.InvalidResponse -> GameError.Data(DataError.InvalidResponse)
    NetworkError.PayloadTooLarge -> GameError.Data(DataError.PayloadTooLarge)
    NetworkError.Unavailable, NetworkError.Unknown -> GameError.Data(DataError.Unknown)
}

private fun Int.toDataError() = when (this) {
    401 -> DataError.Unauthenticated
    403 -> DataError.Forbidden
    404 -> DataError.NotFound
    409 -> DataError.Conflict
    413 -> DataError.PayloadTooLarge
    in 500..599 -> DataError.Server
    else -> DataError.Unknown
}

private fun GameTransport.toDomain() = Game(
    id, GroupId(groupId), title, venue.toDomain(), localDate, localTime, zoneId, startsAt,
    durationMinutes, capacity, confirmationDeadline, gameFeeCents, notes, status.toDomain(),
    version, confirmedCount, availableSpots, waitlistCount, financeReviewRequired,
)

private fun GameVenueTransport.toDomain() = GameVenue(venueId, name, address, court)
private fun GameStatusTransport.toDomain() = GameStatus.entries[ordinal]
private fun WeekdayTransport.toDomain() = Weekday.entries[ordinal]
private fun WeeklySlotTransport.toDomain() = WeeklySlot(
    slotKey,
    weekday.toDomain(),
    localTime,
    durationMinutes,
    venue.toDomain(),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun SeriesOccurrenceTransport.toDomain() = SeriesOccurrence(
    id,
    localDate,
    localTime,
    startsAt,
    status.toDomain(),
    version,
)

private fun WeeklySeriesTransport.toDomain() = WeeklySeries(
    id,
    revisionId,
    revisionNumber,
    zoneId,
    localStartDate,
    localEndDate,
    activeThroughDate,
    slots.map(WeeklySlotTransport::toDomain),
    occurrences.map(SeriesOccurrenceTransport::toDomain),
    version,
)
private fun GameVenue.toRequest() = GameVenueTransport(venueId, name, address, court)
private fun WeeklySlot.toRequest() = WeeklySlotTransport(
    slotKey,
    WeekdayTransport.entries[weekday.ordinal],
    localTime,
    durationMinutes,
    venue.toRequest(),
    capacity,
    confirmationLeadMinutes,
    gameFeeCents,
    title,
)

private fun GameWriteCommand.toRequest() = GameWriteRequest(
    requestId,
    title,
    venue?.toRequest(),
    localDate,
    localTime,
    zoneId,
    startsAt,
    durationMinutes,
    capacity,
    confirmationDeadline,
    gameFeeCents,
    useDefaultGameFee,
    notes,
)

private fun WeeklySeriesWriteCommand.toRequest() = WeeklySeriesWriteRequest(
    requestId,
    revisionId,
    zoneId,
    localStartDate,
    localEndDate,
    slots?.map(WeeklySlot::toRequest),
)

private fun SeriesBoundaryCommand.toRequest() = SeriesBoundaryRequest(
    requestId,
    BoundaryScopeTransport.entries[scope.ordinal],
    BoundaryActionTransport.entries[action.ordinal],
    gameId,
    boundary,
    currentRevisionId,
    successor?.toRequest(),
    replacement?.toRequest(),
)
private fun invalidGameResponse() = SaqzResult.Failure(GameError.Data(DataError.InvalidResponse))
