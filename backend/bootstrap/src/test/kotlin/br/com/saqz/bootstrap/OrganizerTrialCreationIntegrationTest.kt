package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerGroupHistory
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupResult
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcOrganizerTrialRepository
import br.com.saqz.subscriptions.adapter.output.jdbc.JdbcPaidSubscriptionHistory
import br.com.saqz.subscriptions.application.StartOrganizerTrial
import br.com.saqz.subscriptions.application.SubscriptionLimitsAdapter
import br.com.saqz.subscriptions.application.SubscriptionPlanLookup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class OrganizerTrialCreationIntegrationTest {
    private lateinit var ds: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var trials: JdbcOrganizerTrialRepository
    private lateinit var start: StartOrganizerTrial
    private val owner = UUID.randomUUID()
    private val now = Instant.parse("2026-09-12T12:00:00Z")
    private val profile = GroupProfileDefaultsInput(name = "Trial Group", modality = GroupModality.COURT_VOLLEYBALL, composition = GroupComposition.MIXED)

    @BeforeEach
    fun setup() {
        ds = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        jdbc = JdbcClient.create(ds)
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id, :subject, true, 'Owner', now(), now())")
            .param("id", owner).param("subject", owner.toString()).update()
        trials = JdbcOrganizerTrialRepository(ds)
        start = StartOrganizerTrial(trials, JdbcOwnerGroupHistory(ds), JdbcPaidSubscriptionHistory(ds), Clock.fixed(now, ZoneOffset.UTC))
    }

    private fun create(fail: Boolean = false) = CreateGroup(
        JdbcTransactionRunner(ds), JdbcGroupCreationRepository(ds) { if (fail) error("injected group failure") },
        SubscriptionLimitsAdapter(SubscriptionPlanLookup { null }), start,
    )

    @Test
    fun `first group starts trial once and idempotent retry never renews it`() {
        val request = UUID.randomUUID()
        val first = assertIs<CreateGroupResult.Success>(create().execute(owner, request, profile, "UTC"))
        assertEquals(now, trials.find(owner)?.startedAt)
        assertEquals(now.plus(Duration.ofDays(14)), trials.find(owner)?.endsAt)
        assertEquals(first, create().execute(owner, request, profile, "UTC"))
        assertEquals(1, count("access_groups"))
        assertEquals(1, count("organizer_trials"))
        assertEquals(0, count("subscriptions"))
    }

    @Test
    fun `invalid input and failed transaction do not consume the trial`() {
        assertIs<CreateGroupResult.Invalid>(create().execute(owner, UUID.randomUUID(), profile.copy(name = ""), "UTC"))
        assertNull(trials.find(owner))
        assertFailsWith<IllegalStateException> { create(true).execute(owner, UUID.randomUUID(), profile, "UTC") }
        assertNull(trials.find(owner))
        assertEquals(0, count("access_groups"))
        assertIs<CreateGroupResult.Success>(create().execute(owner, UUID.randomUUID(), profile, "UTC"))
    }

    @Test
    fun `concurrent distinct requests create only one group and one trial`() {
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requests = (1..2).map { executor.submit(Callable { latch.await(); create().execute(owner, UUID.randomUUID(), profile, "UTC") }) }
            latch.countDown()
            val results = requests.map { it.get() }
            assertEquals(1, results.count { it is CreateGroupResult.Success })
            assertEquals(1, results.count { it == CreateGroupResult.GroupLimitExceeded })
            assertEquals(1, count("access_groups"))
            assertEquals(1, count("organizer_trials"))
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `deleting an existing group cannot grant a new trial`() {
        val group = assertIs<CreateGroupResult.Success>(create().execute(owner, UUID.randomUUID(), profile, "UTC")).group
        jdbc.sql("UPDATE access_groups SET deleted_at = now() WHERE id = :id").param("id", group.id).update()
        assertEquals(CreateGroupResult.GroupLimitExceeded, create().execute(owner, UUID.randomUUID(), profile, "UTC"))
        assertEquals(now, trials.find(owner)?.startedAt)
        // A legacy group also disqualifies an organizer even without a trial record.
        jdbc.sql("DELETE FROM organizer_trials WHERE owner_user_id = :owner").param("owner", owner).update()
        assertEquals(CreateGroupResult.GroupLimitExceeded, create().execute(owner, UUID.randomUUID(), profile, "UTC"))
        assertNull(trials.find(owner))
    }

    private fun count(table: String) = jdbc.sql("SELECT count(*)::int FROM $table").query(Int::class.java).single()
}
