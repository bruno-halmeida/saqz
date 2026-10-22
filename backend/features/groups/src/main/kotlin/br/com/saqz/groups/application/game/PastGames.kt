package br.com.saqz.groups.application.game

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Base64
import java.util.UUID

/** Posição na lista de anteriores (`starts_at DESC, id DESC`): a página seguinte começa depois deste jogo. */
data class PastGameCursor(val startsAt: Instant, val id: UUID) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$startsAt|$id".toByteArray())

    companion object {
        fun decode(value: String): PastGameCursor? = runCatching {
            val (startsAt, id) = String(Base64.getUrlDecoder().decode(value)).split('|', limit = 2)
            PastGameCursor(Instant.parse(startsAt), UUID.fromString(id))
        }.getOrNull()
    }
}

data class PastGameSummary(
    val id: UUID,
    val title: String,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: String,
    val startsAt: Instant,
    val durationMinutes: Int,
    val status: GameStatus,
    /** Encerrado: quem foi (confirmados sem a marca de falta). Cancelado: quem estava confirmado. */
    val confirmedCount: Int,
    val capacity: Int,
    val venueName: String,
    val pendingChargeCount: Int,
    val pendingChargeCents: Long,
)

/** Presença de uma pessoa (membro ou convidado, pelo `guest_seq`) num jogo, para marcar a falta. */
data class NoShowTarget(
    val gameStatus: GameStatus,
    val endsAt: Instant,
    val attendanceStatus: String?,
)

interface PastGamesRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?

    /** Encerrados, e cancelados que chegaram a ser publicados, que começaram antes de [now]. */
    fun page(groupId: UUID, now: Instant, after: PastGameCursor?, limit: Int): List<PastGameSummary>
    fun hasAny(groupId: UUID, now: Instant): Boolean
    fun noShowTarget(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int): NoShowTarget?

    /** Idempotente: repetir a marca não troca quem marcou nem quando. */
    fun setNoShow(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, marked: Boolean, actorId: UUID, at: Instant)
}

sealed interface PastGamesResult {
    data class Success(val games: List<PastGameSummary>, val nextCursor: PastGameCursor?) : PastGamesResult
    data object Hidden : PastGamesResult
    data object Forbidden : PastGamesResult
}

enum class NoShowResult { SUCCESS, HIDDEN, FORBIDDEN, NOT_COMPLETED, NOT_CONFIRMED, DEADLINE_PASSED }

/** JOGO-011 (jogos anteriores) e PRES-016 (faltou): só dono e admin. */
class PastGames(
    private val transactions: TransactionRunner,
    private val repository: PastGamesRepository,
    private val clock: () -> Instant,
) {
    fun list(actorId: UUID, groupId: UUID, after: PastGameCursor?, limit: Int = PAGE_SIZE): PastGamesResult {
        when (repository.role(actorId, groupId)) {
            null -> return PastGamesResult.Hidden
            GroupRole.ATHLETE -> return PastGamesResult.Forbidden
            GroupRole.OWNER, GroupRole.ADMIN -> Unit
        }
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        // Um a mais para saber se há próxima página sem devolver cursor no fim.
        val rows = repository.page(groupId, clock(), after, size + 1)
        val games = rows.take(size)
        val next = if (rows.size > size) games.last().let { PastGameCursor(it.startsAt, it.id) } else null
        return PastGamesResult.Success(games, next)
    }

    /** Só o gestor sabe se há anteriores; para o atleta é sempre falso. */
    fun hasPastGames(groupId: UUID, role: GroupRole?): Boolean =
        (role == GroupRole.OWNER || role == GroupRole.ADMIN) && repository.hasAny(groupId, clock())

    fun setNoShow(actorId: UUID, groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, marked: Boolean): NoShowResult =
        transactions.inTransaction {
            when (repository.role(actorId, groupId)) {
                null -> return@inTransaction NoShowResult.HIDDEN
                GroupRole.ATHLETE -> return@inTransaction NoShowResult.FORBIDDEN
                GroupRole.OWNER, GroupRole.ADMIN -> Unit
            }
            val target = repository.noShowTarget(groupId, gameId, memberId, guestSeq) ?: return@inTransaction NoShowResult.HIDDEN
            val now = clock()
            when {
                target.gameStatus != GameStatus.COMPLETED -> NoShowResult.NOT_COMPLETED
                target.attendanceStatus != "CONFIRMED" -> NoShowResult.NOT_CONFIRMED
                now.isAfter(target.endsAt.plus(NO_SHOW_WINDOW)) -> NoShowResult.DEADLINE_PASSED
                else -> {
                    repository.setNoShow(groupId, gameId, memberId, guestSeq, marked, actorId, now)
                    NoShowResult.SUCCESS
                }
            }
        }

    companion object {
        const val PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 50

        /** PRES-016: a falta se marca ou desfaz até 7 dias depois do fim do jogo (início + duração). */
        val NO_SHOW_WINDOW: Duration = Duration.ofDays(7)
    }
}
