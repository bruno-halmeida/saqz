package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.game.ChangeGameLifecycle
import br.com.saqz.groups.application.game.CreateGame
import br.com.saqz.groups.application.game.EditGame
import br.com.saqz.groups.application.game.GameCommandResult
import br.com.saqz.groups.application.game.GameListResult
import br.com.saqz.groups.application.game.GameReadResult
import br.com.saqz.groups.application.game.GameView
import br.com.saqz.groups.application.game.GetGame
import br.com.saqz.groups.application.game.ListGames
import br.com.saqz.groups.application.attendance.AttendanceDetailQuery
import br.com.saqz.groups.domain.game.CreateGameInput
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameMutation
import br.com.saqz.groups.domain.game.GameVenueInput
import br.com.saqz.groups.domain.game.NullableGameFeeOverride
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class GameVenueRequest @JsonCreator constructor(
    @JsonProperty("venueId") val venueId: UUID? = null,
    @JsonProperty("name") val name: String?,
    @JsonProperty("address") val address: String?,
    @JsonProperty("court") val court: String? = null,
)

data class GameWriteRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("venue") val venue: GameVenueRequest? = null,
    @JsonProperty("localDate") val localDate: LocalDate?,
    @JsonProperty("localTime") val localTime: LocalTime?,
    @JsonProperty("zoneId") val zoneId: String?,
    @JsonProperty("startsAt") val startsAt: Instant?,
    @JsonProperty("durationMinutes") val durationMinutes: Int? = null,
    @JsonProperty("capacity") val capacity: Int? = null,
    @JsonProperty("confirmationDeadline") val confirmationDeadline: Instant? = null,
    @JsonProperty("gameFeeCents") val gameFeeCents: Long? = null,
    @JsonProperty("useDefaultGameFee") val useDefaultGameFee: Boolean = true,
    @JsonProperty("notes") val notes: String? = null,
)

data class GameVenueResponse(val venueId: UUID?, val name: String, val address: String, val court: String?)
data class GameResponse(
    val id: UUID, val groupId: UUID, val title: String, val venue: GameVenueResponse,
    val localDate: LocalDate, val localTime: LocalTime, val zoneId: String, val startsAt: Instant,
    val durationMinutes: Int, val capacity: Int, val confirmationDeadline: Instant,
    val gameFeeCents: Long?, val notes: String?, val status: String, val version: Long,
    val confirmedCount: Int, val availableSpots: Int, val waitlistCount: Int,
    val financeReviewRequired: Boolean = false,
    val ownAttendance: AttendanceEntryResponse? = null,
)

class GameNotFoundException : RuntimeException()
class InvalidGameTransitionException : RuntimeException()

@RestController
class GameController(
    private val actors: VerifiedGroupActorResolver,
    private val createGame: CreateGame,
    private val editGame: EditGame,
    private val lifecycle: ChangeGameLifecycle,
    private val listGames: ListGames,
    private val getGame: GetGame,
    private val attendance: AttendanceDetailQuery? = null,
) {
    @PostMapping("/api/groups/{groupId}/games")
    fun create(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @RequestBody request: GameWriteRequest): ResponseEntity<GameResponse> {
        val parsedGroup = uuid(groupId); val gameId = request.requestId ?: invalid("requestId", "is required")
        return command(createGame.execute(actors.resolve(identity), parsedGroup, gameId, request.toCreate()), HttpStatus.CREATED)
    }

    @GetMapping("/api/groups/{groupId}/games")
    fun list(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String): List<GameResponse> =
        when (val result = listGames.execute(actors.resolve(identity), uuid(groupId))) {
            is GameListResult.Success -> result.games.map(GameView::toResponse)
            GameListResult.GroupNotFound -> throw GameNotFoundException()
        }

    @GetMapping("/api/groups/{groupId}/games/{gameId}")
    fun read(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String): ResponseEntity<GameResponse> {
        val actor = actors.resolve(identity); val group = uuid(groupId); val game = uuid(gameId)
        return when (val result = getGame.execute(actor, group, game)) {
            is GameReadResult.Success -> ResponseEntity.ok().eTag(result.game.game.version.toString()).body(
                result.game.toResponse().copy(ownAttendance = attendance?.find(actor, group, game)?.own?.let { AttendanceEntryResponse(it.memberId, it.status.name, it.waitlistSequence, it.version) }),
            )
            GameReadResult.GameNotFound -> throw GameNotFoundException()
        }
    }

    @PutMapping("/api/groups/{groupId}/games/{gameId}")
    fun edit(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String, @RequestHeader("If-Match", required = false) ifMatch: String?, @RequestBody request: GameWriteRequest) =
        command(editGame.execute(actors.resolve(identity), uuid(groupId), uuid(gameId), version(ifMatch), request.toDraft()))

    @PostMapping("/api/groups/{groupId}/games/{gameId}/publish")
    fun publish(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String, @RequestHeader("If-Match", required = false) ifMatch: String?) =
        lifecycle(identity, groupId, gameId, ifMatch, GameMutation.PUBLISH)

    @PostMapping("/api/groups/{groupId}/games/{gameId}/cancel")
    fun cancel(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String, @RequestHeader("If-Match", required = false) ifMatch: String?) =
        lifecycle(identity, groupId, gameId, ifMatch, GameMutation.CANCEL)

    @PostMapping("/api/groups/{groupId}/games/{gameId}/complete")
    fun complete(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: String, @PathVariable gameId: String, @RequestHeader("If-Match", required = false) ifMatch: String?) =
        lifecycle(identity, groupId, gameId, ifMatch, GameMutation.COMPLETE)

    private fun lifecycle(identity: RequestIdentity, groupId: String, gameId: String, ifMatch: String?, mutation: GameMutation) =
        command(lifecycle.execute(actors.resolve(identity), uuid(groupId), uuid(gameId), version(ifMatch), mutation))

    private fun command(result: GameCommandResult, status: HttpStatus = HttpStatus.OK): ResponseEntity<GameResponse> = when (result) {
        is GameCommandResult.Success -> ResponseEntity.status(status).eTag(result.game.version.toString()).body(result.game.toView().toResponse())
        is GameCommandResult.Invalid -> throw InvalidGroupRequestException(result.errors.groupBy({ it.field }, { it.message }))
        is GameCommandResult.InvalidTransition -> throw InvalidGameTransitionException()
        GameCommandResult.VersionConflict -> throw VersionConflictException()
        GameCommandResult.GroupNotFound, GameCommandResult.GameNotFound -> throw GameNotFoundException()
        GameCommandResult.AccessForbidden -> throw AccessForbiddenException()
    }

    private fun uuid(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
    private fun version(value: String?): Long {
        if (value == null) throw PreconditionRequiredException()
        return Regex("\"([1-9][0-9]*)\"").matchEntire(value)?.groupValues?.get(1)?.toLong() ?: invalid("ifMatch", "must be a quoted positive version")
    }
    private fun invalid(field: String, message: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf(message)))
}

private fun GameWriteRequest.toCreate() = CreateGameInput(title, venue?.toInput(), localDate, localTime, zoneId, startsAt, durationMinutes, capacity, confirmationDeadline, if (useDefaultGameFee) NullableGameFeeOverride.UseDefault else NullableGameFeeOverride.Value(gameFeeCents), notes)
private fun GameWriteRequest.toDraft() = GameDraftInput(title, venue?.toInput(), localDate, localTime, zoneId, startsAt, durationMinutes, capacity, confirmationDeadline, gameFeeCents, notes)
private fun GameVenueRequest.toInput() = GameVenueInput(venueId, name, address, court)
private fun br.com.saqz.groups.domain.game.Game.toView() = GameView(this, 0, snapshot.capacity, 0)
private fun GameView.toResponse(): GameResponse { val s = game.snapshot; return GameResponse(game.id, game.groupId, s.title, GameVenueResponse(s.venue.venueId,s.venue.name,s.venue.address,s.venue.court),s.localDate,s.localTime,s.zoneId.value,s.startsAt,s.durationMinutes,s.capacity,s.confirmationDeadline,s.gameFeeCents,s.notes,game.status.name,game.version,confirmedCount,availableSpots,waitlistCount,game.status.name == "CANCELLED") }
