package br.com.saqz.groups.application.invite.redeem

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RedeemInvite(
    private val transactionRunner: TransactionRunner,
    private val repository: InviteRedemptionRepository,
    private val clock: Clock,
) {
    fun execute(actor: UUID, rawCode: String): RedeemInviteResult = transactionRunner.inTransaction {
        val now = clock.instant()
        val storedWindow = repository.lockAttemptWindow(actor, now)
        val currentWindow = storedWindow
            .takeUnless { now >= it.windowStartedAt.plus(WINDOW) }
            ?: InviteAttemptWindow(now, 0)
        if (currentWindow.invalidCount == MAX_INVALID_ATTEMPTS) {
            return@inTransaction RedeemInviteResult.AttemptLimit(retryAfterSeconds(now, currentWindow))
        }

        val code = runCatching { InviteCode.from(rawCode) }.getOrNull()
        val invite = code?.let { repository.findInvite(InviteTokenDigest.sha256(it)) }
        if (invite == null) {
            repository.recordInvalidAttempt(
                RecordInvalidInviteAttempt(
                    actor,
                    currentWindow.windowStartedAt,
                    currentWindow.invalidCount + 1,
                ),
            )
            return@inTransaction RedeemInviteResult.InvalidOrExpired
        }

        val role = repository.redeemMembership(RedeemMembershipCommand(invite.groupId, actor))
        RedeemInviteResult.Success(invite.groupId, role)
    }

    private fun retryAfterSeconds(now: Instant, window: InviteAttemptWindow): Int {
        val remainingMillis = Duration.between(now, window.windowStartedAt.plus(WINDOW)).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    private companion object {
        const val MAX_INVALID_ATTEMPTS = 10
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
