package br.com.saqz.groups.adapter.output.jdbc.group.create

import br.com.saqz.groups.testing.startAndAwaitJdbc
import br.com.saqz.groups.testing.accessMigrationLocation
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.create.CreateGroup
import br.com.saqz.groups.application.create.CreateGroupResult
import br.com.saqz.groups.application.create.GroupProfileStatus
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
        Flyway.configure().dataSource(dataSource).locations(accessMigrationLocation()).load().migrate()
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

        val result = success(useCase.execute(owner, requestId, validProfile(name = "  Training Club  "), "America/Sao_Paulo"))

        assertEquals("Training Club", result.group.name)
        assertEquals("America/Sao_Paulo", result.group.timeZone)
        assertEquals(1, result.group.version)
        assertEquals(GroupProfileStatus.COMPLETE, result.group.profileStatus)
        assertEquals(owner, uuid("SELECT owner_user_id FROM access_groups WHERE id = '${result.group.id}'"))
        assertEquals(requestId, uuid("SELECT creation_key FROM access_groups WHERE id = '${result.group.id}'"))
    }

    @Test
    fun `owner is represented only by the group foreign key`() {
        val owner = insertUser("sole-owner")
        val group = success(useCase.execute(owner, UUID.randomUUID(), validProfile(name = "Owner Group"), "UTC")).group

        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(0, count("SELECT count(*) FROM group_memberships WHERE group_id = '${group.id}'"))
    }

    @Test
    fun `retry keeps the original group id name timezone and version`() {
        val owner = insertUser("retry-owner")
        val requestId = UUID.randomUUID()
        val first = success(useCase.execute(owner, requestId, validProfile(name = "Original Group"), "UTC")).group

        val retry = success(useCase.execute(owner, requestId, validProfile(name = "Changed Group"), "Europe/Lisbon")).group

        assertEquals(first, retry)
        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
        assertEquals("Original Group", text("SELECT name FROM access_groups WHERE id = '${first.id}'"))
        assertEquals("UTC", text("SELECT time_zone FROM access_groups WHERE id = '${first.id}'"))
    }

    @Test
    fun `retry after caller loses the first response returns the committed group`() {
        val owner = insertUser("timeout-owner")
        val requestId = UUID.randomUUID()
        useCase.execute(owner, requestId, validProfile(name = "Timeout Group"), "UTC")

        val recovered = success(useCase.execute(owner, requestId, validProfile(name = "Timeout Group"), "UTC")).group

        assertEquals(1, count("SELECT count(*) FROM access_groups WHERE id = '${recovered.id}'"))
        assertEquals(requestId, uuid("SELECT creation_key FROM access_groups WHERE id = '${recovered.id}'"))
    }

    @Test
    fun `the same creation key is isolated by owner`() {
        val firstOwner = insertUser("first-owner")
        val secondOwner = insertUser("second-owner")
        val requestId = UUID.randomUUID()

        val first = success(useCase.execute(firstOwner, requestId, validProfile(name = "First Group"), "UTC")).group
        val second = success(useCase.execute(secondOwner, requestId, validProfile(name = "Second Group"), "UTC")).group

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
                success(useCase.execute(owner, requestId, validProfile(name = name), "UTC")).group
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
            useCase.execute(UUID.randomUUID(), UUID.randomUUID(), validProfile(name = "Orphan Group"), "UTC")
        }

        assertEquals(0, count("SELECT count(*) FROM access_groups"))
    }

    @Test
    fun `failure after insert rolls the transaction back`() {
        val owner = insertUser("rollback-owner")
        val failure = IllegalStateException("injected after insert")

        val thrown = assertFailsWith<IllegalStateException> {
            transaction.inTransaction {
                useCase.execute(owner, UUID.randomUUID(), validProfile(name = "Rollback Group"), "UTC")
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

    @Test
    fun `creates complete scalar profile defaults on the group row`() {
        val owner = insertUser("profile-owner")
        val group = success(
            useCase.execute(
                owner,
                UUID.randomUUID(),
                validProfile(
                    modality = GroupModality.COURT_VOLLEYBALL,
                    composition = GroupComposition.WOMEN,
                    description = "Grupo competitivo",
                    city = "São Paulo",
                    level = GroupLevel.CUSTOM,
                    customLevel = "Liga B",
                    playStyle = CourtPlayStyle.CUSTOM,
                    customPlayStyle = "Sem central fixo",
                    defaultCapacity = 14,
                    defaultConfirmationLeadMinutes = 90,
                    defaultGameFeeCents = 2500,
                    monthlyFeeCents = 9000,
                    monthlyDueDay = 7,
                ),
                "America/Sao_Paulo",
            ),
        ).group

        assertEquals("PRIVATE", text("SELECT privacy FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("BRL", text("SELECT currency FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("COMPLETE", text("SELECT profile_status FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("COURT_VOLLEYBALL", text("SELECT modality FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("WOMEN", text("SELECT composition FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("Grupo competitivo", text("SELECT description FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("São Paulo", text("SELECT city FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("CUSTOM", text("SELECT level FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("Liga B", text("SELECT custom_level FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("CUSTOM", text("SELECT play_style FROM access_groups WHERE id = '${group.id}'"))
        assertEquals("Sem central fixo", text("SELECT custom_play_style FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(14, count("SELECT default_capacity FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(90, count("SELECT default_confirmation_lead_minutes FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(2500, count("SELECT default_game_fee_cents FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(9000, count("SELECT monthly_fee_cents FROM access_groups WHERE id = '${group.id}'"))
        assertEquals(7, count("SELECT monthly_due_day FROM access_groups WHERE id = '${group.id}'"))
    }

    @Test
    fun `creates default venue and points the group default venue to it`() {
        val owner = insertUser("venue-owner")
        val group = success(
            useCase.execute(
                owner,
                UUID.randomUUID(),
                validProfile(defaultVenue = GroupVenueInput("Arena Beach", "Rua Central 100", "Quadra 2")),
                "UTC",
            ),
        ).group

        assertEquals(1, count("SELECT count(*) FROM group_venues WHERE group_id = '${group.id}'"))
        assertEquals("Arena Beach", text("SELECT name FROM group_venues WHERE group_id = '${group.id}'"))
        assertEquals("Rua Central 100", text("SELECT address FROM group_venues WHERE group_id = '${group.id}'"))
        assertEquals("Quadra 2", text("SELECT court FROM group_venues WHERE group_id = '${group.id}'"))
        assertEquals(
            uuid("SELECT id FROM group_venues WHERE group_id = '${group.id}'"),
            uuid("SELECT default_venue_id FROM access_groups WHERE id = '${group.id}'"),
        )
    }

    @Test
    fun `creates regular slots in submitted order without creating games`() {
        val owner = insertUser("slot-owner")
        val group = success(
            useCase.execute(
                owner,
                UUID.randomUUID(),
                validProfile(
                    regularSlots = listOf(
                        RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120),
                        RegularSlotInput(DayOfWeek.THURSDAY, LocalTime.of(19, 30), 90),
                    ),
                ),
                "UTC",
            ),
        ).group

        assertEquals(2, count("SELECT count(*) FROM group_regular_slots WHERE group_id = '${group.id}'"))
        assertEquals(1, count("SELECT weekday FROM group_regular_slots WHERE group_id = '${group.id}' AND position = 0"))
        assertEquals("20:00:00", text("SELECT start_time::text FROM group_regular_slots WHERE group_id = '${group.id}' AND position = 0"))
        assertEquals(120, count("SELECT duration_minutes FROM group_regular_slots WHERE group_id = '${group.id}' AND position = 0"))
        assertEquals(4, count("SELECT weekday FROM group_regular_slots WHERE group_id = '${group.id}' AND position = 1"))
        assertEquals(0, count("SELECT count(*) FROM information_schema.tables WHERE table_name LIKE 'game%'"))
    }

    @Test
    fun `validation failure returns every field error before any database write`() {
        val owner = insertUser("invalid-owner")

        val result = useCase.execute(
            owner,
            UUID.randomUUID(),
            GroupProfileDefaultsInput(
                name = "",
                modality = null,
                composition = null,
                defaultVenue = GroupVenueInput("", "", null),
                regularSlots = listOf(RegularSlotInput(null, null, 10)),
                monthlyFeeCents = 1000,
            ),
            "Not/AZone",
        )

        assertTrue(result is CreateGroupResult.Invalid)
        assertEquals(
            setOf(
                "name",
                "modality",
                "composition",
                "defaultVenue.name",
                "defaultVenue.address",
                "regularSlots[0].weekday",
                "regularSlots[0].startTime",
                "regularSlots[0].durationMinutes",
                "monthlyDueDay",
                "timeZone",
            ),
            result.errors.map { it.field }.toSet(),
        )
        assertEquals(0, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
    }

    @Test
    fun `retry with different profile venue and slots does not mutate the original aggregate`() {
        val owner = insertUser("retry-defaults-owner")
        val requestId = UUID.randomUUID()
        val first = success(
            useCase.execute(
                owner,
                requestId,
                validProfile(
                    name = "Original Defaults",
                    defaultVenue = GroupVenueInput("Original Arena", "Rua A 100", null),
                    regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120)),
                    defaultCapacity = 10,
                ),
                "UTC",
            ),
        ).group

        val retry = success(
            useCase.execute(
                owner,
                requestId,
                validProfile(
                    name = "Changed Defaults",
                    defaultVenue = GroupVenueInput("Changed Arena", "Rua B 200", null),
                    regularSlots = listOf(
                        RegularSlotInput(DayOfWeek.TUESDAY, LocalTime.of(21, 0), 90),
                        RegularSlotInput(DayOfWeek.FRIDAY, LocalTime.of(19, 0), 60),
                    ),
                    defaultCapacity = 20,
                ),
                "Europe/Lisbon",
            ),
        ).group

        assertEquals(first, retry)
        assertEquals("Original Defaults", text("SELECT name FROM access_groups WHERE id = '${first.id}'"))
        assertEquals(10, count("SELECT default_capacity FROM access_groups WHERE id = '${first.id}'"))
        assertEquals("Original Arena", text("SELECT name FROM group_venues WHERE group_id = '${first.id}'"))
        assertEquals(1, count("SELECT count(*) FROM group_regular_slots WHERE group_id = '${first.id}'"))
    }

    @Test
    fun `injected child write failure rolls back group venue and slot writes`() {
        val owner = insertUser("child-failure-owner")
        val requestId = UUID.randomUUID()
        val failingUseCase = CreateGroup(
            transaction,
            JdbcGroupCreationRepository(dataSource) { throw IllegalStateException("injected child failure") },
        )

        assertFailsWith<IllegalStateException> {
            failingUseCase.execute(
                owner,
                requestId,
                validProfile(
                    defaultVenue = GroupVenueInput("Arena Beach", "Rua Central 100", null),
                    regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(20, 0), 120)),
                ),
                "UTC",
            )
        }

        assertEquals(0, count("SELECT count(*) FROM access_groups WHERE owner_user_id = '$owner'"))
        assertEquals(0, count("SELECT count(*) FROM group_venues"))
        assertEquals(0, count("SELECT count(*) FROM group_regular_slots"))
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

    private fun validProfile(
        name: String = "Training Club",
        modality: GroupModality = GroupModality.COURT_VOLLEYBALL,
        composition: GroupComposition = GroupComposition.MIXED,
        description: String? = null,
        city: String? = null,
        level: GroupLevel? = GroupLevel.INTERMEDIATE,
        customLevel: String? = null,
        playStyle: CourtPlayStyle? = CourtPlayStyle.FIVE_ONE,
        customPlayStyle: String? = null,
        defaultVenue: GroupVenueInput? = null,
        regularSlots: List<RegularSlotInput> = emptyList(),
        defaultCapacity: Int? = 18,
        defaultConfirmationLeadMinutes: Int? = 180,
        defaultGameFeeCents: Long? = 1500,
        monthlyFeeCents: Long? = null,
        monthlyDueDay: Int? = null,
    ) = GroupProfileDefaultsInput(
        name = name,
        modality = modality,
        composition = composition,
        description = description,
        city = city,
        level = level,
        customLevel = customLevel,
        playStyle = playStyle,
        customPlayStyle = customPlayStyle,
        defaultVenue = defaultVenue,
        regularSlots = regularSlots,
        defaultCapacity = defaultCapacity,
        defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
        defaultGameFeeCents = defaultGameFeeCents,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )

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
