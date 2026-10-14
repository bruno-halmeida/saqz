package br.com.saqz.groups.adapter.output.jdbc.report

import br.com.saqz.groups.application.report.ReportPeriod
import br.com.saqz.groups.application.report.ReportRange
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcGroupReportRepositoryIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = Instant.parse("2026-09-15T15:00:00Z")
    private val august = ReportRange.of(ReportPeriod.CURRENT_MONTH, LocalDate.of(2026, 8, 15), zone)

    @BeforeEach
    fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
    }

    @Test
    fun `role resolves owner admin athlete and hides outsiders`() {
        val f = fixture()
        val outsider = user("Fora")

        assertEquals(GroupRole.OWNER, repository().role(f.owner, f.group))
        assertEquals(GroupRole.ATHLETE, repository().role(f.a, f.group))
        assertNull(repository().role(outsider, f.group))
        assertEquals(f.owner, repository().ownerOf(f.group))
        assertEquals(zone, repository().timeZone(f.group))
    }

    @Test
    fun `games count only published or completed games that already happened in the period`() {
        val f = fixture()
        val g1 = game(f, "Racha 1", "2026-08-10 22:00:00+00", "COMPLETED", capacity = 12)
        val g2 = game(f, "Racha 2", "2026-08-20 22:00:00+00", "PUBLISHED", capacity = 10)
        game(f, "Setembro", "2026-09-05 22:00:00+00", "COMPLETED")
        game(f, "Rascunho", "2026-08-12 22:00:00+00", "DRAFT")
        game(f, "Cancelado", "2026-08-13 22:00:00+00", "CANCELLED")
        game(f, "Futuro", "2026-10-05 22:00:00+00", "PUBLISHED")
        attendance(f, g1, f.a, "CONFIRMED")
        attendance(f, g1, f.b, "DECLINED")
        guest(f, g1, f.a, 1)
        attendance(f, g2, f.a, "CONFIRMED")
        attendance(f, g2, f.b, "DECLINED")
        attendance(f, g2, f.c, "WAITLISTED", waitlistSequence = 1)

        val games = repository().games(f.group, august, now)

        assertEquals(listOf("Racha 1", "Racha 2"), games.map { it.title })
        val first = games[0]
        assertEquals(12, first.capacity)
        assertEquals(2, first.confirmed) // A + convidado
        assertEquals(1, first.confirmedMembers)
        assertEquals(1, first.declined)
        assertEquals(0, first.waitlisted)
        assertEquals(2, first.noResponse) // C e E nunca responderam; D está inativo
        val second = games[1]
        assertEquals(1, second.confirmed)
        assertEquals(1, second.declined)
        assertEquals(1, second.waitlisted)
        assertEquals(1, second.noResponse) // só E
    }

    @Test
    fun `members count presence refusals and silence per athlete ignoring inactive and recent joiners`() {
        val f = fixture()
        val g1 = game(f, "Racha 1", "2026-08-10 22:00:00+00", "COMPLETED")
        val g2 = game(f, "Racha 2", "2026-08-20 22:00:00+00", "COMPLETED")
        attendance(f, g1, f.a, "CONFIRMED")
        attendance(f, g2, f.a, "CONFIRMED")
        attendance(f, g1, f.b, "DECLINED")
        attendance(f, g2, f.b, "DECLINED")
        attendance(f, g2, f.c, "CONFIRMED")
        val late = user("Entrou depois")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('${f.group}', '$late', 'ATHLETE', TIMESTAMPTZ '2026-08-25 12:00:00+00', now())",
        )

        val byName = repository().members(f.group, august, now).associateBy { it.name }

        assertEquals(setOf("Ana", "Bia", "Caio", "Edu", "Entrou depois"), byName.keys)
        assertEquals(2, byName.getValue("Ana").confirmed)
        assertEquals(2, byName.getValue("Bia").declined)
        assertEquals(1, byName.getValue("Caio").responded)
        assertEquals(2, byName.getValue("Edu").games)
        assertEquals(0, byName.getValue("Edu").responded)
        assertEquals(0, byName.getValue("Entrou depois").games)
    }

    @Test
    fun `ledger sums paid charges and launches of the period only`() {
        val f = fixture()
        paidCharge(f, f.a, "2026-08-20 15:00:00+00", 12_000, "Ana")
        paidCharge(f, f.b, "2026-09-02 15:00:00+00", 9_900, "Bia", month = "2026-09-01")
        expense(f, "Racha recebido", 2_000, "2026-08-18", "IN")
        expense(f, "Aluguel", 30_000, "2026-08-19", "OUT")
        expense(f, "Julho", 1_000, "2026-07-31", "OUT")
        expense(f, "Anulada", 5_000, "2026-08-21", "OUT", status = "VOIDED")

        val rows = repository().ledger(f.group, august)

        assertEquals(listOf("Racha recebido", "Aluguel", "Mensalidade · Ana").sorted(), rows.map { it.title }.sorted())
        assertEquals(14_000L, rows.filter { it.direction == "IN" }.sumOf { it.amountCents })
        assertEquals(30_000L, rows.filter { it.direction == "OUT" }.sumOf { it.amountCents })
        assertEquals("PIX", rows.single { it.type == "CHARGE" }.paidMethod)
        assertEquals("Mensalidade", rows.single { it.type == "CHARGE" }.category)
        assertEquals("Quadra", rows.single { it.title == "Aluguel" }.category)
    }

    @Test
    fun `ledger shows portuguese labels and the custom category of other expenses`() {
        val f = fixture()
        execute(
            """
            INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, custom_category, status, direction, created_by_user_id, changed_by_user_id, version, created_at, updated_at)
            VALUES ('${UUID.randomUUID()}', '${f.group}', 'Carne', 8000, DATE '2026-08-18', 'OTHER', 'Churrasco', 'ACTIVE', 'OUT', '${f.owner}', '${f.owner}', 1, now(), now()),
                   ('${UUID.randomUUID()}', '${f.group}', 'Bolas', 9000, DATE '2026-08-19', 'EQUIPMENT', NULL, 'ACTIVE', 'OUT', '${f.owner}', '${f.owner}', 1, now(), now())
            """.trimIndent(),
        )

        val rows = repository().ledger(f.group, august)

        assertEquals("Churrasco", rows.single { it.title == "Carne" }.category)
        assertEquals("Material", rows.single { it.title == "Bolas" }.category)
    }

    @Test
    fun `receivable counts pending charges due in the period and overdue lists monthly debtors`() {
        val f = fixture()
        pendingCharge(f, f.a, "2026-07-01", "2026-07-10", 3_000, "Ana")
        pendingCharge(f, f.a, "2026-08-01", "2026-08-10", 3_000, "Ana")
        pendingCharge(f, f.b, "2026-08-01", "2026-08-25", 2_500, "Bia")
        pendingCharge(f, f.b, "2026-09-01", "2026-09-10", 2_500, "Bia")
        paidCharge(f, f.c, "2026-08-12 15:00:00+00", 3_000, "Caio")

        assertEquals(5_500L, repository().receivableCents(f.group, august))

        val overdue = repository().overdueMonthly(f.group, august, LocalDate.of(2026, 9, 15))

        assertEquals(listOf("Ana", "Bia"), overdue.map { it.name })
        assertEquals(6_000L, overdue[0].amountCents)
        assertEquals(2, overdue[0].months)
        assertEquals(2_500L, overdue[1].amountCents)
        // Para o mês corrente ainda não vencido, ninguém está inadimplente.
        assertEquals(emptyList(), repository().overdueMonthly(f.group, august, LocalDate.of(2026, 7, 1)))
    }

    private fun repository() = JdbcGroupReportRepository(dataSource)

    private fun fixture(): Fixture {
        val owner = user("Dono")
        val a = user("Ana")
        val b = user("Bia")
        val c = user("Caio")
        val d = user("Dani")
        val e = user("Edu")
        val group = UUID.randomUUID()
        execute(
            """
            INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, created_at, updated_at)
            VALUES ('$group', '$owner', '${UUID.randomUUID()}', 'Relatórios', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', now(), now())
            """.trimIndent(),
        )
        for ((user, active) in listOf(a to true, b to true, c to true, d to false, e to true)) {
            execute(
                "INSERT INTO group_memberships (group_id, user_id, role, active, created_at, updated_at) VALUES ('$group', '$user', 'ATHLETE', $active, TIMESTAMPTZ '2026-01-01 12:00:00+00', now())",
            )
        }
        return Fixture(group, owner, a, b, c)
    }

    private fun game(f: Fixture, title: String, startsAt: String, status: String, capacity: Int = 12): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,status,created_at,updated_at) " +
                "VALUES ('$id','${f.group}','$title',(TIMESTAMPTZ '$startsAt' AT TIME ZONE 'America/Sao_Paulo')::date,(TIMESTAMPTZ '$startsAt' AT TIME ZONE 'America/Sao_Paulo')::time,'America/Sao_Paulo',TIMESTAMPTZ '$startsAt',90,TIMESTAMPTZ '$startsAt' - interval '1 hour','Arena','Rua Central 100',$capacity,'$status',now(),now())",
        )
        return id
    }

    private fun attendance(f: Fixture, game: UUID, member: UUID, status: String, waitlistSequence: Int? = null) = execute(
        "INSERT INTO game_attendance (game_id,group_id,member_user_id,status,waitlist_sequence,responded_at,updated_at,member_display_name) " +
            "VALUES ('$game','${f.group}','$member','$status',${waitlistSequence ?: "NULL"},now(),now(),'Nome')",
    )

    private fun guest(f: Fixture, game: UUID, host: UUID, seq: Int) = execute(
        "INSERT INTO game_attendance (game_id,group_id,member_user_id,status,responded_at,updated_at,member_display_name,guest_seq) " +
            "VALUES ('$game','${f.group}','$host','CONFIRMED',now(),now(),'Convidado',$seq)",
    )

    private fun paidCharge(f: Fixture, member: UUID, occurredAt: String, amount: Long, name: String, month: String = "2026-08-01") {
        val charge = UUID.randomUUID()
        execute(
            """
            INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status, paid_method, created_by_user_id, changed_by_user_id, version, created_at, updated_at, member_display_name)
            VALUES ('$charge', '${f.group}', '$member', 'MONTHLY', DATE '$month', $amount, DATE '$month', 'PAID', 'PIX', '${f.owner}', '${f.owner}', 2, TIMESTAMPTZ '$occurredAt', TIMESTAMPTZ '$occurredAt', '$name')
            """.trimIndent(),
        )
        execute(
            """
            INSERT INTO group_charge_events (id, charge_id, group_id, actor_user_id, old_status, new_status, note, occurred_at)
            VALUES ('${UUID.randomUUID()}', '$charge', '${f.group}', '${f.owner}', 'PENDING', 'PAID', 'Recebido', TIMESTAMPTZ '$occurredAt')
            """.trimIndent(),
        )
    }

    private fun pendingCharge(f: Fixture, member: UUID, month: String, due: String, amount: Long, name: String) = execute(
        """
        INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, status, created_by_user_id, changed_by_user_id, version, created_at, updated_at, member_display_name)
        VALUES ('${UUID.randomUUID()}', '${f.group}', '$member', 'MONTHLY', DATE '$month', $amount, DATE '$due', 'PENDING', '${f.owner}', '${f.owner}', 1, now(), now(), '$name')
        """.trimIndent(),
    )

    private fun expense(f: Fixture, description: String, amount: Long, date: String, direction: String, status: String = "ACTIVE") = execute(
        """
        INSERT INTO group_expenses (id, group_id, description, amount_cents, expense_date, category, status, direction, created_by_user_id, changed_by_user_id, version, created_at, updated_at)
        VALUES ('${UUID.randomUUID()}', '${f.group}', '$description', $amount, DATE '$date', 'VENUE', '$status', '$direction', '${f.owner}', '${f.owner}', 1, now(), now())
        """.trimIndent(),
    )

    private fun user(displayName: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) VALUES ('$id', 'sub-${UUID.randomUUID()}', true, '$displayName', now(), now())",
        )
        return id
    }

    private fun execute(sql: String) {
        dataSource.connection.use { connection -> connection.createStatement().use { it.execute(sql) } }
    }

    private data class Fixture(val group: UUID, val owner: UUID, val a: UUID, val b: UUID, val c: UUID)
}
