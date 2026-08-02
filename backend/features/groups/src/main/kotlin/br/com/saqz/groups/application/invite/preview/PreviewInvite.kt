package br.com.saqz.groups.application.invite.preview

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PreviewInvite(
    private val transactionRunner: TransactionRunner,
    private val repository: PreviewInviteRepository,
    private val anonymousRateLimiter: AnonymousInvitePreviewRateLimiter,
    private val clock: Clock,
) {
    fun execute(actor: UUID?, ipAddress: String, rawCode: String): PreviewInviteResult =
        transactionRunner.inTransaction {
            val now = clock.instant()
            val authenticatedWindow = actor?.let { repository.lockAttemptWindow(it, now) }
            val currentWindow = authenticatedWindow?.let { currentWindow(it, now) }
            val authenticatedRetry = currentWindow?.let { retryAfterSeconds(now, it) }
            val anonymousRetry = actor?.let { null } ?: anonymousRateLimiter.retryAfterSeconds(ipAddress, now)
            val retryAfter = authenticatedRetry ?: anonymousRetry
            if (retryAfter != null) return@inTransaction PreviewInviteResult.AttemptLimit(retryAfter)

            val code = runCatching { InviteCode.from(rawCode) }.getOrNull()
            val invite = code?.let { repository.findInvite(InviteTokenDigest.sha256(it), now) }
            when {
                invite == null -> {
                    recordInvalidAttempt(actor, ipAddress, currentWindow, now)
                    PreviewInviteResult.Invalid
                }

                invite.groupDeleted -> PreviewInviteResult.Invalid
                invite.expiredAt != null -> PreviewInviteResult.Expired(invite.expiredAt)
                else -> PreviewInviteResult.Success(invite.card)
            }
        }

    private fun recordInvalidAttempt(
        actor: UUID?,
        ipAddress: String,
        currentWindow: PreviewInviteAttemptWindow?,
        now: Instant,
    ) {
        if (actor == null) {
            anonymousRateLimiter.recordInvalid(ipAddress, now)
            return
        }
        val window = requireNotNull(currentWindow)
        repository.recordInvalidAttempt(
            RecordInvalidPreviewInviteAttempt(actor, window.windowStartedAt, window.invalidCount + 1),
        )
    }

    private fun currentWindow(window: PreviewInviteAttemptWindow, now: Instant): PreviewInviteAttemptWindow =
        window.takeUnless { now >= it.windowStartedAt.plus(WINDOW) } ?: PreviewInviteAttemptWindow(now, 0)

    private fun retryAfterSeconds(now: Instant, window: PreviewInviteAttemptWindow): Int? {
        if (window.invalidCount < MAX_INVALID_ATTEMPTS) return null
        val remainingMillis = Duration.between(now, window.windowStartedAt.plus(WINDOW)).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    private companion object {
        const val MAX_INVALID_ATTEMPTS = 10
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}

/**
 * Process-local anonymous protection. It is intentionally independent from the database-backed
 * authenticated window because anonymous requests do not have a stable user id.
 * ponytail: the 30-attempt/10-minute ceiling is for one instance; move this counter to a shared
 * table-backed limiter if the API is deployed across multiple instances.
 */
class AnonymousInvitePreviewRateLimiter {
    private val windows = mutableMapOf<String, Window>()

    @Synchronized
    fun retryAfterSeconds(ipAddress: String, now: Instant): Int? {
        val window = activeWindow(ipAddress, now) ?: return null
        if (window.invalidCount < MAX_INVALID_ATTEMPTS) return null
        val remainingMillis = Duration.between(now, window.startedAt.plus(WINDOW)).toMillis()
        return ((remainingMillis + 999) / 1_000).coerceAtLeast(1).toInt()
    }

    @Synchronized
    fun recordInvalid(ipAddress: String, now: Instant) {
        windows.entries.removeIf { now >= it.value.startedAt.plus(WINDOW) }
        val current = activeWindow(ipAddress, now)
        val startedAt = current?.startedAt ?: now
        windows[ipAddress] = Window(startedAt, (current?.invalidCount ?: 0) + 1)
    }

    @Synchronized
    fun clear() {
        windows.clear()
    }

    private fun activeWindow(ipAddress: String, now: Instant): Window? {
        val current = windows[ipAddress] ?: return null
        if (now >= current.startedAt.plus(WINDOW)) {
            windows.remove(ipAddress)
            return null
        }
        return current
    }

    private data class Window(
        val startedAt: Instant,
        val invalidCount: Int,
    )

    private companion object {
        const val MAX_INVALID_ATTEMPTS = 30
        val WINDOW: Duration = Duration.ofMinutes(10)
    }
}
