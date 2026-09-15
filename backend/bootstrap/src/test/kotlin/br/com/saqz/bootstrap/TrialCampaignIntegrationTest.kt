package br.com.saqz.bootstrap

import br.com.saqz.groups.adapter.output.jdbc.group.create.JdbcGroupCreationRepository
import br.com.saqz.groups.adapter.output.jdbc.plan.JdbcOwnerGroupHistory
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupResult
import br.com.saqz.groups.domain.group.*
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.subscriptions.adapter.output.jdbc.*
import br.com.saqz.subscriptions.application.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.simple.JdbcClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.test.*

class TrialCampaignIntegrationTest {
    private lateinit var ds: DataSource
    private lateinit var jdbc: JdbcClient
    private lateinit var campaigns: JdbcTrialCampaignStore
    private lateinit var trials: JdbcOrganizerTrialRepository
    private lateinit var start: StartOrganizerTrial
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val profile = GroupProfileDefaultsInput(name = "Campaign", modality = GroupModality.COURT_VOLLEYBALL, composition = GroupComposition.MIXED)

    @BeforeEach fun setup() {
        ds = TestPostgres.migrated("classpath:db/migration", owner = this).dataSource
        jdbc = JdbcClient.create(ds)
        campaigns = JdbcTrialCampaignStore(ds, clock)
        trials = JdbcOrganizerTrialRepository(ds)
        start = StartOrganizerTrial(trials, JdbcOwnerGroupHistory(ds), JdbcPaidSubscriptionHistory(ds), clock, campaigns)
    }
    private fun owner(): UUID = UUID.randomUUID().also {
        jdbc.sql("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES (:id,:subject,true,'Owner',now(),now())")
            .param("id", it).param("subject", it.toString()).update()
    }
    private fun create(owner: UUID, key: UUID = UUID.randomUUID(), fail: Boolean = false) = CreateGroup(
        JdbcTransactionRunner(ds), JdbcGroupCreationRepository(ds) { if (fail) error("failure") },
        SubscriptionLimitsAdapter(SubscriptionPlanLookup { null }, trials, clock), start,
    ).execute(owner, key, profile, "UTC")
    private fun coupon(days: Int = 30, max: Int? = null, until: Instant? = null) =
        requireNotNull(campaigns.create("QUADRA", "Quadra A", days, until, max))
    private fun select(owner: UUID, code: String = "quadra") = campaigns.select(owner, code) { start.isFirstTrialEligible(owner) }

    @Test fun `web enrollment is durable and idempotent without starting the trial`() {
        val owner = owner()
        assertFalse(campaigns.isPreauthorized(owner))
        repeat(2) { assertTrue(campaigns.enroll(owner) { start.isFirstTrialEligible(owner) }) }
        assertTrue(JdbcTrialCampaignStore(ds, clock).isPreauthorized(owner))
        assertNull(trials.find(owner))
        assertFailsWith<IllegalStateException> { create(owner, fail = true) }
        assertNull(trials.find(owner))
        assertTrue(campaigns.isPreauthorized(owner))
        assertIs<CreateGroupResult.Success>(create(owner))
        assertEquals(now.plusSeconds(14 * 86400L), trials.find(owner)?.endsAt)
        assertFalse(campaigns.enroll(owner) { start.isFirstTrialEligible(owner) })
    }

    @Test fun `enrollment respects campaign mode and eligibility`() {
        val owner = owner()
        assertFalse(campaigns.enroll(owner) { false })
        for (mode in listOf(TrialOfferMode.OFF, TrialOfferMode.COUPON_ONLY)) {
            campaigns.setMode(mode)
            assertFalse(campaigns.enroll(owner) { true })
            assertFalse(campaigns.isPreauthorized(owner))
        }
    }

    @Test fun `default open grants fourteen days while off blocks and preserves existing trial`() {
        val owner = owner()
        assertEquals(TrialOfferMode.ON, campaigns.mode())
        assertIs<CreateGroupResult.Success>(create(owner))
        val granted = trials.find(owner)
        assertEquals(now.plusSeconds(14 * 86400L), granted?.endsAt)
        campaigns.setMode(TrialOfferMode.OFF)
        assertEquals(TrialOfferMode.OFF, JdbcTrialCampaignStore(ds, clock).mode())
        assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner()))
        assertEquals(granted, trials.find(owner))
    }
    @Test fun `coupon selection waits for first group then grants configured duration and keeps attribution`() {
        campaigns.setMode(TrialOfferMode.COUPON_ONLY)
        val coupon = coupon(45)
        val owner = owner()
        assertFalse(start.isEligible(owner))
        assertEquals(TrialCouponSelection.APPLIED, select(owner, " quadra "))
        assertNull(trials.find(owner))
        assertEquals(0L, campaigns.list().single().uses)
        assertTrue(start.isEligible(owner))
        val key = UUID.randomUUID()
        val created = assertIs<CreateGroupResult.Success>(create(owner, key))
        assertEquals(created, create(owner, key))
        assertEquals(now.plusSeconds(45 * 86400L), trials.find(owner)?.endsAt)
        assertEquals(1L, campaigns.list().single().uses)
        val snapshot = jdbc.sql("SELECT coupon_id,coupon_code,campaign FROM organizer_trials WHERE owner_user_id=:owner").param("owner", owner).query().singleRow()
        assertEquals(coupon.id, snapshot["coupon_id"])
        assertEquals("QUADRA", snapshot["coupon_code"])
        assertEquals("Quadra A", snapshot["campaign"])
        campaigns.deactivate(coupon.id)
        campaigns.setMode(TrialOfferMode.OFF)
        assertEquals(now.plusSeconds(45 * 86400L), trials.find(owner)?.endsAt)
        assertEquals(1L, campaigns.list().single().uses)
        assertEquals(TrialCouponSelection.INELIGIBLE, select(owner))
    }
    @Test fun `invalid expired deactivated and full coupons cannot release coupon only trial`() {
        campaigns.setMode(TrialOfferMode.COUPON_ONLY)
        val owner = owner()
        assertEquals(TrialCouponSelection.UNAVAILABLE, select(owner, "UNKNOWN"))
        val c = coupon(until = now)
        assertEquals(TrialCouponSelection.UNAVAILABLE, select(owner))
        assertFalse(start.isEligible(owner))
        assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner))
        assertTrue(campaigns.deactivate(c.id))
        assertFalse(campaigns.deactivate(UUID.randomUUID()))
        assertNull(campaigns.create("quadra", null, 14, null, null))
    }
    @Test fun `mode and coupon are rechecked after selection and public fallback has no campaign`() {
        val owner = owner()
        val c = coupon()
        assertEquals(TrialCouponSelection.APPLIED, select(owner))
        campaigns.setMode(TrialOfferMode.OFF)
        assertEquals(TrialCouponSelection.INELIGIBLE, select(owner()))
        assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner))
        campaigns.setMode(TrialOfferMode.COUPON_ONLY)
        campaigns.deactivate(c.id)
        assertEquals(CreateGroupResult.GroupLimitExceeded, create(owner))
        campaigns.setMode(TrialOfferMode.ON)
        assertIs<CreateGroupResult.Success>(create(owner))
        assertEquals(now.plusSeconds(14 * 86400L), trials.find(owner)?.endsAt)
        assertEquals(0L, campaigns.list().single().uses)
    }
    @Test fun `failed group rolls back trial and coupon use`() {
        coupon(max = 1)
        val owner = owner()
        select(owner)
        assertFailsWith<IllegalStateException> { create(owner, fail = true) }
        assertNull(trials.find(owner))
        assertEquals(0L, campaigns.list().single().uses)
        assertIs<CreateGroupResult.Success>(create(owner))
        assertEquals(1L, campaigns.list().single().uses)
    }
    @Test fun `two organizers competing for final use cannot exceed limit`() {
        campaigns.setMode(TrialOfferMode.COUPON_ONLY)
        coupon(days = 7, max = 1)
        val owners = listOf(owner(), owner())
        owners.forEach { assertEquals(TrialCouponSelection.APPLIED, select(it)) }
        val latch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val futures = owners.map { id -> executor.submit(Callable { latch.await(); create(id) }) }
            latch.countDown()
            val results = futures.map { it.get() }
            assertEquals(1, results.count { it is CreateGroupResult.Success })
            assertEquals(1, results.count { it == CreateGroupResult.GroupLimitExceeded })
            assertEquals(1L, campaigns.list().single().uses)
            assertEquals(1, owners.count { trials.find(it)?.endsAt == now.plusSeconds(7 * 86400L) })
            assertEquals(TrialCouponSelection.UNAVAILABLE, select(owner()))
        } finally { executor.shutdownNow() }
    }
}
