package br.com.saqz.access.adapter.output.jdbc.session

import br.com.saqz.access.application.session.SessionUpsert
import br.com.saqz.access.domain.AccessName
import br.com.saqz.access.domain.GroupRole
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcSessionRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcSessionRepository

    @BeforeAll
    fun startDatabase() {
        postgres.start()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = JdbcSessionRepository(dataSource)
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
    fun `inserts Firebase UID mirrors and returns an empty session`() {
        val session = repository.upsertAndLoad(command("subject-new"))

        assertEquals("subject-new", session.user.firebaseSubject)
        assertEquals("person@example.test", session.user.email)
        assertEquals("Person Name", session.user.displayName.value)
        assertTrue(session.memberships.isEmpty())
        assertEquals(1, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `retry with the same Firebase UID returns one stable user ID`() {
        val first = repository.upsertAndLoad(command("subject-retry"))
        val second = repository.upsertAndLoad(command("subject-retry"))

        assertEquals(first.user.id, second.user.id)
        assertEquals(1, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `equal emails with different Firebase UIDs create distinct users`() {
        val first = repository.upsertAndLoad(command("subject-one"))
        val second = repository.upsertAndLoad(command("subject-two"))

        assertNotEquals(first.user.id, second.user.id)
        assertEquals(2, count("SELECT count(*) FROM access_users"))
    }

    @Test
    fun `changed email and display name update mirrors without changing user ID`() {
        val original = repository.upsertAndLoad(command("subject-update"))
        val updated = repository.upsertAndLoad(
            SessionUpsert("subject-update", "changed@example.test", AccessName.from("Changed Name")),
        )

        assertEquals(original.user.id, updated.user.id)
        assertEquals("changed@example.test", updated.user.email)
        assertEquals("Changed Name", updated.user.displayName.value)
        assertEquals("changed@example.test", text("SELECT email FROM access_users WHERE id = '${updated.user.id}'"))
    }

    @Test
    fun `email mirror can become null without changing user ID`() {
        val original = repository.upsertAndLoad(command("subject-null-email"))
        val updated = repository.upsertAndLoad(
            SessionUpsert("subject-null-email", null, AccessName.from("Person Name")),
        )

        assertEquals(original.user.id, updated.user.id)
        assertEquals(null, updated.user.email)
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE id = '${updated.user.id}' AND email IS NULL"))
    }

    @Test
    fun `group ownership is synthesized as owner membership`() {
        val owner = repository.upsertAndLoad(command("owner-subject"))
        val group = insertGroup(owner.user.id, "Owner Group")

        val refreshed = repository.upsertAndLoad(command("owner-subject"))

        assertEquals(listOf(group), refreshed.memberships.map { it.groupId })
        assertEquals(GroupRole.OWNER, refreshed.memberships.single().role)
    }

    @Test
    fun `persisted admin and athlete memberships keep their exact roles`() {
        val owner = repository.upsertAndLoad(command("roles-owner"))
        val member = repository.upsertAndLoad(command("roles-member"))
        val adminGroup = insertGroup(owner.user.id, "Admin Group")
        val athleteGroup = insertGroup(owner.user.id, "Athlete Group")
        insertMembership(adminGroup, member.user.id, "ADMIN")
        insertMembership(athleteGroup, member.user.id, "ATHLETE")

        val roles = repository.upsertAndLoad(command("roles-member")).memberships.associate { it.groupId to it.role }

        assertEquals(mapOf(adminGroup to GroupRole.ADMIN, athleteGroup to GroupRole.ATHLETE), roles)
    }

    @Test
    fun `memberships are returned in stable group name and ID order`() {
        val owner = repository.upsertAndLoad(command("order-owner"))
        val member = repository.upsertAndLoad(command("order-member"))
        val beta = insertGroup(owner.user.id, "Beta Group")
        val alpha = insertGroup(owner.user.id, "Alpha Group")
        insertMembership(beta, member.user.id, "ATHLETE")
        insertMembership(alpha, member.user.id, "ATHLETE")

        val first = repository.upsertAndLoad(command("order-member")).memberships
        val second = repository.upsertAndLoad(command("order-member")).memberships

        assertEquals(listOf(alpha, beta), first.map { it.groupId })
        assertEquals(first, second)
    }

    @Test
    fun `mirror update preserves existing memberships and roles`() {
        val owner = repository.upsertAndLoad(command("preserve-owner"))
        val member = repository.upsertAndLoad(command("preserve-member"))
        val group = insertGroup(owner.user.id, "Preserved Group")
        insertMembership(group, member.user.id, "ADMIN")

        val refreshed = repository.upsertAndLoad(
            SessionUpsert("preserve-member", "new@example.test", AccessName.from("New Name")),
        )

        assertEquals(listOf(group), refreshed.memberships.map { it.groupId })
        assertEquals(GroupRole.ADMIN, refreshed.memberships.single().role)
    }

    @Test
    fun `two concurrent connections upsert one UID and return the same user ID`() {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val calls = List(2) {
            Callable {
                ready.countDown()
                start.await()
                repository.upsertAndLoad(command("subject-concurrent")).user.id
            }
        }
        val futures = calls.map(pool::submit)
        ready.await()
        start.countDown()
        val ids = futures.map { it.get() }
        pool.shutdown()

        assertEquals(ids[0], ids[1])
        assertEquals(1, count("SELECT count(*) FROM access_users WHERE firebase_subject = 'subject-concurrent'"))
    }

    private fun command(subject: String) =
        SessionUpsert(subject, "person@example.test", AccessName.from("Person Name"))

    private fun insertGroup(ownerId: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups " +
                "(id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) VALUES " +
                "('$id', '$ownerId', '${UUID.randomUUID()}', '$name', 'UTC', now(), now())",
        )
        return id
    }

    private fun insertMembership(groupId: UUID, userId: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$groupId', '$userId', '$role', now(), now())",
        )
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

    private fun text(sql: String): String =
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }

    private fun connection(): Connection = dataSource.connection
}
