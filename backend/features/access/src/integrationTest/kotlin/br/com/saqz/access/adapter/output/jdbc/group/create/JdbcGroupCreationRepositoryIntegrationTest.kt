package br.com.saqz.access.adapter.output.jdbc.group.create

import br.com.saqz.access.testing.startAndAwaitJdbc
import br.com.saqz.access.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.access.application.group.create.CreateGroup
import br.com.saqz.access.application.group.create.CreateGroupResult
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupCreationRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var useCase: CreateGroup
    private lateinit var transaction: JdbcTransactionRunner
    private lateinit var repository: JdbcGroupCreationRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        transaction = JdbcTransactionRunner(dataSource)
        repository = JdbcGroupCreationRepository(dataSource)
        useCase = CreateGroup(transaction, repository)
    }

    @AfterAll
    fun stopDatabase() {
        postgres.stop()
    }

    @BeforeEach
    fun clearData() {
        execute(
            "TRUNCATE group_invites, group_memberships, access_groups, " +
                "invite_redemption_limits, access_users CASCADE",
        )
    }

    @Test
    fun `creates one group with exact owner settings and version`() {
        val owner = insertUser("create-owner")
        val requestId = UUID.randomUUID()

        val result = success(useCase.execute(owner, requestId, "  Training Club  ", "America/Sao_Paulo"))

        assertEquals("Training Club", result.group.name)
        assertEquals("America/Sao_Paulo", result.group.timeZone)
        assertEquals(1, result.group.version)
        assertEquals(owner, uuid("SELECT owner_user_id FROM access_groups WHERE id = '${result.group.id}'"))
        assertEquals(requestId, uuid("SELECT creation_key FROM access_groups WHERE id = '${result.group.id}'"))
    }

    @Test
    fun `owner is represented only by the group foreign key`() {
        val owner = insertUser("sole-owner")
        val group = success(useCase.execute(owner, UUID.randomUUID(), "Owner Group", "UTC")).group

        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '${group.id}'"))
    }

    @Test
    fun `retry keeps the original group id name timezone and version`() {
        val owner = insertUser("retry-owner")
        val requestId = UUID.randomUUID()
        val first = success(useCase.execute(owner, requestId, "Original Group", "UTC")).group

        val retry = success(useCase.execute(owner, requestId, "Changed Group", "Europe/Lisbon")).group

        assertEquals(first, retry)
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
    }

    @Test
    fun `retry after caller loses the first response returns the committed group`() {
        val owner = insertUser("timeout-owner")
        val requestId = UUID.randomUUID()
        useCase.execute(owner, requestId, "Timeout Group", "UTC")

        val recovered = success(useCase.execute(owner, requestId, "Timeout Group", "UTC")).group

        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '${recovered.id}'"))
        assertEquals(requestId, uuid("SELECT creation_key FROM access_groups WHERE id = '${recovered.id}'"))
    }

    @Test
    fun `the same creation key is isolated by owner`() {
        val firstOwner = insertUser("first-owner")
        val secondOwner = insertUser("second-owner")
        val requestId = UUID.randomUUID()

        val first = success(useCase.execute(firstOwner, requestId, "First Group", "UTC")).group
        val second = success(useCase.execute(secondOwner, requestId, "Second Group", "UTC")).group

        assertNotEquals(first.id, second.id)
        assertEquals(2, count("SELECT count(*) FROM access_groups WHERE creation_key = '$requestId'"))
    }

    @Test
    fun `concurrent requests with one creation key return one stable group`() {
        val owner = insertUser("concurrent-owner")
        val requestId = UUID.randomUUID()
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val calls = listOf("First Concurrent", "Second Concurrent").map { name ->
            Callable {
                ready.countDown()
                start.await()
                success(useCase.execute(owner, requestId, name, "UTC")).group
            }
        }
        val futures = calls.map(pool::submit)
        ready.await()
        start.countDown()
        val groups = futures.map { it.get() }
        pool.shutdown()

        assertEquals(groups[0], groups[1])
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
    }

    @Test
    fun `unknown owner fails without a partial group`() {
        assertFailsWith<RuntimeException> {
            useCase.execute(UUID.randomUUID(), UUID.randomUUID(), "Orphan Group", "UTC")
        }

        assertEquals(0, count("SELECT count(*) FROM access_groups"))
    }

    @Test
    fun `failure after insert rolls the transaction back`() {
        val owner = insertUser("rollback-owner")
        val failure = IllegalStateException("injected after insert")

        val thrown = assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                useCase.execute(owner, UUID.randomUUID(), "Rollback Group", "UTC")
                throw failure
            }
        }

        assertTrue(thrown === failure)
        assertEquals(0, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
    }

    @Test
    fun `database exposes the owner creation key uniqueness sensor`() {
        assertEquals(
            1,
            count(
                "SELECT count(*) FROM pg_constraint " +
                    "WHERE conname = 'uq_access_groups_owner_creation' AND contype = 'u'",
            ),
        )
    }

    private fun success(result: CreateGroupResult): CreateGroupResult.Success {
        assertTrue(result is CreateGroupResult.Success)
        return result
    }

    private fun insertUser(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users " +
                "(id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES " +
                "('$id', '$subject', true, 'Valid User', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun count(sql: String): Int =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun uuid(sql: String): UUID =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getObject(1, UUID::class.java)
                }
            }
        }

    private fun connection(): Connection = dataSource.connection
}
