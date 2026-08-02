package br.com.saqz.groups.adapter.output.jdbc.invite

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.application.invite.manage.RotateInviteCommand
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcInviteManagementRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcInviteManagementRepository
    private lateinit var transaction: JdbcTransactionRunner
    private val legacyCreatedAt = Instant.parse("2026-07-01T10:00:00Z")

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
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
        repository = JdbcInviteManagementRepository(dataSource)
        transaction = JdbcTransactionRunner(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_invites, group_memberships, access_groups, invite_redemption_limits, access_users CASCADE")
    }

    @Test
    fun `first rotation inserts exact digest and creator`() {
        val fixture = fixture("insert")
        val digest = digest(1)
        val expiresAt = Instant.parse("2026-07-16T18:00:00Z")

        transaction.inTransaction {
            repository.rotate(command(fixture, digest, expiresAt))
        }

        val stored = requireNotNull(invite(fixture.group))
        assertContentEquals(digest.toByteArray(), stored.digest)
        assertEquals(fixture.owner, stored.creator)
        assertEquals(expiresAt, stored.expiresAt)
    }

    @Test
    fun `metadata lookup returns expiration creation time and creator display name`() {
        val fixture = fixture("metadata")
        val expiresAt = Instant.parse("2026-08-16T18:00:00Z")

        rotate(fixture, digest(1), expiresAt)

        val metadata = transaction.inTransaction { repository.findMetadata(fixture.group) }

        assertEquals(expiresAt, metadata?.expiresAt)
        assertTrue(metadata!!.createdAt.isBefore(metadata.expiresAt))
        assertEquals("Owner Person", metadata.createdByName)
    }

    @Test
    fun `rotation replaces digest while preserving one row per group`() {
        val fixture = fixture("replace")

        rotate(fixture, digest(1))
        rotate(fixture, digest(2))

        assertEquals(1, inviteCount(fixture.group))
        assertContentEquals(digest(2).toByteArray(), invite(fixture.group)?.digest)
    }

    @Test
    fun `old digest is absent after replacement commits`() {
        val fixture = fixture("old-digest")
        val old = digest(1)

        rotate(fixture, old)
        rotate(fixture, digest(2))

        assertEquals(0, digestCount(old))
    }

    @Test
    fun `expiration deletes the active invite`() {
        val fixture = fixture("expire")
        rotate(fixture, digest(1))

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
                repository.rotate(command(fixture, digest(1)))
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
        rotate(first, digest(1))
        rotate(second, digest(2))

        transaction.inTransaction { repository.expire(first.group) }

        assertNull(invite(first.group))
        assertContentEquals(digest(2).toByteArray(), invite(second.group)?.digest)
    }

    @Test
    fun `same digest cannot be active in two groups`() {
        val first = fixture("unique-first")
        val second = fixture("unique-second")
        val shared = digest(3)
        rotate(first, shared)

        assertFailsWith<RuntimeException> { rotate(second, shared) }
        assertNull(invite(second.group))
    }

    @Test
    fun `transaction rollback preserves previous digest`() {
        val fixture = fixture("rollback")
        val previous = digest(1)
        rotate(fixture, previous)

        assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                repository.rotate(command(fixture, digest(2)))
                error("rollback")
            }
        }

        assertContentEquals(previous.toByteArray(), invite(fixture.group)?.digest)
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
                    repository.rotate(command(fixture, digest(1)))
                    firstWritten.countDown()
                    releaseFirst.await()
                }
            }
            firstWritten.await()
            val second = pool.submit {
                transaction.inTransaction {
                    secondAttempted.countDown()
                    repository.rotate(command(fixture, digest(2)))
                    secondFinished.countDown()
                }
            }
            secondAttempted.await()
            assertFalse(secondFinished.await(200, TimeUnit.MILLISECONDS))
            releaseFirst.countDown()
            first.get()
            second.get()

            assertEquals(1, inviteCount(fixture.group))
            assertContentEquals(digest(2).toByteArray(), invite(fixture.group)?.digest)
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
                    RotateInviteCommand(missingGroup, digest(1), creator, Instant.parse("2026-07-16T18:00:00Z")),
                )
            }
        }
        assertEquals(0, inviteCount(missingGroup))
    }

    private data class Fixture(val owner: UUID, val group: UUID)
    private data class StoredInvite(val digest: ByteArray, val creator: UUID, val expiresAt: Instant)

    private fun fixture(prefix: String): Fixture {
        val owner = insertUser("$prefix-owner")
        return Fixture(owner, insertGroup(owner))
    }

    private fun rotate(
        fixture: Fixture,
        digest: InviteTokenDigest,
        expiresAt: Instant = Instant.parse("2026-07-16T18:00:00Z"),
    ) = transaction.inTransaction {
        repository.rotate(command(fixture, digest, expiresAt))
    }

    private fun command(
        fixture: Fixture,
        digest: InviteTokenDigest,
        expiresAt: Instant = Instant.parse("2026-07-16T18:00:00Z"),
    ) = RotateInviteCommand(
        groupId = fixture.group,
        digest = digest,
        createdByUserId = fixture.owner,
        expiresAt = expiresAt,
    )

    private fun digest(seed: Int) = InviteTokenDigest.from(ByteArray(32) { index -> (seed + index).toByte() })

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
            "SELECT token_digest, created_by_user_id, expires_at FROM group_invites WHERE group_id = ?",
        ).use { statement ->
            statement.setObject(1, group)
            statement.executeQuery().use { result ->
                if (result.next()) {
                    StoredInvite(
                        result.getBytes(1),
                        result.getObject(2, UUID::class.java),
                        result.getTimestamp(3).toInstant(),
                    )
                } else null
            }
        }
    }

    private fun digestCount(digest: InviteTokenDigest): Int = connection().use { connection ->
        connection.prepareStatement("SELECT count(*) FROM group_invites WHERE token_digest = ?").use { statement ->
            statement.setBytes(1, digest.toByteArray())
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun inviteCount(group: UUID): Int = number("SELECT count(*) FROM group_invites WHERE group_id = '$group'")
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun number(sql: String): Int = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } } }
    private fun connection(): Connection = dataSource.connection
}
