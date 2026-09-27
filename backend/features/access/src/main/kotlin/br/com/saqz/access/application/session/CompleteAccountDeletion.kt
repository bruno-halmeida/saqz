package br.com.saqz.access.application.session

import java.time.Instant
import java.util.UUID

data class AccountDeletionWork(val userId: UUID, val subject: String, val attempt: Int)

interface AccountDeletionJobs {
    fun claim(now: Instant): AccountDeletionWork?
    fun complete(work: AccountDeletionWork, now: Instant)
    fun retry(work: AccountDeletionWork, nextAttemptAt: Instant)
}

interface AccountDeletionProviders {
    /** Idempotent: absence/already canceled is success. Stop billing before removing identity. */
    fun cancelSubscription(userId: UUID)
    fun deleteIdentity(subject: String)
}

class CompleteAccountDeletion(
    private val jobs: AccountDeletionJobs,
    private val providers: AccountDeletionProviders,
) {
    fun runNext(now: Instant): Boolean {
        val work = jobs.claim(now) ?: return false
        try {
            providers.cancelSubscription(work.userId)
            providers.deleteIdentity(work.subject)
            jobs.complete(work, now)
        } catch (failure: Exception) {
            jobs.retry(work, now.plusSeconds(60))
            if (failure is InterruptedException) Thread.currentThread().interrupt()
        }
        return true
    }
}

class AccountDeletionIdentityMismatch : RuntimeException()
class AccountDeleted : RuntimeException()
