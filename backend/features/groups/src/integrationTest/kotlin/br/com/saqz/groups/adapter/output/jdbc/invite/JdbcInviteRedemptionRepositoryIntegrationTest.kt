package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.redeem.InviteAttemptWindow
import br.com.saqz.groups.application.invite.redeem.GroupAthleteOccupancy
import br.com.saqz.groups.application.invite.redeem.CreateEntryRequestCommand
import br.com.saqz.groups.application.invite.redeem.RecordInvalidInviteAttempt
import br.com.saqz.groups.application.invite.redeem.RedeemInvite
import br.com.saqz.groups.application.invite.redeem.RedeemInviteResult
import br.com.saqz.groups.application.invite.redeem.RedeemMembershipCommand
import br.com.saqz.groups.application.entryrequest.GroupEntryRequest
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.postgrestesting.TestPostgres
import br.com.saqz.sharedkernel.subscription.SubscriptionLimits
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInviteRedemptionRepositoryIntegrationTest {
    private val now = Instant.parse("2026-07-16T18:00:00Z")
    private val code = InviteCode.from(
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 11 }),
    )
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcInviteRedemptionRepository
    private lateinit var transaction: JdbcTransactionRunner

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations()).dataSource
        repository = JdbcInviteRedemptionRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE game_attendance, games, group_invites, group_entry_requests, group_membership_removals, group_memberships, " +
                "access_groups, invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `locking a new user attempt window initializes zero count`() {
        val user = insertUser("window-new")

        val window = transaction.inTransaction { repository.lockAttemptWindow(user, now) }

        assertEquals(InviteAttemptWindow(now, 0), window)
    }

    @Test
    fun `recording invalid attempt persists exact window state`() {
        val user = insertUser("record-invalid")

        transaction.inTransaction {
            repository.lockAttemptWindow(user, now)
            repository.recordInvalidAttempt(RecordInvalidInviteAttempt(user, now.minusSeconds(30), 7))
        }

        assertEquals(InviteAttemptWindow(now.minusSeconds(30), 7), storedWindow(user))
    }

    @Test
    fun `active invite lookup returns group by digest`() {
        val fixture = inviteFixture("lookup-active")

        val found = transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code)) }

        assertEquals(fixture.group, found?.groupId)
        assertEquals(now.plusSeconds(7 * 24 * 60 * 60), found?.expiresAt)
        assertEquals(false, found?.entryRequiresApproval)
    }

    @Test
    fun `active invite lookup returns the group approval setting`() {
        val fixture = inviteFixture("lookup-approval")
        execute("UPDATE access_groups SET entry_requires_approval = true WHERE id = '${fixture.group}'")

        val found = transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code)) }

        assertEquals(true, found?.entryRequiresApproval)
    }

    @Test
    fun `entry request uniqueness preserves the original requested timestamp`() {
        val fixture = inviteFixture("entry-request")
        val user = insertUser("entry-request-user")

        transaction.inTransaction {
            repository.createEntryRequest(CreateEntryRequestCommand(fixture.group, user, now))
            repository.createEntryRequest(CreateEntryRequestCommand(fixture.group, user, now.plusSeconds(30)))
        }

        assertEquals(1, number("SELECT count(*) FROM group_entry_requests WHERE group_id = '${fixture.group}' AND user_id = '$user'"))
        assertEquals(now, timestamp("SELECT requested_at FROM group_entry_requests WHERE group_id = '${fixture.group}' AND user_id = '$user'"))
    }

    @Test
    fun `entry request list and delete expose persisted identity and timestamp`() {
        val fixture = inviteFixture("entry-request-list")
        val user = insertUser("entry-request-list-user")

        transaction.inTransaction {
            repository.createEntryRequest(CreateEntryRequestCommand(fixture.group, user, now))
        }

        val listed = transaction.inTransaction { repository.list(fixture.group) }
        assertEquals(listOf(GroupEntryRequest(user, AccessName.from("Access Person"), now)), listed)
        assertEquals(listed.single(), transaction.inTransaction { repository.find(fixture.group, user) })

        transaction.inTransaction { repository.delete(fixture.group, user) }

        assertTrue(transaction.inTransaction { repository.list(fixture.group) }.isEmpty())
    }

    @Test
    fun `entry request queries hide a soft-deleted requester`() {
        val fixture = inviteFixture("entry-request-deleted")
        val user = insertUser("entry-request-deleted-user")

        transaction.inTransaction {
            repository.createEntryRequest(CreateEntryRequestCommand(fixture.group, user, now))
        }
        execute("UPDATE access_users SET deleted_at = now() WHERE id = '$user'")

        assertTrue(transaction.inTransaction { repository.list(fixture.group) }.isEmpty())
        assertNull(transaction.inTransaction { repository.find(fixture.group, user) })
    }

    @Test
    fun `valid invite lookup keeps deleted group distinguishable from invalid invite`() {
        val fixture = inviteFixture("lookup-deleted")
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '${fixture.group}'")

        val found = transaction.inTransaction { repository.findInvite(InviteTokenDigest.sha256(code)) }

        assertEquals(fixture.group, found?.groupId)
        assertEquals(true, found?.groupDeleted)
        assertEquals(
            RedeemInviteResult.GroupDeleted,
            useCase().execute(insertUser("lookup-deleted-user"), code.value),
        )
    }

    @Test
    fun `occupancy lock rechecks deletion after invite lookup`() {
        val fixture = inviteFixture("lookup-race")
        val lookupDone = CountDownLatch(1)
        val continueLookup = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        try {
            val future = executor.submit<Pair<Boolean, GroupAthleteOccupancy?>> {
                transaction.inTransaction {
                    val found = requireNotNull(repository.findInvite(InviteTokenDigest.sha256(code)))
                    lookupDone.countDown()
                    continueLookup.await()
                    found.groupDeleted to repository.loadAthleteOccupancy(fixture.group)
                }
            }

            lookupDone.await()
            execute("UPDATE access_groups SET deleted_at = now() WHERE id = '${fixture.group}'")
            continueLookup.countDown()

            val result = future.get()
            assertFalse(result.first)
            assertNull(result.second)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `unknown digest returns no invite`() {
        inviteFixture("lookup-missing")
        val unknown = InviteTokenDigest.from(ByteArray(32) { 99.toByte() })

        assertNull(transaction.inTransaction { repository.findInvite(unknown) })
    }

    @Test
    fun `new user redeems as athlete`() {
        val fixture = inviteFixture("new-athlete")
        val user = insertUser("new-athlete-user")

        val role = transaction.inTransaction {
            repository.redeemMembership(RedeemMembershipCommand(fixture.group, user))
        }

        assertEquals(GroupRole.ATHLETE, role)
        assertEquals("ATHLETE", membershipRole(fixture.group, user))
    }

    @Test
    fun `owner redemption preserves owner without membership row`() {
        val fixture = inviteFixture("owner-preserved")

        val role = transaction.inTransaction {
            repository.redeemMembership(RedeemMembershipCommand(fixture.group, fixture.owner))
        }

        assertEquals(GroupRole.OWNER, role)
        assertEquals(0, membershipCount(fixture.group, fixture.owner))
    }

    @Test
    fun `admin redemption preserves admin`() {
        val fixture = inviteFixture("admin-preserved")
        val admin = insertUser("admin-preserved-user")
        insertMembership(fixture.group, admin, "ADMIN")

        val role = transaction.inTransaction {
            repository.redeemMembership(RedeemMembershipCommand(fixture.group, admin))
        }

        assertEquals(GroupRole.ADMIN, role)
        assertEquals("ADMIN", membershipRole(fixture.group, admin))
    }

    @Test
    fun `athlete retry remains one athlete row`() {
        val fixture = inviteFixture("athlete-retry")
        val athlete = insertUser("athlete-retry-user")
        insertMembership(fixture.group, athlete, "ATHLETE")

        repeat(2) {
            transaction.inTransaction {
                repository.redeemMembership(RedeemMembershipCommand(fixture.group, athlete))
            }
        }

        assertEquals(1, membershipCount(fixture.group, athlete))
        assertEquals("ATHLETE", membershipRole(fixture.group, athlete))
    }

    @Test
    fun `redemption never consumes active invite`() {
        val fixture = inviteFixture("reusable")
        val user = insertUser("reusable-user")

        transaction.inTransaction {
            repository.redeemMembership(RedeemMembershipCommand(fixture.group, user))
        }

        assertContentEquals(InviteTokenDigest.sha256(code).toByteArray(), inviteDigest(fixture.group))
    }

    @Test
    fun `two users redeem same invite concurrently as athletes`() {
        val fixture = inviteFixture("parallel-users")
        val users = listOf(insertUser("parallel-user-one"), insertUser("parallel-user-two"))

        val results = concurrent(users.map { user -> Callable { useCase().execute(user, code.value) } })

        assertEquals(2, results.size)
        results.forEach { assertEquals(RedeemInviteResult.Success(fixture.group, GroupRole.ATHLETE), it) }
        users.forEach { assertEquals("ATHLETE", membershipRole(fixture.group, it)) }
        assertEquals(1, inviteCount(fixture.group))
    }

    @Test
    fun `concurrent retries by same user leave one membership`() {
        val fixture = inviteFixture("parallel-retry")
        val user = insertUser("parallel-retry-user")

        val results = concurrent(List(2) { Callable { useCase().execute(user, code.value) } })

        results.forEach { assertEquals(RedeemInviteResult.Success(fixture.group, GroupRole.ATHLETE), it) }
        assertEquals(1, membershipCount(fixture.group, user))
    }

    @Test
    fun `eleventh invalid redemption is limited without membership`() {
        val actor = insertUser("limit-eleven")
        val useCase = useCase()

        repeat(10) { assertEquals(RedeemInviteResult.InvalidOrExpired, useCase.execute(actor, code.value)) }
        val limited = useCase.execute(actor, code.value)

        assertEquals(RedeemInviteResult.AttemptLimit(600), limited)
        assertEquals(10, storedWindow(actor)?.invalidCount)
        assertEquals(0, membershipCountForUser(actor))
    }

    @Test
    fun `concurrent invalid boundary permits tenth and limits eleventh`() {
        val actor = insertUser("parallel-limit")
        insertWindow(actor, now, 9)

        val results = concurrent(List(2) { Callable { useCase().execute(actor, code.value) } })

        assertEquals(1, results.count { it === RedeemInviteResult.InvalidOrExpired })
        assertEquals(1, results.count { it is RedeemInviteResult.AttemptLimit })
        assertEquals(10, storedWindow(actor)?.invalidCount)
    }

    @Test
    fun `transaction rollback removes both rate and membership mutations`() {
        val fixture = inviteFixture("rollback")
        val user = insertUser("rollback-user")

        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                repository.lockAttemptWindow(user, now)
                repository.recordInvalidAttempt(RecordInvalidInviteAttempt(user, now, 1))
                repository.redeemMembership(RedeemMembershipCommand(fixture.group, user))
                error("rollback")
            }
        }

        assertNull(storedWindow(user))
        assertEquals(0, membershipCount(fixture.group, user))
    }

    @Test
    fun `removed member still waitlisted on a group game occupies an athlete slot`() {
        val fixture = inviteFixture("waitlist-occupancy")
        val removed = insertUser("waitlist-removed")
        insertMembership(fixture.group, removed, "ATHLETE")
        val game = insertGame(fixture.group)
        insertWaitlistedAttendance(game, fixture.group, removed, sequence = 1)
        execute("DELETE FROM group_memberships WHERE group_id = '${fixture.group}' AND user_id = '$removed'")

        val occupancy = transaction.inTransaction { repository.loadAthleteOccupancy(fixture.group) }

        assertEquals(setOf(removed), occupancy!!.openWaitlistIds)
        assertTrue(removed !in occupancy.openMemberIds)
    }

    private data class InviteFixture(val owner: UUID, val group: UUID)

    private fun useCase() = RedeemInvite(
        transaction,
        repository,
        UnlimitedSubscriptionLimits,
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private object UnlimitedSubscriptionLimits : SubscriptionLimits {
        override fun groupLimitFor(ownerId: UUID): Int? = null
        override fun athleteLimitFor(ownerId: UUID): Int? = null
    }

    private fun inviteFixture(prefix: String): InviteFixture {
        val owner = insertUser("$prefix-owner")
        val group = insertGroup(owner)
        insertInvite(group, owner, InviteTokenDigest.sha256(code))
        return InviteFixture(owner, group)
    }

    private fun <T> concurrent(calls: List<Callable<T>>): List<T> {
        val ready = CountDownLatch(calls.size)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(calls.size)
        return try {
            val futures = calls.map { call ->
                pool.submit<T> {
                    ready.countDown()
                    start.await()
                    call.call()
                }
            }
            ready.await()
            start.countDown()
            futures.map { it.get() }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, 'Access Person', now(), now())",
        )
        return id
    }

    private fun insertGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Treino', DATE '2026-08-12', TIME '19:30', 'America/Sao_Paulo', " +
                "TIMESTAMPTZ '2026-08-12 22:30Z', 90, TIMESTAMPTZ '2026-08-11 22:30Z', 'Arena', 'Rua Central 100', " +
                "12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertWaitlistedAttendance(game: UUID, group: UUID, member: UUID, sequence: Int) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, waitlist_sequence, " +
            "responded_at, updated_at, version, member_display_name) VALUES " +
            "('$game', '$group', '$member', 'WAITLISTED', $sequence, now(), now(), 1, 'Waitlisted Person')",
    )

    private fun insertInvite(group: UUID, creator: UUID, digest: InviteTokenDigest) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                    "VALUES (?, ?, ?, now(), ?)",
            ).use { statement ->
                statement.setObject(1, group)
                statement.setBytes(2, digest.toByteArray())
                statement.setObject(3, creator)
                statement.setTimestamp(4, Timestamp.from(now.plusSeconds(7 * 24 * 60 * 60)))
                statement.executeUpdate()
            }
        }
    }

    private fun insertWindow(user: UUID, startedAt: Instant, count: Int) {
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO invite_redemption_limits (user_id, window_started_at, invalid_count) VALUES (?, ?, ?)",
            ).use { statement ->
                statement.setObject(1, user)
                statement.setTimestamp(2, Timestamp.from(startedAt))
                statement.setInt(3, count)
                statement.executeUpdate()
            }
        }
    }

    private fun storedWindow(user: UUID): InviteAttemptWindow? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT window_started_at, invalid_count FROM invite_redemption_limits WHERE user_id = ?",
        ).use { statement ->
            statement.setObject(1, user)
            statement.executeQuery().use { result ->
                if (result.next()) InviteAttemptWindow(result.getTimestamp(1).toInstant(), result.getInt(2)) else null
            }
        }
    }

    private fun inviteDigest(group: UUID): ByteArray? = bytes("SELECT token_digest FROM group_invites WHERE group_id = '$group'")
    private fun membershipRole(group: UUID, user: UUID): String? = text("SELECT role FROM group_memberships WHERE group_id = '$group' AND user_id = '$user'")
    private fun membershipCount(group: UUID, user: UUID) = number("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$user'")
    private fun membershipCountForUser(user: UUID) = number("SELECT count(*) FROM group_memberships WHERE user_id = '$user'")
    private fun inviteCount(group: UUID) = number("SELECT count(*) FROM group_invites WHERE group_id = '$group'")
    private fun timestamp(sql: String): Instant = connection().use {
        it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getTimestamp(1).toInstant() } }
    }
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun text(sql: String): String? = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> if (result.next()) result.getString(1) else null } } }
    private fun bytes(sql: String): ByteArray? = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> if (result.next()) result.getBytes(1) else null } } }
    private fun number(sql: String): Int = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } } }
    private fun connection(): Connection = dataSource.connection
}
