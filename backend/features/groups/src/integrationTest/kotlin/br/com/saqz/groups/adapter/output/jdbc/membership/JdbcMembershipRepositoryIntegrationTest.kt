package br.com.saqz.groups.adapter.output.jdbc.membership

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.application.membership.ChangeMemberRoleCommand
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.PersistedMembershipRole
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
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcMembershipRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var repository: JdbcMembershipRepository

    @BeforeAll
    fun startDatabase() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
        repository = JdbcMembershipRepository(dataSource)
    }

    @AfterAll
    fun stopDatabase() = postgres.stop()

    @BeforeEach
    fun clearData() {
        execute("TRUNCATE group_invites, group_memberships, access_groups, invite_redemption_limits, access_users CASCADE")
    }

    @Test
    fun `list synthesizes the owner when no membership rows exist`() {
        val owner = insertUser("list-owner", "Owner Person")
        val group = insertGroup(owner)

        val result = repository.list(group)

        assertEquals(listOf(owner), result.map { it.userId })
        assertEquals(listOf(GroupRole.OWNER), result.map { it.role })
    }

    @Test
    fun `list returns only user id display name and exact roles`() {
        val owner = insertUser("minimal-owner", "Owner Person", "owner-secret@example.test")
        val admin = insertUser("minimal-admin", "Admin Person", "admin-secret@example.test")
        val athlete = insertUser("minimal-athlete", "Athlete Person", "athlete-secret@example.test")
        val group = insertGroup(owner)
        insertMembership(group, admin, "ADMIN")
        insertMembership(group, athlete, "ATHLETE")

        val result = repository.list(group)

        assertEquals(setOf(owner, admin, athlete), result.map { it.userId }.toSet())
        assertEquals(mapOf(owner to GroupRole.OWNER, admin to GroupRole.ADMIN, athlete to GroupRole.ATHLETE), result.associate { it.userId to it.role })
        assertEquals(setOf("Owner Person", "Admin Person", "Athlete Person"), result.map { it.displayName.value }.toSet())
    }

    @Test
    fun `find synthesizes owner membership`() {
        val owner = insertUser("find-owner", "Owner Person")
        val group = insertGroup(owner)

        assertEquals(GroupRole.OWNER, repository.find(group, owner)?.role)
    }

    @Test
    fun `find returns persisted member role`() {
        val owner = insertUser("find-member-owner", "Owner Person")
        val member = insertUser("find-member", "Member Person")
        val group = insertGroup(owner)
        insertMembership(group, member, "ADMIN")

        assertEquals(GroupRole.ADMIN, repository.find(group, member)?.role)
    }

    @Test
    fun `membership in another group is not returned`() {
        val firstOwner = insertUser("isolation-owner-one", "First Owner")
        val secondOwner = insertUser("isolation-owner-two", "Second Owner")
        val member = insertUser("isolation-member", "Member Person")
        val firstGroup = insertGroup(firstOwner)
        val secondGroup = insertGroup(secondOwner)
        insertMembership(firstGroup, member, "ADMIN")

        assertNull(repository.find(secondGroup, member))
        assertEquals(setOf(secondOwner), repository.list(secondGroup).map { it.userId }.toSet())
    }

    @Test
    fun `promotion changes one athlete row to admin`() {
        val fixture = membershipFixture("promotion", "ATHLETE")

        val changed = repository.change(ChangeMemberRoleCommand(fixture.group, fixture.member, PersistedMembershipRole.ADMIN))

        assertEquals(GroupRole.ADMIN, changed.role)
        assertEquals("ADMIN", role(fixture.group, fixture.member))
    }

    @Test
    fun `repeating promotion leaves one membership row`() {
        val fixture = membershipFixture("repeat", "ATHLETE")
        val command = ChangeMemberRoleCommand(fixture.group, fixture.member, PersistedMembershipRole.ADMIN)

        repository.change(command)
        repository.change(command)

        assertEquals(1, membershipCount(fixture.group, fixture.member))
        assertEquals("ADMIN", role(fixture.group, fixture.member))
    }

    @Test
    fun `demoting one admin leaves another admin unchanged`() {
        val owner = insertUser("demote-owner", "Owner Person")
        val first = insertUser("demote-first", "First Admin")
        val second = insertUser("demote-second", "Second Admin")
        val group = insertGroup(owner)
        insertMembership(group, first, "ADMIN")
        insertMembership(group, second, "ADMIN")

        repository.change(ChangeMemberRoleCommand(group, first, PersistedMembershipRole.ATHLETE))

        assertEquals("ATHLETE", role(group, first))
        assertEquals("ADMIN", role(group, second))
    }

    @Test
    fun `concurrent repeated promotion leaves one admin row`() {
        val fixture = membershipFixture("concurrent", "ATHLETE")
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val calls = List(2) {
            Callable {
                ready.countDown()
                start.await()
                repository.change(ChangeMemberRoleCommand(fixture.group, fixture.member, PersistedMembershipRole.ADMIN))
            }
        }
        val futures = calls.map(pool::submit)
        ready.await()
        start.countDown()
        futures.forEach { assertEquals(GroupRole.ADMIN, it.get().role) }
        pool.shutdown()

        assertEquals(1, membershipCount(fixture.group, fixture.member))
    }

    @Test
    fun `adapter refuses to persist the owner as a regular membership`() {
        val owner = insertUser("immutable-owner", "Owner Person")
        val group = insertGroup(owner)

        assertFailsWith<RuntimeException> {
            repository.change(ChangeMemberRoleCommand(group, owner, PersistedMembershipRole.ADMIN))
        }
        assertEquals(0, membershipCount(group, owner))
    }

    private data class Fixture(val group: UUID, val member: UUID)

    private fun membershipFixture(prefix: String, role: String): Fixture {
        val owner = insertUser("$prefix-owner", "Owner Person")
        val member = insertUser("$prefix-member", "Member Person")
        val group = insertGroup(owner)
        insertMembership(group, member, role)
        return Fixture(group, member)
    }

    private fun insertUser(subject: String, name: String, email: String? = null): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_users (id, firebase_subject, email, email_verified, display_name, created_at, updated_at) " +
            "VALUES ('$id', '$subject', ${email?.let { "'$it'" } ?: "NULL"}, true, '$name', now(), now())")
        return id
    }

    private fun insertGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, created_at, updated_at) " +
            "VALUES ('$id', '$owner', '${UUID.randomUUID()}', 'Training Group', 'UTC', now(), now())")
        return id
    }

    private fun insertMembership(group: UUID, user: UUID, role: String) = execute(
        "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$user', '$role', now(), now())",
    )

    private fun role(group: UUID, user: UUID) = text("SELECT role FROM group_memberships WHERE group_id = '$group' AND user_id = '$user'")
    private fun membershipCount(group: UUID, user: UUID) = number("SELECT count(*) FROM group_memberships WHERE group_id = '$group' AND user_id = '$user'")
    private fun execute(sql: String) { connection().use { it.createStatement().use { statement -> statement.execute(sql) } } }
    private fun text(sql: String): String = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getString(1) } } }
    private fun number(sql: String): Int = connection().use { it.createStatement().use { statement -> statement.executeQuery(sql).use { result -> result.next(); result.getInt(1) } } }
    private fun connection(): Connection = dataSource.connection
}
