package br.com.saqz.subscriptions.adapter.output.jdbc

import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.domain.OrganizerTrial
import br.com.saqz.subscriptions.testing.allSubscriptionsFeatureMigrationLocations
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JdbcTrialEndingRepositoryIntegrationTest {
    private val source = TestPostgres.migrated(*allSubscriptionsFeatureMigrationLocations(), owner = this).dataSource
    private val jdbc = JdbcClient.create(source)
    private val trials = JdbcOrganizerTrialRepository(source)
    private val repository = JdbcTrialEndingRepository(source)
    private val now = Instant.parse("2026-10-12T13:00:00Z")

    @Test
    fun `lista os testes que terminam na janela e os que acabaram ha pouco`() {
        val soon = trial(now.minus(Duration.ofDays(11))) // termina em 3 dias
        val ended = trial(now.minus(Duration.ofDays(15))) // acabou ontem
        val longAgo = trial(now.minus(Duration.ofDays(40)))
        val far = trial(now)
        val ids = repository.around(now, Duration.ofDays(4), Duration.ofDays(16)).map { it.ownerUserId }
        assertEquals(listOf(ended.ownerUserId, soon.ownerUserId), ids)
        assertFalse(longAgo.ownerUserId in ids)
        assertFalse(far.ownerUserId in ids)
    }

    @Test
    fun `os marcos de depois do fim (-3 e -14) tem reserva propria`() {
        val owner = trial(now.minus(Duration.ofDays(20))).ownerUserId

        assertTrue(repository.claim(owner, listOf(3, 1, 0, -3)))
        assertFalse(repository.claim(owner, listOf(3, 1, 0, -3)))
        assertTrue(repository.claim(owner, listOf(3, 1, 0, -3, -14)))
        assertEquals(setOf<Short>(3, 1, 0, -3, -14), milestones(owner))
        repository.release(owner, -14)
        assertEquals(setOf<Short>(3, 1, 0, -3), milestones(owner))
    }

    @Test
    fun `reserva e idempotente e so o marco mais apertado libera o aviso`() {
        val owner = trial(now.minus(Duration.ofDays(11))).ownerUserId

        assertTrue(repository.claim(owner, listOf(3)))
        assertFalse(repository.claim(owner, listOf(3)))
        assertTrue(repository.claim(owner, listOf(3, 1)))
        assertFalse(repository.claim(owner, listOf(3, 1)))
        assertEquals(setOf<Short>(3, 1), milestones(owner))

        repository.release(owner, 1)
        assertEquals(setOf<Short>(3), milestones(owner))
        assertTrue(repository.claim(owner, listOf(3, 1)))
    }

    @Test
    fun `quem perde varios marcos de uma vez reserva todos e avisa uma vez`() {
        val owner = trial(now.minus(Duration.ofDays(13))).ownerUserId

        assertTrue(repository.claim(owner, listOf(3, 1, 0)))
        assertFalse(repository.claim(owner, listOf(3, 1, 0)))
        assertEquals(setOf<Short>(3, 1, 0), milestones(owner))
    }

    private fun milestones(owner: UUID) =
        jdbc.sql("SELECT milestone FROM organizer_trial_notices WHERE owner_user_id = :o").param("o", owner)
            .query(Short::class.java).set()

    /** Teste que começou em [startedAt] e dura 14 dias. */
    private fun trial(startedAt: Instant): OrganizerTrial {
        val owner = UUID.randomUUID()
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Owner', now(), now())")
            .param("id", owner).param("subject", owner.toString()).update()
        return OrganizerTrial.start(owner, startedAt).also(trials::insert)
    }
}
