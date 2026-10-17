package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface ResolveAttendanceLinkResult {
    data class Success(val groupId: UUID, val gameId: UUID, val registrationRequired: Boolean = false) : ResolveAttendanceLinkResult

    data class AttemptLimit(val retryAfterSeconds: Int) : ResolveAttendanceLinkResult

    data object InvalidOrExpired : ResolveAttendanceLinkResult

    /** O código existe e o ator o enxerga, mas o jogo não aceita mais resposta: a tela diz o motivo. */
    data class GameClosed(val reason: AttendanceLinkClosedReason) : ResolveAttendanceLinkResult

    data object Unavailable : ResolveAttendanceLinkResult
}

/** Por que um link de jogo conhecido não vale mais. Vai no corpo do erro como `reason`. */
enum class AttendanceLinkClosedReason { GAME_STARTED, GAME_CANCELLED, GAME_UNPUBLISHED }

class ResolveAttendanceLink(
    private val transactionRunner: TransactionRunner,
    private val repository: AttendanceLinkRepository,
    private val clock: Clock,
) {
    fun execute(actorId: UUID, rawCode: String): ResolveAttendanceLinkResult = runCatching {
        transactionRunner.inTransaction {
            val now = clock.instant()
            val storedWindow = repository.lockAttemptWindow(actorId, now)
            val currentWindow = storedWindow
                .takeUnless { now >= it.windowStartedAt.plus(WINDOW) }
                ?: AttendanceLinkAttemptWindow(now, 0)
            if (currentWindow.invalidCount == MAX_INVALID_ATTEMPTS) {
                return@inTransaction ResolveAttendanceLinkResult.AttemptLimit(retryAfterSeconds(now, currentWindow))
            }

            val code = runCatching { AttendanceLinkCode.from(rawCode) }.getOrNull()
            val target = code?.let { repository.findResolvableTarget(actorId, AttendanceLinkTokenDigest.sha256(it)) }
            val closedReason = target?.closedReason(now)
            if (target == null || closedReason != null) {
                repository.recordInvalidAttempt(
                    RecordInvalidAttendanceLinkAttempt(
                        actorId,
                        currentWindow.windowStartedAt,
                        currentWindow.invalidCount + 1,
                    ),
                )
                return@inTransaction closedReason?.let(ResolveAttendanceLinkResult::GameClosed)
                    ?: ResolveAttendanceLinkResult.InvalidOrExpired
            }

            ResolveAttendanceLinkResult.Success(target.groupId, target.gameId, target.registrationRequired)
        }
    }.getOrElse {
        ResolveAttendanceLinkResult.Unavailable
    }

    private fun AttendanceLinkResolvableTarget.closedReason(now: Instant): AttendanceLinkClosedReason? = when {
        status == GameStatus.CANCELLED -> AttendanceLinkClosedReason.GAME_CANCELLED
        status == GameStatus.DRAFT -> AttendanceLinkClosedReason.GAME_UNPUBLISHED
        status == GameStatus.COMPLETED || now >= startsAt -> AttendanceLinkClosedReason.GAME_STARTED
        else -> null
    }

    private fun retryAfterSeconds(now: Instant, window: AttendanceLinkAttemptWindow): Int {
        val remainingMillis = Duration.between(now, window.windowStartedAt.plus(WINDOW)).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    private companion object {
        const val MAX_INVALID_ATTEMPTS = 10
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
