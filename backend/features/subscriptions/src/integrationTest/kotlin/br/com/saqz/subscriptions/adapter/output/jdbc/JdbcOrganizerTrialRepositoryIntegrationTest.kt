package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.domain.OrganizerTrial
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JdbcOrganizerTrialRepositoryIntegrationTest {
    @Test
    fun `trial is persisted once per organizer and rollback does not consume it`() {
        val ds = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
        val jdbc = JdbcClient.create(ds)
        val owner = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Owner', now(), now())")
            .param("id", owner).param("subject", owner.toString()).update()
        val repository = JdbcOrganizerTrialRepository(ds)
        val transaction = JdbcSubscriptionsTransactionRunner(ds)
        val trial = OrganizerTrial.start(owner, Instant.parse("2026-09-12T12:00:00Z"))
        assertNull(repository.find(owner))
        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                repository.insert(trial)
                error("failed group creation")
            }
        }
        assertNull(repository.find(owner))
        repository.insert(trial)
        repository.insert(OrganizerTrial.start(owner, trial.endsAt))
        assertEquals(trial, repository.find(owner))
        assertEquals(0, jdbc.sql("SELECT count(*)::int FROM subscriptions").query(Int::class.java).single())
    }
}
