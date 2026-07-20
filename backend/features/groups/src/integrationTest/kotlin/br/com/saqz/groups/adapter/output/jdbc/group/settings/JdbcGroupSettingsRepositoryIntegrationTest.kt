package br.com.saqz.groups.adapter.output.jdbc.group.settings

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.settings.UpdateGroupSettings
import br.com.saqz.groups.application.settings.UpdateGroupProfileInput
import br.com.saqz.groups.application.settings.UpdateGroupSettingsResult
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.group.CourtPlayStyle
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupProfileDefaultsInput
import br.com.saqz.groups.domain.group.GroupVenueInput
import br.com.saqz.groups.domain.group.RegularSlotInput
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
import java.time.DayOfWeek
import java.time.LocalTime
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

    @Test
    fun `owner updates complete profile defaults and increments version`() {
        val owner = insertUser("profile-settings-owner")
        val group = insertGroup(owner)

        val result = assertIs<UpdateGroupSettingsResult.Success>(
            useCase.execute(owner, group, 1, UpdateGroupProfileInput(profile())),
        )

        assertEquals(2, result.settings.version)
        assertEquals("New Group", text("SELECT name FROM access_groups WHERE id = '$group'"))
        assertEquals("COMPLETE", text("SELECT profile_status FROM access_groups WHERE id = '$group'"))
        assertEquals("COURT_VOLLEYBALL", text("SELECT modality FROM access_groups WHERE id = '$group'"))
        assertEquals("MIXED", text("SELECT composition FROM access_groups WHERE id = '$group'"))
        assertEquals("São Paulo", text("SELECT city FROM access_groups WHERE id = '$group'"))
        assertEquals(18, number("SELECT default_capacity FROM access_groups WHERE id = '$group'"))
        assertEquals(180, number("SELECT default_confirmation_lead_minutes FROM access_groups WHERE id = '$group'"))
        assertEquals(1500, number("SELECT default_game_fee_cents FROM access_groups WHERE id = '$group'"))
        assertEquals(7000, number("SELECT monthly_fee_cents FROM access_groups WHERE id = '$group'"))
        assertEquals(10, number("SELECT monthly_due_day FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `admin updates complete profile defaults`() {
        val owner = insertUser("profile-admin-owner")
        val admin = insertUser("profile-admin")
        val group = insertGroup(owner)
        insertMembership(group, admin, "ADMIN")

        assertIs<UpdateGroupSettingsResult.Success>(
            useCase.execute(admin, group, 1, UpdateGroupProfileInput(profile(name = "Admin Profile"))),
        )

        assertEquals("Admin Profile", text("SELECT name FROM access_groups WHERE id = '$group'"))
        assertEquals("2", settings(group).substringAfterLast('|'))
    }

    @Test
    fun `athlete cannot update profile defaults`() {
        val owner = insertUser("profile-athlete-owner")
        val athlete = insertUser("profile-athlete")
        val group = insertGroup(owner)
        insertMembership(group, athlete, "ATHLETE")

        assertSame(UpdateGroupSettingsResult.AccessForbidden, useCase.execute(athlete, group, 1, UpdateGroupProfileInput(profile())))

        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
        assertEquals(null, nullableText("SELECT modality FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `nonmember profile update is not found and does not mutate`() {
        val owner = insertUser("profile-private-owner")
        val stranger = insertUser("profile-private-stranger")
        val group = insertGroup(owner)

        assertSame(UpdateGroupSettingsResult.GroupNotFound, useCase.execute(stranger, group, 1, UpdateGroupProfileInput(profile())))

        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
        assertEquals(null, nullableText("SELECT modality FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `stale profile version does not mutate scalar venue or slot defaults`() {
        val owner = insertUser("profile-stale-owner")
        val group = insertGroup(owner, version = 2)

        assertSame(UpdateGroupSettingsResult.VersionConflict, useCase.execute(owner, group, 1, UpdateGroupProfileInput(profile())))

        assertEquals("Original Group|America/Sao_Paulo|2", settings(group))
        assertEquals(0, number("SELECT count(*) FROM group_venues WHERE group_id = '$group'"))
        assertEquals(0, number("SELECT count(*) FROM group_regular_slots WHERE group_id = '$group'"))
    }

    @Test
    fun `modality and preset changes clear obsolete custom and play style values`() {
        val owner = insertUser("profile-cleanup-owner")
        val group = insertCompleteCourtGroup(owner)

        assertIs<UpdateGroupSettingsResult.Success>(
            useCase.execute(
                owner,
                group,
                1,
                UpdateGroupProfileInput(
                    profile(
                        modality = GroupModality.BEACH_VOLLEYBALL,
                        level = GroupLevel.ADVANCED,
                        customLevel = null,
                        playStyle = null,
                        customPlayStyle = null,
                    ),
                ),
            ),
        )

        assertEquals("BEACH_VOLLEYBALL", text("SELECT modality FROM access_groups WHERE id = '$group'"))
        assertEquals("ADVANCED", text("SELECT level FROM access_groups WHERE id = '$group'"))
        assertEquals(null, nullableText("SELECT custom_level FROM access_groups WHERE id = '$group'"))
        assertEquals(null, nullableText("SELECT play_style FROM access_groups WHERE id = '$group'"))
        assertEquals(null, nullableText("SELECT custom_play_style FROM access_groups WHERE id = '$group'"))
    }

    @Test
    fun `venue and slot replacement preserves submitted stable ids`() {
        val owner = insertUser("profile-stable-owner")
        val group = insertGroup(owner)
        val venueId = UUID.randomUUID()
        val slotId = UUID.randomUUID()

        assertIs<UpdateGroupSettingsResult.Success>(
            useCase.execute(
                owner,
                group,
                1,
                UpdateGroupProfileInput(
                    profile = profile(
                        defaultVenue = GroupVenueInput("Arena Beach", "Rua Central 100", "Quadra 2"),
                        regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120)),
                    ),
                    defaultVenueId = venueId,
                    regularSlotIds = listOf(slotId),
                ),
            ),
        )

        assertEquals(venueId, uuid("SELECT id FROM group_venues WHERE group_id = '$group'"))
        assertEquals(venueId, uuid("SELECT default_venue_id FROM access_groups WHERE id = '$group'"))
        assertEquals(slotId, uuid("SELECT id FROM group_regular_slots WHERE group_id = '$group'"))
        assertEquals(1, number("SELECT weekday FROM group_regular_slots WHERE id = '$slotId'"))
    }

    @Test
    fun `profile failure after write rolls back scalar venue and slot replacement`() {
        val owner = insertUser("profile-rollback-owner")
        val group = insertGroup(owner)
        val failure = IllegalStateException("profile after update")

        assertSame(failure, assertFailsWith {
            transaction.inTransaction {
                useCase.execute(
                    owner,
                    group,
                    1,
                    UpdateGroupProfileInput(
                        profile(
                            defaultVenue = GroupVenueInput("Arena Beach", "Rua Central 100", null),
                            regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120)),
                        ),
                    ),
                )
                throw failure
            }
        })

        assertEquals("Original Group|America/Sao_Paulo|1", settings(group))
        assertEquals(0, number("SELECT count(*) FROM group_venues WHERE group_id = '$group'"))
        assertEquals(0, number("SELECT count(*) FROM group_regular_slots WHERE group_id = '$group'"))
    }

    @Test
    fun `profile update does not touch operational game attendance charge or expense tables`() {
        val owner = insertUser("profile-operational-owner")
        val group = insertGroup(owner)

        assertIs<UpdateGroupSettingsResult.Success>(useCase.execute(owner, group, 1, UpdateGroupProfileInput(profile())))

        assertEquals(0, number("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'game%'"))
        assertEquals(0, number("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE '%attendance%'"))
        assertEquals(0, number("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE '%charge%'"))
        assertEquals(0, number("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE '%expense%'"))
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

    private fun insertCompleteCourtGroup(owner: UUID): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (" +
                "id, owner_user_id, creation_key, name, time_zone, version, privacy, currency, profile_status, " +
                "modality, composition, level, custom_level, play_style, custom_play_style, created_at, updated_at" +
                ") VALUES (" +
                "'$id', '$owner', '${UUID.randomUUID()}', 'Original Group', 'America/Sao_Paulo', 1, " +
                "'PRIVATE', 'BRL', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', 'CUSTOM', 'Liga B', " +
                "'CUSTOM', 'Sem central fixo', now(), now())",
        )
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

    private fun profile(
        name: String = "New Group",
        modality: GroupModality = GroupModality.COURT_VOLLEYBALL,
        composition: GroupComposition = GroupComposition.MIXED,
        level: GroupLevel? = GroupLevel.INTERMEDIATE,
        customLevel: String? = null,
        playStyle: CourtPlayStyle? = CourtPlayStyle.FIVE_ONE,
        customPlayStyle: String? = null,
        defaultVenue: GroupVenueInput? = null,
        regularSlots: List<RegularSlotInput> = emptyList(),
    ) = GroupProfileDefaultsInput(
        name = name,
        modality = modality,
        composition = composition,
        description = "Training group",
        city = "São Paulo",
        level = level,
        customLevel = customLevel,
        playStyle = playStyle,
        customPlayStyle = customPlayStyle,
        defaultVenue = defaultVenue,
        regularSlots = regularSlots,
        defaultCapacity = 18,
        defaultConfirmationLeadMinutes = 180,
        defaultGameFeeCents = 1500,
        monthlyFeeCents = 7000,
        monthlyDueDay = 10,
    )

    private fun text(sql: String): String = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getString(1)
            }
        }
    }

    private fun nullableText(sql: String): String? = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getString(1)
            }
        }
    }

    private fun number(sql: String): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getInt(1)
            }
        }
    }

    private fun uuid(sql: String): UUID = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use {
                it.next()
                it.getObject(1, UUID::class.java)
            }
        }
    }

    private fun execute(sql: String) {
        connection().use { it.createStatement().use { statement -> statement.execute(sql) } }
    }
    private fun connection(): Connection = dataSource.connection
}
