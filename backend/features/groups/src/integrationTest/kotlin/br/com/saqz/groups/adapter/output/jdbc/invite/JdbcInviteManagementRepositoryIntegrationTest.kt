package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteCode
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import br.com.saqz.postgrestesting.TestPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInviteManagementRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcInviteManagementRepository
    private lateinit var transaction: JdbcTransactionRunner
    private val legacyCreatedAt = Instant.parse("2026-07-01T10:00:00Z")

    @BeforeAll
    fun startDatabase() {
        dataSource = TestPostgres.empty().dataSource
        val flyway = Flyway.configure().dataSource(dataSource)
            .locations(*allGroupFeatureMigrationLocations())
            .target("23")
            .load()
        flyway.migrate()
        val legacy = fixture("migration-backfill")
        execute(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at) " +
                "VALUES ('${legacy.group}', decode('010203', 'hex'), '${legacy.owner}', TIMESTAMPTZ '$legacyCreatedAt')",
        )
        Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).load().migrate()
        assertEquals(legacyCreatedAt.plus(Duration.ofDays(7)), invite(legacy.group)?.expiresAt)
        assertNotNull(invite(legacy.group)?.revision)
        repository = JdbcInviteManagementRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_invites, group_memberships, access_groups, invite_redemption_limits, access_users CASCADE")
    }

    @Test
    fun `permanent invite round trips with its revision and replacement invalidates old code`() {
        val fixture = fixture("permanent")
        val first = command(fixture, code(1), null)
        transaction.inTransaction { repository.rotate(first) }

        assertNull(invite(fixture.group)!!.expiresAt)
        assertEquals(first.revision, invite(fixture.group)!!.revision)
        val metadata = assertNotNull(repository.findMetadata(fixture.group))
        assertNull(metadata.expiresAt)
        assertEquals(first.revision, metadata.revision)
        assertEquals("Owner Person", metadata.createdByName)
        val redemption = JdbcInviteRedemptionRepository(dataSource)
        val redeemable = assertNotNull(redemption.findInvite(InviteTokenDigest.sha256(first.code)))
        assertEquals(fixture.group, redeemable.groupId)
        assertNull(redeemable.expiresAt)

        val second = command(fixture, code(2), null)
        transaction.inTransaction { repository.rotate(second) }
        assertNotEquals(first.revision, second.revision)
        assertEquals(second.revision, repository.findMetadata(fixture.group)!!.revision)
        assertEquals(1, inviteCount(fixture.group))
        assertNull(redemption.findInvite(InviteTokenDigest.sha256(first.code)))
        assertNull(assertNotNull(redemption.findInvite(InviteTokenDigest.sha256(second.code))).expiresAt)

        transaction.inTransaction { repository.expire(fixture.group) }
        assertNull(redemption.findInvite(InviteTokenDigest.sha256(second.code)))
        assertNull(repository.findMetadata(fixture.group))
    }

    @Test
    fun `rotation drops the legacy long token and keeps only the new short code`() {
        val fixture = fixture("legacy-rotate")
        val legacy = InviteTokenDigest.from(ByteArray(32) { it.toByte() })
        insertLegacy(fixture, legacy)
        val redemption = JdbcInviteRedemptionRepository(dataSource)
        assertNotNull(redemption.findInvite(legacy))

        rotate(fixture, code(1))

        assertNull(redemption.findInvite(legacy))
        assertNull(invite(fixture.group)!!.digest)
        assertNotNull(redemption.findInvite(InviteTokenDigest.sha256(code(1))))
    }

    @Test
    fun `ensuring a short code keeps the legacy long token valid`() {
        val fixture = fixture("legacy-ensure")
        val legacy = InviteTokenDigest.from(ByteArray(32) { (it + 5).toByte() })
        insertLegacy(fixture, legacy)
        val revision = repository.findMetadata(fixture.group)!!.revision
        assertNull(repository.findMetadata(fixture.group)!!.shortCode)

        transaction.inTransaction { repository.ensureShortCode(fixture.group, code(3).value, fixture.owner) }
        // Segunda chamada não troca o código já gravado.
        transaction.inTransaction { repository.ensureShortCode(fixture.group, code(4).value, fixture.owner) }

        val metadata = repository.findMetadata(fixture.group)!!
        assertEquals(code(3).value, metadata.shortCode)
        assertEquals(revision, metadata.revision)
        val redemption = JdbcInviteRedemptionRepository(dataSource)
        assertEquals(fixture.group, redemption.findInvite(legacy)!!.groupId)
        assertEquals(fixture.group, redemption.findInvite(InviteTokenDigest.sha256(code(3)))!!.groupId)
    }

    @Test
    fun `ensuring a short code on a group without invite creates one`() {
        val fixture = fixture("ensure-new")

        transaction.inTransaction { repository.ensureShortCode(fixture.group, code(1).value, fixture.owner) }

        assertEquals(code(1).value, invite(fixture.group)!!.shortCode)
        assertNull(invite(fixture.group)!!.digest)
        assertNull(invite(fixture.group)!!.expiresAt)
        assertEquals(fixture.owner, invite(fixture.group)!!.creator)
    }

    @Test
    fun `short code is taken only while an active invite uses it`() {
        val fixture = fixture("taken")
        assertFalse(repository.isShortCodeTaken(code(1).value))

        rotate(fixture, code(1))
        assertTrue(repository.isShortCodeTaken(code(1).value))

        rotate(fixture, code(2))
        assertFalse(repository.isShortCodeTaken(code(1).value))
    }

    @Test
    fun `first rotation inserts exact code and creator`() {
        val fixture = fixture("insert")
        val code = code(1)
        val expiresAt = futureExpiry()

        transaction.inTransaction {
            repository.rotate(command(fixture, code, expiresAt))
        }

        val stored = requireNotNull(invite(fixture.group))
        assertEquals(code.value, stored.shortCode)
        assertNull(stored.digest)
        assertEquals(fixture.owner, stored.creator)
        assertEquals(expiresAt, stored.expiresAt)
    }

    @Test
    fun `metadata lookup returns expiration creation time and creator display name`() {
        val fixture = fixture("metadata")
        val expiresAt = futureExpiry()

        rotate(fixture, code(1), expiresAt)

        val metadata = transaction.inTransaction { repository.findMetadata(fixture.group) }

        assertEquals(expiresAt, metadata?.expiresAt)
        assertTrue(metadata!!.createdAt.isBefore(metadata.expiresAt))
        assertEquals("Owner Person", metadata.createdByName)
    }

    @Test
    fun `rotation replaces code while preserving one row per group`() {
        val fixture = fixture("replace")

        rotate(fixture, code(1))
        rotate(fixture, code(2))

        assertEquals(1, inviteCount(fixture.group))
        assertEquals(code(2).value, invite(fixture.group)?.shortCode)
    }

    @Test
    fun `old code is absent after replacement commits`() {
        val fixture = fixture("old-digest")
        val old = code(1)

        rotate(fixture, old)
        rotate(fixture, code(2))

        assertEquals(0, codeCount(old))
    }

    @Test
    fun `expiration deletes the active invite`() {
        val fixture = fixture("expire")
        rotate(fixture, code(1))

        transaction.inTransaction { repository.expire(fixture.group) }

        assertNull(invite(fixture.group))
    }

    @Test
    fun `repeated expiration remains successful`() {
        val fixture = fixture("repeat-expire")

        transaction.inTransaction { repository.expire(fixture.group) }
        transaction.inTransaction { repository.expire(fixture.group) }

        assertEquals(0, inviteCount(fixture.group))
    }

    @Test
    fun `deleted group rejects invite writes`() {
        val fixture = fixture("deleted")
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '${fixture.group}'")

        assertFailsWith<IllegalArgumentException> {
            transaction.inTransaction {
                repository.rotate(command(fixture, code(1)))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            transaction.inTransaction { repository.expire(fixture.group) }
        }
        assertEquals(0, inviteCount(fixture.group))
    }

    @Test
    fun `expiration leaves another group invite untouched`() {
        val first = fixture("isolation-first")
        val second = fixture("isolation-second")
        rotate(first, code(1))
        rotate(second, code(2))

        transaction.inTransaction { repository.expire(first.group) }

        assertNull(invite(first.group))
        assertEquals(code(2).value, invite(second.group)?.shortCode)
    }

    @Test
    fun `same code cannot be active in two groups`() {
        val first = fixture("unique-first")
        val second = fixture("unique-second")
        val shared = code(3)
        rotate(first, shared)

        assertFailsWith<RuntimeException> { rotate(second, shared) }
        assertNull(invite(second.group))
    }

    @Test
    fun `transaction rollback preserves previous code`() {
        val fixture = fixture("rollback")
        val previous = code(1)
        rotate(fixture, previous)

        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                repository.rotate(command(fixture, code(2)))
                error("rollback")
            }
        }

        assertEquals(previous.value, invite(fixture.group)?.shortCode)
    }

    @Test
    fun `concurrent rotations serialize and retain only the committed winner`() {
        val fixture = fixture("concurrent")
        val firstWritten = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit {
                transaction.inTransaction {
                    repository.rotate(command(fixture, code(1)))
                    firstWritten.countDown()
                    releaseFirst.await()
                }
            }
            firstWritten.await()
            val second = pool.submit {
                transaction.inTransaction {
                    secondAttempted.countDown()
                    repository.rotate(command(fixture, code(2)))
                    secondFinished.countDown()
                }
            }
            secondAttempted.await()
            assertFalse(secondFinished.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get()
            second.get()

            assertEquals(1, inviteCount(fixture.group))
            assertEquals(code(2).value, invite(fixture.group)?.shortCode)
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `rotation for a missing group is rejected without invite row`() {
        val missingGroup = UUID.randomUUID()
        val creator = insertUser("missing-creator")

        assertFailsWith<RuntimeException> {
            transaction.inTransaction {
                repository.rotate(
                    RotateInviteCommand(missingGroup, code(1), creator, futureExpiry()),
                )
            }
        }
        assertEquals(0, inviteCount(missingGroup))
    }

    private data class Fixture(val owner: UUID, val group: UUID)
    private data class StoredInvite(val shortCode: String?, val digest: ByteArray?, val creator: UUID, val expiresAt: Instant?, val revision: UUID)

    private fun fixture(prefix: String): Fixture {
        val owner = insertUser("$prefix-owner")
        return Fixture(owner, insertGroup(owner))
    }

    private fun rotate(
        fixture: Fixture,
        code: InviteCode,
        expiresAt: Instant = futureExpiry(),
    ) = transaction.inTransaction {
        repository.rotate(command(fixture, code, expiresAt))
    }

    private fun command(
        fixture: Fixture,
        code: InviteCode,
        expiresAt: Instant? = futureExpiry(),
    ) = RotateInviteCommand(
        groupId = fixture.group,
        code = code,
        createdByUserId = fixture.owner,
        expiresAt = expiresAt,
    )

    // timestamptz guarda microssegundos; sem truncar, Instant.now() (nanos) não bate no round-trip.
    private fun futureExpiry(): Instant = Instant.now().plus(Duration.ofDays(7)).truncatedTo(ChronoUnit.MICROS)

    private fun code(seed: Int) = InviteCode.from("23456789ABCDEFGHJKMNPQRSTUVWXYZ".substring(seed, seed + 8))

    private fun insertLegacy(fixture: Fixture, digest: InviteTokenDigest) {
        val hex = digest.toByteArray().joinToString("") { "%02x".format(it) }
        execute(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at, expires_at) " +
                "VALUES ('${fixture.group}', decode('$hex', 'hex'), '${fixture.owner}', now(), NULL)",
        )
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject', true, 'Owner Person', now(), now())",
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

    private fun invite(group: UUID): StoredInvite? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT short_code, token_digest, created_by_user_id, expires_at, revision FROM group_invites WHERE group_id = ?",
        ).use { statement ->
            statement.setObject(1, group)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    StoredInvite(
                        result.getString(1),
                        result.getBytes(2),
                        result.getObject(3, UUID::class.java),
                        result.getTimestamp(4)?.toInstant(),
                        result.getObject(5, UUID::class.java),
                    )
                } else null
            }
        }
    }

    private fun codeCount(code: InviteCode): Int = connection().use { connection ->
        connection.prepareStatement("SELECT count(*) FROM group_invites WHERE short_code = ?").use { statement ->
            statement.setString(1, code.value)
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun inviteCount(group: UUID): Int = number("SELECT count(*) FROM group_invites WHERE group_id = '$group'")
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun number(sql: String): Int = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } } }
    private fun connection(): Connection = dataSource.connection
}
