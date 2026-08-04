package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.statement.FinanceStatementDirection
import br.com.saqz.groups.application.finance.statement.FinanceStatementQuery
import br.com.saqz.groups.application.finance.statement.FinanceStatementType
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFinanceStatementRepositoryIntegrationTest {
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeAll
    fun start() {
        postgres.startAndAwaitJdbc()
        dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
    }

    @BeforeEach
    fun reset() {
        flyway().clean()
        flyway().migrate()
    }

    @AfterAll
    fun stop() = postgres.stop()

    @Test
    fun `mixed page is sorted with signed values and derived summaries`() {
        val fixture = fixture()
        val charge = paidCharge(fixture, "2026-08-20 15:00:00+00", 1000, "Marina Freitas", "CASH")
        expense(fixture, "Racha recebido", 200, "2026-08-18", "RACHA", "IN", "2026-08-18")
        expense(fixture, "Aluguel", 300, "2026-08-19", "VENUE", "OUT", "2026-08-19")
        expense(
            fixture,
            "Saída anterior",
            50,
            "2026-07-31",
            "OTHER",
            "OUT",
            "2026-07-31",
            customCategory = "Fornecedor",
        )

        val page = repository().page(FinanceStatementQuery(fixture.group, YearMonth.of(2026, 8), null, 20, 0))

        assertEquals(listOf(charge, "Aluguel", "Racha recebido"), page.items.map { it.idOrTitle(charge) })
        assertEquals(listOf(1000L, -300L, 200L), page.items.map { it.amountCents })
        assertEquals(FinanceStatementType.CHARGE, page.items.first().type)
        assertEquals("Mensalidade · Marina Freitas", page.items.first().title)
        assertEquals("CASH", page.items.first().paidMethod)
        assertEquals(Instant.parse("2026-08-20T15:00:00Z"), page.items.first().occurredAt)
        assertEquals(1200L, page.summary.totalInCents)
        assertEquals(300L, page.summary.totalOutCents)
        assertEquals(900L, page.summary.periodBalanceCents)
        assertEquals(850L, page.summary.accumulatedBalanceCents)
        assertFalse(page.hasMore)
    }

    @Test
    fun `direction and offset paginate the mixed union and keep summary unfiltered`() {
        val fixture = fixture()
        expense(fixture, "Saída nova", 100, "2026-08-20", "VENUE", "OUT", "2026-08-20")
        expense(fixture, "Saída antiga", 200, "2026-08-10", "VENUE", "OUT", "2026-08-10")
        expense(fixture, "Saída remota", 50, "2026-08-01", "VENUE", "OUT", "2026-08-01")
        expense(fixture, "Entrada", 500, "2026-08-15", "RACHA", "IN", "2026-08-15")

        val page = repository().page(
            FinanceStatementQuery(fixture.group, YearMonth.of(2026, 8), FinanceStatementDirection.OUT, 1, 1),
        )

        assertEquals(listOf("Saída antiga"), page.items.map { it.title })
        assertEquals(listOf(FinanceStatementDirection.OUT), page.items.map { it.direction })
        assertTrue(page.hasMore)
        assertEquals(500L, page.summary.totalInCents)
        assertEquals(350L, page.summary.totalOutCents)
        assertEquals(150L, page.summary.periodBalanceCents)
    }

    @Test
    fun `voided entries and charges outside the selected month are excluded from period`() {
        val fixture = fixture()
        paidCharge(fixture, "2026-07-31 23:00:00+00", 700, "Juliana")
        expense(fixture, "Ativo", 400, "2026-08-05", "VENUE", "OUT", "2026-08-05")
        expense(fixture, "Anulado", 900, "2026-08-06", "VENUE", "OUT", "2026-08-06", status = "VOIDED")

        val page = repository().page(FinanceStatementQuery(fixture.group, YearMonth.of(2026, 8), null, 20, 0))

        assertEquals(listOf("Ativo"), page.items.map { it.title })
        assertEquals(0L, page.summary.totalInCents)
        assertEquals(400L, page.summary.totalOutCents)
        assertEquals(-400L, page.summary.periodBalanceCents)
        assertEquals(300L, page.summary.accumulatedBalanceCents)
    }

    private fun repository() = JdbcFinanceStatementRepository(dataSource)

    private fun fixture(): Fixture {
        val owner = user("owner", "Owner")
        val member = user("member", "Member")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at)
            VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Finance Group', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())
            """.trimIndent(),
        )
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$member', 'ATHLETE', now(), now())",
        )
        return Fixture(group, owner, member)
    }

    private fun paidCharge(
        fixture: Fixture,
        occurredAt: String,
        amount: Long,
        memberDisplayName: String,
        paidMethod: String = "PIX",
    ): UUID {
        val charge = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status, paid_method, created_by_user_id, changed_by_user_id, version, created_at, updated_at, member_display_name)
            VALUES ('$charge', '${fixture.group}', '${fixture.member}', 'MONTHLY', DATE '2026-08-01', $amount, DATE '2026-08-10', 'PAID', '$paidMethod', '${fixture.owner}', '${fixture.owner}', 2, TIMESTAMPTZ '$occurredAt', TIMESTAMPTZ '$occurredAt', '$memberDisplayName')
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO group_charge_events (id, charge_id, group_id, actor_user_id, old_status, new_status, note, occurred_at)
            VALUES ('${UUID.randomUUID()}', '$charge', '${fixture.group}', '${fixture.owner}', 'PENDING', 'PAID', 'Recebido', TIMESTAMPTZ '$occurredAt')
            """.trimIndent(),
        )
        return charge
    }

    private fun expense(
        fixture: Fixture,
        description: String,
        amount: Long,
        date: String,
        category: String,
        direction: String,
        occurredAt: String,
        status: String = "ACTIVE",
        customCategory: String? = null,
    ): UUID {
        val id = UUID.randomUUID()
        val custom = customCategory?.let { "'$it'" } ?: "NULL"
        execute(
            """
            INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, custom_category, status, direction, created_by_user_id, changed_by_user_id, version, created_at, updated_at)
            VALUES ('$id', '${fixture.group}', '$description', $amount, DATE '$date', '$category', $custom, '$status', '$direction', '${fixture.owner}', '${fixture.owner}', 1, TIMESTAMPTZ '${occurredAt}T12:00:00+00', TIMESTAMPTZ '${occurredAt}T12:00:00+00')
            """.trimIndent(),
        )
        return id
    }

    private fun user(subject: String, displayName: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES ('$id', '$subject-${UUID.randomUUID()}', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun flyway() = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private data class Fixture(val group: UUID, val owner: UUID, val member: UUID)

    private fun br.com.saqz.groups.application.finance.statement.FinanceStatementItem.idOrTitle(charge: UUID): Any =
        if (id == charge) id else title
}
