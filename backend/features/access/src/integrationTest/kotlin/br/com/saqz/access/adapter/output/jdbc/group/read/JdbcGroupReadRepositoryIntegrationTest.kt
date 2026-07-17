package br.com.saqz.access.adapter.output.jdbc.group.read

import br.com.saqz.access.testing.startAndAwaitJdbc
import br.com.saqz.access.application.group.read.GroupReadKey
import br.com.saqz.access.domain.GroupRole
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.AbstractDataSource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupReadRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcGroupReadRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = JdbcGroupReadRepository(dataSource)
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
    fun `owner role is synthesized with exact settings and version`() {
        val owner = insertUser("read-owner")
        val group = insertGroup(owner, "Owner Group", "America/Sao_Paulo", version = 4)

        val snapshot = requireNotNull(repository.find(GroupReadKey(owner, group)))

        assertEquals(group, snapshot.id)
        assertEquals("Owner Group", snapshot.name.value)
        assertEquals("America/Sao_Paulo", snapshot.timeZone.value)
        assertEquals(GroupRole.OWNER, snapshot.role)
        assertEquals(4, snapshot.version)
    }

    @Test
    fun `admin membership returns admin role`() {
        val owner = insertUser("admin-owner")
        val admin = insertUser("admin-member")
        val group = insertGroup(owner, "Admin Group")
        insertMembership(group, admin, "ADMIN")

        assertEquals(GroupRole.ADMIN, repository.find(GroupReadKey(admin, group))?.role)
    }

    @Test
    fun `athlete membership returns athlete role`() {
        val owner = insertUser("athlete-owner")
        val athlete = insertUser("athlete-member")
        val group = insertGroup(owner, "Athlete Group")
        insertMembership(group, athlete, "ATHLETE")

        assertEquals(GroupRole.ATHLETE, repository.find(GroupReadKey(athlete, group))?.role)
    }

    @Test
    fun `existing group returns a null role to a nonmember`() {
        val owner = insertUser("private-owner")
        val stranger = insertUser("private-stranger")
        val group = insertGroup(owner, "Private Group")

        val snapshot = requireNotNull(repository.find(GroupReadKey(stranger, group)))

        assertNull(snapshot.role)
    }

    @Test
    fun `missing group returns no snapshot`() {
        val actor = insertUser("missing-actor")

        assertNull(repository.find(GroupReadKey(actor, UUID.randomUUID())))
    }

    @Test
    fun `membership from another group cannot authorize the requested group`() {
        val firstOwner = insertUser("isolation-first-owner")
        val secondOwner = insertUser("isolation-second-owner")
        val member = insertUser("isolation-member")
        val allowedGroup = insertGroup(firstOwner, "Allowed Group")
        val requestedGroup = insertGroup(secondOwner, "Requested Group")
        insertMembership(allowedGroup, member, "ADMIN")

        val snapshot = requireNotNull(repository.find(GroupReadKey(member, requestedGroup)))

        assertEquals(requestedGroup, snapshot.id)
        assertNull(snapshot.role)
    }

    @Test
    fun `query plan uses group and membership primary indexes`() {
        val owner = insertUser("plan-owner")
        val member = insertUser("plan-member")
        val group = insertGroup(owner, "Plan Group")
        insertMembership(group, member, "ATHLETE")

        val plan = explain(member, group)

        assertTrue(plan.contains("access_groups_pkey"), plan)
        assertTrue(plan.contains("group_memberships_pkey"), plan)
    }

    @Test
    fun `one read executes exactly one bounded SQL statement`() {
        val owner = insertUser("bounded-owner")
        val group = insertGroup(owner, "Bounded Group")
        val counting = CountingDataSource(dataSource)
        val countedRepository = JdbcGroupReadRepository(counting)

        countedRepository.find(GroupReadKey(owner, group))

        assertEquals(1, counting.preparedStatements.get())
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

    private fun insertGroup(
        owner: UUID,
        name: String,
        timeZone: String = "UTC",
        version: Long = 1,
    ): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups " +
                "(id, owner_user_id, creation_key, name, time_zone, version, created_at, updated_at) VALUES " +
                "('$id', '$owner', '${UUID.randomUUID()}', '$name', '$timeZone', $version, now(), now())",
        )
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) {
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) " +
                "VALUES ('$group', '$user', '$role', now(), now())",
        )
    }

    private fun explain(actor: UUID, group: UUID): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.execute("SET enable_seqscan = off")
            statement.executeQuery(
                "EXPLAIN SELECT groups.id, memberships.role FROM access_groups groups " +
                    "LEFT JOIN group_memberships memberships " +
                    "ON memberships.group_id = groups.id AND memberships.user_id = '$actor' " +
                    "WHERE groups.id = '$group'",
            ).use { result ->
                buildString {
                    while (result.next()) appendLine(result.getString(1))
                }
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    private fun connection(): Connection = dataSource.connection

    private class CountingDataSource(
        private val delegate: DataSource,
    ) : AbstractDataSource() {
        val preparedStatements = AtomicInteger()

        override fun getConnection(): Connection = wrap(delegate.connection)

        override fun getConnection(username: String, password: String): Connection =
            wrap(delegate.getConnection(username, password))

        private fun wrap(connection: Connection): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") preparedStatements.incrementAndGet()
            method.invoke(connection, *(arguments ?: emptyArray()))
        } as Connection
    }
}
