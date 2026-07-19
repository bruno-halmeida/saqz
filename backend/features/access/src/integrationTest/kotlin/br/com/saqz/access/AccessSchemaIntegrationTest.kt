package br.com.saqz.access

import br.com.saqz.access.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessSchemaIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var flyway: Flyway

    @BeforeAll
    fun migrateEmptyDatabase() {
        postgres.startAndAwaitJdbc()
        flyway = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .load()
        assertEquals(2, flyway.migrate().migrationsExecuted)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_regular_slots, group_venues, group_invites, group_memberships, access_groups, " +
                "invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `migration creates the exact access table inventory`() {
        assertEquals(
            setOf(
                "access_users",
                "access_groups",
                "group_memberships",
                "group_invites",
                "group_venues",
                "group_regular_slots",
                "invite_redemption_limits",
            ),
            queryStrings(
                "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
            ),
        )
    }

    @Test
    fun `reapplying Flyway leaves the versioned schema unchanged`() {
        assertEquals(0, flyway.migrate().migrationsExecuted)
        assertTrue(flyway.info().pending().isEmpty())
    }

    @Test
    fun `firebase subject is the unique external identity key`() {
        insertUser(subject = "same-subject", email = "first@example.test")

        assertSqlFails { insertUser(subject = "same-subject", email = "second@example.test") }
    }

    @Test
    fun `equal emails do not merge different Firebase subjects`() {
        insertUser(subject = "subject-one", email = "same@example.test")
        insertUser(subject = "subject-two", email = "same@example.test")

        assertEquals(2, queryInt("SELECT count(*) FROM access_users WHERE email = 'same@example.test'"))
    }

    @Test
    fun `unverified users cannot be persisted`() {
        assertSqlFails { insertUser(subject = "unverified", verified = false) }
    }

    @Test
    fun `display name rejects trim length and control mutations`() {
        listOf("a", " leading", "trailing ", "line\nbreak").forEachIndexed { index, name ->
            assertSqlFails { insertUser(subject = "invalid-name-$index", displayName = name) }
        }
    }

    @Test
    fun `group owner must reference an existing user`() {
        assertSqlFails { insertGroup(ownerId = UUID.randomUUID()) }
        assertSqlFails {
            execute(
                "INSERT INTO access_groups " +
                    "(id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES " +
                    "('${UUID.randomUUID()}', NULL, '${UUID.randomUUID()}', 'Valid Group', 'UTC', now(), now())",
            )
        }
    }

    @Test
    fun `creation key is unique per owner and reusable by another owner`() {
        val firstOwner = insertUser("owner-one")
        val secondOwner = insertUser("owner-two")
        val creationKey = UUID.randomUUID()
        insertGroup(firstOwner, creationKey)

        assertSqlFails { insertGroup(firstOwner, creationKey) }
        insertGroup(secondOwner, creationKey)
    }

    @Test
    fun `group version starts at one and rejects nonpositive values`() {
        val owner = insertUser("version-owner")
        val group = insertGroup(owner)

        assertEquals(1, queryInt("SELECT version FROM access_groups WHERE id = '$group'"))
        assertSqlFails {
            execute("UPDATE access_groups SET version = 0 WHERE id = '$group'")
        }
    }

    @Test
    fun `memberships accept only admin and athlete persisted roles`() {
        val owner = insertUser("membership-owner")
        val admin = insertUser("admin-user")
        val athlete = insertUser("athlete-user")
        val group = insertGroup(owner)

        insertMembership(group, admin, "ADMIN")
        insertMembership(group, athlete, "ATHLETE")
        assertEquals(setOf("ADMIN", "ATHLETE"), queryStrings("SELECT role FROM group_memberships"))
    }

    @Test
    fun `memberships reject owner role and duplicate group user pair`() {
        val owner = insertUser("pair-owner")
        val member = insertUser("pair-member")
        val group = insertGroup(owner)

        assertSqlFails { insertMembership(group, member, "OWNER") }
        assertSqlFails { insertMembership(UUID.randomUUID(), member, "ATHLETE") }
        assertSqlFails { insertMembership(group, UUID.randomUUID(), "ATHLETE") }
        insertMembership(group, member, "ATHLETE")
        assertSqlFails { insertMembership(group, member, "ADMIN") }
    }

    @Test
    fun `invite stores one unique digest per group without expiration column`() {
        val owner = insertUser("invite-owner")
        val secondOwner = insertUser("second-invite-owner")
        val group = insertGroup(owner)
        val secondGroup = insertGroup(secondOwner)
        insertInvite(group, owner, "aa")

        assertSqlFails { insertInvite(group, owner, "bb") }
        assertSqlFails { insertInvite(secondGroup, secondOwner, "aa") }
        assertSqlFails { insertInvite(secondGroup, UUID.randomUUID(), "bb") }
        val columns = queryStrings(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'group_invites'",
        )
        assertFalse("expires_at" in columns)
        assertEquals("bytea", queryString("SELECT data_type FROM information_schema.columns " +
            "WHERE table_name = 'group_invites' AND column_name = 'token_digest'"))
    }

    @Test
    fun `redemption invalid count is constrained from zero through ten`() {
        val user = insertUser("limit-user")
        assertSqlFails {
            execute("INSERT INTO invite_redemption_limits VALUES ('${UUID.randomUUID()}', now(), 0)")
        }
        execute("INSERT INTO invite_redemption_limits VALUES ('$user', now(), 0)")
        execute("UPDATE invite_redemption_limits SET invalid_count = 10 WHERE user_id = '$user'")

        assertSqlFails { execute("UPDATE invite_redemption_limits SET invalid_count = 11 WHERE user_id = '$user'") }
        assertSqlFails { execute("UPDATE invite_redemption_limits SET invalid_count = -1 WHERE user_id = '$user'") }
    }

    @Test
    fun `transaction rollback leaves no partial user mutation`() {
        val id = UUID.randomUUID()
        connection().use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                statement.executeUpdate(userInsert(id, "rollback-subject", null, true, "Rollback User"))
            }
            connection.rollback()
        }

        assertEquals(0, queryInt("SELECT count(*) FROM access_users WHERE id = '$id'"))
    }

    private fun insertUser(
        subject: String,
        email: String? = null,
        verified: Boolean = true,
        displayName: String = "Valid Name",
    ): UUID {
        val id = UUID.randomUUID()
        execute(userInsert(id, subject, email, verified, displayName))
        return id
    }

    private fun userInsert(id: UUID, subject: String, email: String?, verified: Boolean, displayName: String): String =
        "INSERT INTO access_users " +
            "(id, firebase_subject, email, email_verified, display_name, created_at, updated_at) VALUES " +
            "('$id', '$subject', ${email?.let { "'$it'" } ?: "NULL"}, $verified, " +
            "'${displayName.replace("'", "''")}', now(), now())"

    private fun insertGroup(ownerId: UUID, creationKey: UUID = UUID.randomUUID()): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups " +
                "(id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES " +
                "('$id', '$ownerId', '$creationKey', 'Valid Group', 'America/Sao_Paulo', now(), now())",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', '$role', now(), now())",
        )
    }

    private fun insertInvite(groupId: UUID, creatorId: UUID, digestHex: String) {
        execute(
            "INSERT INTO group_invites (group_id, token_digest, created_by_user_id, created_at) " +
                "VALUES ('$groupId', decode('$digestHex', 'hex'), '$creatorId', now())",
        )
    }

    private fun execute(sql: String) {
        connection().use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun queryInt(sql: String): Int =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun queryString(sql: String): String =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun queryStrings(sql: String): Set<String> =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    buildSet {
                        while (result.next()) add(result.getString(1))
                    }
                }
            }
        }

    private fun connection() = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun assertSqlFails(block: () -> Unit) {
        assertFailsWith<SQLException>(block = block)
    }
}
