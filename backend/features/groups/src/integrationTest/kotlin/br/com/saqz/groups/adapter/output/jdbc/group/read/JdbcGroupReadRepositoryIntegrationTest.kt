package br.com.saqz.groups.adapter.output.jdbc.group.read

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.application.create.GroupProfileStatus
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.GroupRole
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
        Flyway.configure().dataSource(dataSource).locations(accessMigrationLocation()).load().migrate()
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

    @Test
    fun `owner reads complete profile game defaults venue slots and finance defaults`() {
        val owner = insertUser("profile-owner")
        val group = insertCompleteGroup(owner)
        val venue = insertDefaultVenue(group, "Arena Beach", "Rua Central 100", "Quadra 2")
        insertSlot(group, venue, 1, "20:00", 120, 0)
        insertSlot(group, null, 4, "19:30", 90, 1)

        val snapshot = requireNotNull(repository.find(GroupReadKey(owner, group)))

        assertEquals(GroupRole.OWNER, snapshot.role)
        assertEquals(GroupProfileStatus.COMPLETE, snapshot.profileStatus)
        assertEquals(GroupModality.COURT_VOLLEYBALL, snapshot.profile?.modality)
        assertEquals(GroupComposition.MIXED, snapshot.profile?.composition)
        assertEquals("São Paulo", snapshot.profile?.city)
        assertEquals(18, snapshot.profile?.defaultCapacity)
        assertEquals(180, snapshot.profile?.defaultConfirmationLeadMinutes)
        assertEquals(venue, snapshot.profile?.defaultVenue?.id)
        assertEquals("Arena Beach", snapshot.profile?.defaultVenue?.name)
        assertEquals(listOf(1, 4), snapshot.profile?.regularSlots?.map { it.weekday.value })
        assertEquals(1500, snapshot.financeDefaults?.defaultGameFeeCents)
        assertEquals(7000, snapshot.financeDefaults?.monthlyFeeCents)
        assertEquals(10, snapshot.financeDefaults?.monthlyDueDay)
    }

    @Test
    fun `admin reads finance defaults`() {
        val owner = insertUser("finance-owner")
        val admin = insertUser("finance-admin")
        val group = insertCompleteGroup(owner)
        insertMembership(group, admin, "ADMIN")

        val snapshot = requireNotNull(repository.find(GroupReadKey(admin, group)))

        assertEquals(GroupRole.ADMIN, snapshot.role)
        assertEquals(1500, snapshot.financeDefaults?.defaultGameFeeCents)
        assertEquals(7000, snapshot.financeDefaults?.monthlyFeeCents)
        assertEquals(10, snapshot.financeDefaults?.monthlyDueDay)
    }

    @Test
    fun `athlete reads profile defaults but no finance defaults`() {
        val owner = insertUser("athlete-profile-owner")
        val athlete = insertUser("athlete-profile")
        val group = insertCompleteGroup(owner)
        insertMembership(group, athlete, "ATHLETE")

        val snapshot = requireNotNull(repository.find(GroupReadKey(athlete, group)))

        assertEquals(GroupRole.ATHLETE, snapshot.role)
        assertEquals(GroupProfileStatus.COMPLETE, snapshot.profileStatus)
        assertEquals(GroupModality.COURT_VOLLEYBALL, snapshot.profile?.modality)
        assertEquals(null, snapshot.financeDefaults?.defaultGameFeeCents)
        assertEquals(null, snapshot.financeDefaults?.monthlyFeeCents)
        assertEquals(null, snapshot.financeDefaults?.monthlyDueDay)
    }

    @Test
    fun `legacy group without modality or composition reads as incomplete`() {
        val owner = insertUser("legacy-owner")
        val group = insertGroup(owner, "Legacy Group")

        val snapshot = requireNotNull(repository.find(GroupReadKey(owner, group)))

        assertEquals(GroupProfileStatus.INCOMPLETE, snapshot.profileStatus)
        assertEquals(null, snapshot.profile?.modality)
        assertEquals(null, snapshot.profile?.composition)
    }

    @Test
    fun `nonmember read has same absence of finance detail as unknown group`() {
        val owner = insertUser("private-profile-owner")
        val stranger = insertUser("private-profile-stranger")
        val group = insertCompleteGroup(owner)

        val nonmember = requireNotNull(repository.find(GroupReadKey(stranger, group)))
        val missing = repository.find(GroupReadKey(stranger, UUID.randomUUID()))

        assertEquals(null, nonmember.role)
        assertEquals(null, nonmember.financeDefaults?.defaultGameFeeCents)
        assertEquals(null, nonmember.financeDefaults?.monthlyFeeCents)
        assertEquals(null, nonmember.financeDefaults?.monthlyDueDay)
        assertEquals(null, missing)
    }

    @Test
    fun `read query does not select photo expense or charge detail`() {
        val owner = insertUser("privacy-query-owner")
        val group = insertCompleteGroup(owner)
        val counting = CountingDataSource(dataSource)
        val countedRepository = JdbcGroupReadRepository(counting)

        countedRepository.find(GroupReadKey(owner, group))

        val sql = counting.sql.single().lowercase()
        assertTrue(!sql.contains("photo"), sql)
        assertTrue(!sql.contains("expense"), sql)
        assertTrue(!sql.contains("charge"), sql)
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

    private fun insertCompleteGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (" +
                "id, owner_user_id, creation_key, name, time_zone, version, privacy, currency, " +
                "profile_status, modality, composition, description, city, level, play_style, " +
                "default_capacity, default_confirmation_lead_minutes, default_game_fee_cents, " +
                "monthly_fee_cents, monthly_due_day, created_at, updated_at" +
                ") VALUES (" +
                "'$id', '$owner', '${UUID.randomUUID()}', 'Complete Group', 'America/Sao_Paulo', 3, " +
                "'PRIVATE', 'BRL', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', 'Training group', " +
                "'São Paulo', 'INTERMEDIATE', 'FIVE_ONE', 18, 180, 1500, 7000, 10, now(), now()" +
                ")",
        )
        return id
    }

    private fun insertDefaultVenue(group: UUID, name: String, address: String, court: String?): UUID {
        val venue = UUID.randomUUID()
        execute(
            "INSERT INTO group_venues (id, group_id, name, address, court, version, created_at, updated_at) " +
                "VALUES ('$venue', '$group', '$name', '$address', ${court.sqlString()}, 1, now(), now())",
        )
        execute("UPDATE access_groups SET default_venue_id = '$venue' WHERE id = '$group'")
        return venue
    }

    private fun insertSlot(group: UUID, venue: UUID?, weekday: Int, startTime: String, duration: Int, position: Int) {
        execute(
            "INSERT INTO group_regular_slots " +
                "(id, group_id, venue_id, weekday, start_time, duration_minutes, position, version, created_at, updated_at) " +
                "VALUES ('${UUID.randomUUID()}', '$group', ${venue.sqlUuid()}, $weekday, '$startTime', $duration, " +
                "$position, 1, now(), now())",
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
        val sql = mutableListOf<String>()

        override fun getConnection(): Connection = wrap(delegate.connection)

        override fun getConnection(username: String, password: String): Connection =
            wrap(delegate.getConnection(username, password))

        private fun wrap(connection: Connection): Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") {
                preparedStatements.incrementAndGet()
                sql += arguments?.firstOrNull()?.toString().orEmpty()
            }
            method.invoke(connection, *(arguments ?: emptyArray()))
        } as Connection
    }
}

private fun String?.sqlString(): String = this?.let { "'$it'" } ?: "null"

private fun UUID?.sqlUuid(): String = this?.let { "'$it'" } ?: "null"
