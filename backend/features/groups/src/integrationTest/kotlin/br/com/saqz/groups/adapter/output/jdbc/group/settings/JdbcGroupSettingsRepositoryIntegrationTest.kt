package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupSettingsResult
import br.com.saqz.groups.domain.GroupAccessPolicy
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
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupSettingsRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var transaction: JdbcTransactionRunner
    private lateinit var useCase: UpdateGroupSettings

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations(accessMigrationLocation()).load().migrate()
        transaction = JdbcTransactionRunner(dataSource)
        useCase = UpdateGroupSettings(
            transaction,
            JdbcGroupReadRepository(dataSource),
            JdbcGroupSettingsRepository(dataSource),
            GroupAccessPolicy(),
        )
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_invites, group_memberships, access_groups, " +
                "invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `owner atomically updates both settings and increments version`() {
        val owner = insertUser("settings-owner")
        val group = insertGroup(owner)

        val result = assertIs<UpdateGroupSettingsResult.Success>(
            useCase.execute(owner, group, 1, "New Group", "Europe/Lisbon"),
        )

        assertEquals("New Group|Europe/Lisbon|2", settings(group))
        assertEquals(2, result.settings.version)
    }

    @Test
    fun `admin can persist both settings`() {
        val owner = insertUser("settings-admin-owner")
        val admin = insertUser("settings-admin")
        val group = insertGroup(owner)
        insertMembership(group, admin, "ADMIN")

        assertIs<UpdateGroupSettingsResult.Success>(useCase.execute(admin, group, 1, "Admin Group", "UTC"))
        assertEquals("Admin Group|UTC|2", settings(group))
    }

    @Test
    fun `athlete cannot mutate persisted settings`() {
        val owner = insertUser("settings-athlete-owner")
        val athlete = insertUser("settings-athlete")
        val group = insertGroup(owner)
        insertMembership(group, athlete, "ATHLETE")

        assertSame(UpdateGroupSettingsResult.AccessForbidden, useCase.execute(athlete, group, 1, "Denied", "UTC"))
        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
    }

    @Test
    fun `stale version changes neither setting`() {
        val owner = insertUser("settings-stale-owner")
        val group = insertGroup(owner, version = 2)

        assertSame(UpdateGroupSettingsResult.VersionConflict, useCase.execute(owner, group, 1, "Stale", "UTC"))
        assertEquals("Original Group|America/Sao_Paulo|2", settings(group))
    }

    @Test
    fun `nonmember cannot distinguish or mutate an existing group`() {
        val owner = insertUser("settings-private-owner")
        val stranger = insertUser("settings-stranger")
        val group = insertGroup(owner)

        assertSame(UpdateGroupSettingsResult.GroupNotFound, useCase.execute(stranger, group, 1, "Hidden", "UTC"))
        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
    }

    @Test
    fun `missing group returns group not found`() {
        val actor = insertUser("settings-missing")

        assertSame(
            UpdateGroupSettingsResult.GroupNotFound,
            useCase.execute(actor, UUID.randomUUID(), 1, "Missing", "UTC"),
        )
    }

    @Test
    fun `exactly one concurrent writer wins an expected version`() {
        val owner = insertUser("settings-concurrent-owner")
        val group = insertGroup(owner)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val futures = listOf("First Writer", "Second Writer").map { name ->
            pool.submit(Callable {
                ready.countDown()
                start.await()
                useCase.execute(owner, group, 1, name, "UTC")
            })
        }
        ready.await()
        start.countDown()
        val results = futures.map { it.get() }
        pool.shutdown()

        assertEquals(1, results.count { it is UpdateGroupSettingsResult.Success })
        assertEquals(1, results.count { it === UpdateGroupSettingsResult.VersionConflict })
        assertEquals("2", settings(group).substringAfterLast('|'))
    }

    @Test
    fun `failure after update rolls back both settings and version`() {
        val owner = insertUser("settings-rollback-owner")
        val group = insertGroup(owner)
        val failure = IllegalStateException("after update")

        assertSame(failure, assertFailsWith {
            transaction.inTransaction {
                useCase.execute(owner, group, 1, "Rollback Group", "UTC")
                throw failure
            }
        })
        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
    }

    @Test
    fun `sequential current versions preserve compare and set semantics`() {
        val owner = insertUser("settings-sequential-owner")
        val group = insertGroup(owner)

        assertIs<UpdateGroupSettingsResult.Success>(useCase.execute(owner, group, 1, "Second", "UTC"))
        assertIs<UpdateGroupSettingsResult.Success>(useCase.execute(owner, group, 2, "Third", "Europe/Lisbon"))

        assertEquals("Third|Europe/Lisbon|3", settings(group))
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
            "VALUES ('$id', '$subject', true, 'Valid User', now(), now())")
        return id
    }

    private fun insertGroup(owner: UUID, version: Long = 1): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at) " +
            "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Original Group', 'America/Sao_Paulo', $version, now(), now())")
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
            "VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun settings(group: UUID): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name, time_zone, version FROM access_groups WHERE id = '$group'").use {
                it.next()
                "${it.getString(1)}|${it.getString(2)}|${it.getLong(3)}"
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }
    private fun connection(): Connection = dataSource.connection
}
