package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.game.NoShowResult
import br.com.saqz.groups.application.game.PastGameCursor
import br.com.saqz.groups.application.game.PastGameSummary
import br.com.saqz.groups.application.game.PastGames
import br.com.saqz.groups.application.game.PastGamesResult
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class PastGameResponse(
    val id: UUID,
    val title: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: String,
    val startsAt: Instant,
    val durationMinutes: Int,
    val status: String,
    val confirmedCount: Int,
    val capacity: Int,
    val venueName: String,
    val pendingChargeCount: Int,
    val pendingChargeCents: Long,
)

data class PastGamesPageResponse(val games: List<PastGameResponse>, val nextCursor: String?)

/** JOGO-011 (jogos anteriores) e PRES-016 (faltou): rotas só de dono e admin. */
@RestController
class PastGamesController(
    private val actors: VerifiedGroupActorResolver,
    private val pastGames: PastGames,
) {
    @GetMapping("/api/groups/{groupId}/games/past")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): PastGamesPageResponse {
        val after = cursor?.takeIf { it.isNotBlank() }?.let { PastGameCursor.decode(it) ?: invalid("cursor") }
        return when (val result = pastGames.list(actors.resolve(identity), uuid(groupId), after, limit ?: PastGames.PAGE_SIZE)) {
            is PastGamesResult.Success -> PastGamesPageResponse(result.games.map { it.response() }, result.nextCursor?.encode())
            PastGamesResult.Hidden -> throw GameNotFoundException()
            PastGamesResult.Forbidden -> throw AccessForbiddenException()
        }
    }

    @PutMapping("/api/groups/{groupId}/games/{gameId}/attendance/no-show/{memberId}/{guestSeq}")
    fun mark(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @PathVariable memberId: String,
        @PathVariable guestSeq: Int,
    ): ResponseEntity<Void> = noShow(identity, groupId, gameId, memberId, guestSeq, marked = true)

    @DeleteMapping("/api/groups/{groupId}/games/{gameId}/attendance/no-show/{memberId}/{guestSeq}")
    fun undo(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @PathVariable memberId: String,
        @PathVariable guestSeq: Int,
    ): ResponseEntity<Void> = noShow(identity, groupId, gameId, memberId, guestSeq, marked = false)

    private fun noShow(identity: RequestIdentity, groupId: String, gameId: String, memberId: String, guestSeq: Int, marked: Boolean): ResponseEntity<Void> {
        if (guestSeq < 0) invalid("guestSeq")
        return when (pastGames.setNoShow(actors.resolve(identity), uuid(groupId), uuid(gameId), uuid(memberId), guestSeq, marked)) {
            NoShowResult.SUCCESS -> ResponseEntity.noContent().build()
            NoShowResult.HIDDEN -> throw GameNotFoundException()
            NoShowResult.FORBIDDEN -> throw AccessForbiddenException()
            NoShowResult.NOT_CONFIRMED -> invalid("memberId")
            NoShowResult.NOT_COMPLETED, NoShowResult.DEADLINE_PASSED -> throw AttendanceFrozenException()
        }
    }

    private fun uuid(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
    private fun invalid(field: String): Nothing = throw InvalidGroupRequestException(mapOf(field to listOf("is required or invalid")))
}

private fun PastGameSummary.response() = PastGameResponse(
    id, title, localDate, localTime, zoneId, startsAt, durationMinutes, status.name, confirmedCount, capacity, venueName,
    pendingChargeCount, pendingChargeCents,
)
