package br.com.saqz.bootstrap

import br.com.saqz.access.adapter.output.jdbc.session.JdbcSessionRepository
import br.com.saqz.access.application.session.AccountGroupCleanup
import br.com.saqz.access.application.session.AccountTransactionRunner
import br.com.saqz.access.application.session.BootstrapSession
import br.com.saqz.access.application.session.BootstrapSessionResult
import br.com.saqz.access.application.session.DeleteAccount
import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.group.delete.JdbcGroupDeletionRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.delete.DeleteGroup
import br.com.saqz.sharedkernel.RequestIdentity
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeleteAccountIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var sessionRepository: JdbcSessionRepository
    private lateinit var deleteAccount: DeleteAccount

    @BeforeAll
    fun startDatabase() {
        postgres.start()
        awaitDatabase()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        sessionRepository = JdbcSessionRepository(dataSource)
        deleteAccount = accountDeletion()
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_charges, game_attendance, games, group_membership_removals, " +
                "group_memberships, access_groups, access_user_photos, access_users CASCADE",
        )
    }

    @Test
    fun `deleting an account cascades owned groups and preserves third party history`() {
        val deleted = sessionRepository.upsertAndLoad(
            SessionUpsert("deleted-subject", "same@example.test", true, AccessName.from("Deleted Public")),
        )
        val thirdPartyOwner = sessionRepository.upsertAndLoad(
            SessionUpsert("third-party-owner", "owner@example.test", true, AccessName.from("Group Owner")),
        )
        val ownedGroup = insertGroup(deleted.user.id, "Owned Group")
        val thirdPartyGroup = insertGroup(thirdPartyOwner.user.id, "Third Party Group")
        insertMembership(thirdPartyGroup, deleted.user.id, "ADMIN")
        insertEntryRequest(thirdPartyGroup, deleted.user.id)
        val game = insertGame(thirdPartyGroup)
        insertAttendance(game, thirdPartyGroup, deleted.user.id, "Deleted Public")
        insertCharge(thirdPartyGroup, deleted.user.id, thirdPartyOwner.user.id, "Deleted Public")

        deleteAccount.execute("deleted-subject")

        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))
        assertNull(textOrNull("SELECT email FROM access_users WHERE id = '${deleted.user.id}'"))
        assertEquals("Deleted Public", text("SELECT display_name FROM access_users WHERE id = '${deleted.user.id}'"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$ownedGroup' AND deleted_at IS NOT NULL"))
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '$thirdPartyGroup' AND deleted_at IS NULL"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '$thirdPartyGroup' AND user_id = '${deleted.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_entry_requests WHERE user_id = '${deleted.user.id}'"))
        assertEquals(1, count("SELECT count(*) FROM group_membership_removals WHERE group_id = '$thirdPartyGroup' AND user_id = '${deleted.user.id}'"))
        assertEquals("Deleted Public", text("SELECT member_display_name FROM game_attendance WHERE member_user_id = '${deleted.user.id}'"))
        assertEquals("Deleted Public", text("SELECT member_display_name FROM group_charges WHERE member_user_id = '${deleted.user.id}'"))

        deleteAccount.execute("deleted-subject")
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE deleted_at IS NOT NULL"))

        val replacement = assertIs<BootstrapSessionResult.Success>(
            BootstrapSession(sessionRepository).execute(
                RequestIdentity("deleted-subject", "same@example.test", true, "New Person"),
            ),
        ).session
        assertNotEquals(deleted.user.id, replacement.user.id)
        assertEquals(0, replacement.memberships.size)
        assertEquals("New Person", replacement.user.displayName.value)
        assertEquals(3, count("SELECT count(*) FROM access_users"))
        assertEquals(0, count("SELECT count(*) FROM game_attendance WHERE member_user_id = '${replacement.user.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_charges WHERE member_user_id = '${replacement.user.id}'"))
    }

    private fun awaitDatabase() {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        var lastFailure: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { return }
            } catch (failure: Exception) {
                lastFailure = failure
                Thread.sleep(100)
            }
        }
        throw IllegalStateException("PostgreSQL did not become ready", lastFailure)
    }

    private fun accountDeletion(): DeleteAccount {
        val transaction = JdbcTransactionRunner(dataSource)
        val deleteGroup = DeleteGroup(transaction, JdbcGroupDeletionRepository(dataSource))
        val athletes = JdbcAthleteRepository(dataSource)
        val jdbc = JdbcClient.create(dataSource)
        val cleanup = object : AccountGroupCleanup {
            override fun deleteOwnedGroups(ownerUserId: UUID) {
                jdbc.sql(
                    "SELECT id FROM access_groups WHERE owner_user_id = :owner AND deleted_at IS NULL FOR UPDATE",
                )
                    .param("owner", ownerUserId)
                    .query(UUID::class.java)
                    .list()
                    .filterNotNull()
                    .forEach { groupId -> deleteGroup.execute(ownerUserId, groupId) }
            }

            override fun removeMemberships(userId: UUID) {
                jdbc.sql(
                    """
                    SELECT memberships.group_id
                    FROM group_memberships memberships
                    JOIN access_groups groups ON groups.id = memberships.group_id
                    WHERE memberships.user_id = :userId
                      AND groups.owner_user_id <> :userId
                      AND groups.deleted_at IS NULL
                    FOR UPDATE OF memberships, groups
                    """.trimIndent(),
                )
                    .param("userId", userId)
                    .query(UUID::class.java)
                    .list()
                    .filterNotNull()
                    .forEach { groupId -> athletes.remove(groupId, userId) }
                jdbc.sql("DELETE FROM group_entry_requests WHERE user_id = :userId")
                    .param("userId", userId)
                    .update()
            }
        }
        return DeleteAccount(
            transactionRunner = object : AccountTransactionRunner {
                override fun <T> inTransaction(block: () -> T): T = transaction.inTransaction(block)
            },
            repository = sessionRepository,
            groupCleanup = cleanup,
        )
    }

    private fun insertGroup(owner: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
                "VALUES ('$id', '$owner', '${UUID.randomUUID()}', '$name', 'UTC', now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun insertEntryRequest(group: UUID, user: UUID) = execute(
        "INSERT INTO group_entry_requests (group_id, user_id, requested_at) " +
            "VALUES ('$group', '$user', now())",
    )

    private fun insertGame(group: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id, group_id, title, local_date, local_time, zone_id, starts_at, duration_minutes, " +
                "confirmation_deadline, venue_name, venue_address, capacity, status, created_at, updated_at) VALUES " +
                "('$id', '$group', 'Training', DATE '2026-08-12', TIME '19:30', 'UTC', " +
                "TIMESTAMPTZ '2026-08-12 19:30Z', 90, TIMESTAMPTZ '2026-08-11 19:30Z', 'Arena', 'Rua Central 100', " +
                "12, 'PUBLISHED', now(), now())",
        )
        return id
    }

    private fun insertAttendance(game: UUID, group: UUID, member: UUID, displayName: String) = execute(
        "INSERT INTO game_attendance (game_id, group_id, member_user_id, status, responded_at, updated_at, " +
            "version, member_display_name) VALUES ('$game', '$group', '$member', 'CONFIRMED', now(), now(), 1, '$displayName')",
    )

    private fun insertCharge(group: UUID, member: UUID, actor: UUID, displayName: String) = execute(
        "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, " +
            "created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name) VALUES " +
            "('${UUID.randomUUID()}', '$group', '$member', 'MONTHLY', DATE '2026-08-01', 5000, DATE '2026-08-10', " +
            "'$actor', '$actor', now(), now(), '$displayName')",
    )

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun count(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getInt(1)
            }
        }
    }

    private fun text(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun textOrNull(sql: String): String? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                result.next()
                result.getString(1)
            }
        }
    }

    private fun connection(): Connection = dataSource.connection
}
