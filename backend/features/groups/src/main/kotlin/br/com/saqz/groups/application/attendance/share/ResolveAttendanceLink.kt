package br.com.saqz.groups.application.attendance.share

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

sealed interface ResolveAttendanceLinkResult {
    data class Success(val groupId: UUID, val gameId: UUID) : ResolveAttendanceLinkResult

    data class AttemptLimit(val retryAfterSeconds: Int) : ResolveAttendanceLinkResult

    data object InvalidOrExpired : ResolveAttendanceLinkResult

    data object Unavailable : ResolveAttendanceLinkResult
}

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
            if (target == null || target.status != GameStatus.PUBLISHED || now > target.confirmationDeadline) {
                repository.recordInvalidAttempt(
                    RecordInvalidAttendanceLinkAttempt(
                        actorId,
                        currentWindow.windowStartedAt,
                        currentWindow.invalidCount + 1,
                    ),
                )
                return@inTransaction ResolveAttendanceLinkResult.InvalidOrExpired
            }

            ResolveAttendanceLinkResult.Success(target.groupId, target.gameId)
        }
    }.getOrElse {
        ResolveAttendanceLinkResult.Unavailable
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
