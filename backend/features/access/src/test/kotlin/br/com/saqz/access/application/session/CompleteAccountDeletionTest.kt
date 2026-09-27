package br.com.saqz.access.application.session

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CompleteAccountDeletionTest {
    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val userId = UUID.randomUUID()

    @Test
    fun `successful work cancels billing and removes identity before completing deletion`() {
        val jobs = Jobs(AccountDeletionWork(userId, "firebase-subject", 1))
        val providers = Providers()
        assertTrue(CompleteAccountDeletion(jobs, providers).runNext(now))
        assertEquals(listOf("subscription:$userId", "identity:firebase-subject"), providers.deleted)
        assertEquals(now, jobs.completedAt)
        assertNull(jobs.retryAt)
    }

    @Test
    fun `provider outage leaves a durable retry instead of claiming deletion completed`() {
        val jobs = Jobs(AccountDeletionWork(userId, "firebase-subject", 1))
        val providers = Providers().apply { failIdentity = true }
        val useCase = CompleteAccountDeletion(jobs, providers)
        useCase.runNext(now)
        assertNull(jobs.completedAt)
        assertEquals(now.plusSeconds(60), jobs.retryAt)
        providers.failIdentity = false
        useCase.runNext(now.plusSeconds(60))
        assertEquals(now.plusSeconds(60), jobs.completedAt)
        assertEquals(listOf("subscription:$userId", "identity:firebase-subject"), providers.deleted.distinct())
    }

    @Test
    fun `billing failure does not remove identity or finish the job`() {
        val jobs = Jobs(AccountDeletionWork(userId, "firebase-subject", 1))
        val providers = Providers().apply { failSubscription = true }
        CompleteAccountDeletion(jobs, providers).runNext(now)
        assertTrue(providers.deleted.isEmpty())
        assertNull(jobs.completedAt)
        assertEquals(now.plusSeconds(60), jobs.retryAt)
    }

    @Test
    fun `empty queue has no provider side effects`() {
        val jobs = Jobs(null)
        val providers = Providers()
        assertFalse(CompleteAccountDeletion(jobs, providers).runNext(now))
        assertTrue(providers.deleted.isEmpty())
    }

    private class Jobs(val work: AccountDeletionWork?) : AccountDeletionJobs {
        var completedAt: Instant? = null
        var retryAt: Instant? = null
        override fun claim(now: Instant) = work.takeIf { completedAt == null }
        override fun complete(work: AccountDeletionWork, now: Instant) { completedAt = now }
        override fun retry(work: AccountDeletionWork, nextAttemptAt: Instant) { retryAt = nextAttemptAt }
    }

    private class Providers : AccountDeletionProviders {
        var failIdentity = false
        var failSubscription = false
        val deleted = mutableListOf<String>()
        override fun cancelSubscription(userId: UUID) {
            if (failSubscription) error("provider unavailable")
            deleted += "subscription:$userId"
        }
        override fun deleteIdentity(subject: String) {
            if (failIdentity) error("provider unavailable")
            deleted += "identity:$subject"
        }
    }
}
