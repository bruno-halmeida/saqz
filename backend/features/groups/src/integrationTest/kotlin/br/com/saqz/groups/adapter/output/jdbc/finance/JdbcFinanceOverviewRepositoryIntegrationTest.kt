package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.overview.FinanceOverviewPeriod
import br.com.saqz.groups.domain.finance.expense.ExpenseDirection
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
import java.time.ZoneId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcFinanceOverviewRepositoryIntegrationTest {
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
    fun `owner and admin groups expose accumulated formula period totals and structured health`() {
        val fixture = fixture()

        val overview = JdbcFinanceOverviewRepository(dataSource, ZONE)
            .find(fixture.actor, FinanceOverviewPeriod.Month(YearMonth.of(2026, 8)))

        assertEquals(1600, overview.totals.balanceCents)
        assertEquals(1900, overview.totals.inCents)
        assertEquals(300, overview.totals.outCents)
        assertEquals(500, overview.totals.pendingCents)
        assertEquals(listOf("Admin group", "Owner group"), overview.groups.map { it.name })

        val adminGroup = overview.groups.first { it.id == fixture.adminGroup }
        assertEquals(250, adminGroup.balanceCents)
        assertEquals(0, adminGroup.pendingMonthlyCount)
        assertFalse(adminGroup.hasBillingConfigured)

        val ownerGroup = overview.groups.first { it.id == fixture.ownerGroup }
        assertEquals(1350, ownerGroup.balanceCents)
        assertEquals(2, ownerGroup.pendingMonthlyCount)
        assertTrue(ownerGroup.hasBillingConfigured)
        assertTrue(overview.groups.none { it.id == fixture.athleteGroup })
        assertEquals(5, overview.recentTransactions.size)
        assertEquals("Admin group", overview.recentTransactions.first().groupName)
        assertEquals(ExpenseDirection.OUT, overview.recentTransactions.first().direction)
        assertEquals("Owner group", overview.recentTransactions.last().groupName)
        assertEquals(ExpenseDirection.IN, overview.recentTransactions.last().direction)
    }

    @Test
    fun `period without movement keeps accumulated balances but returns no recent activity`() {
        val fixture = fixture()

        val overview = JdbcFinanceOverviewRepository(dataSource, ZONE)
            .find(fixture.actor, FinanceOverviewPeriod.Month(YearMonth.of(2026, 9)))

        assertEquals(1600, overview.totals.balanceCents)
        assertEquals(0, overview.totals.inCents)
        assertEquals(0, overview.totals.outCents)
        assertEquals(0, overview.totals.pendingCents)
        assertEquals(2, overview.groups.first { it.id == fixture.ownerGroup }.pendingMonthlyCount)
        assertTrue(overview.recentTransactions.isEmpty())
    }

    @Test
    fun `unknown actor and soft deleted groups return an empty authorized aggregate`() {
        val fixture = fixture()
        execute("UPDATE access_groups SET deleted_at = now() WHERE id = '${fixture.ownerGroup}'")

        val unknown = JdbcFinanceOverviewRepository(dataSource, ZONE)
            .find(UUID.randomUUID(), FinanceOverviewPeriod.Month(YearMonth.of(2026, 8)))
        val deleted = JdbcFinanceOverviewRepository(dataSource, ZONE)
            .find(fixture.actor, FinanceOverviewPeriod.Month(YearMonth.of(2026, 8)))

        assertTrue(unknown.groups.isEmpty())
        assertEquals(0, unknown.totals.balanceCents)
        assertTrue(deleted.groups.none { it.id == fixture.ownerGroup })
        assertEquals(250, deleted.totals.balanceCents)
    }

    private fun fixture(): Fixture {
        val actor = user("organizer", "Organizer")
        val otherOwner = user("other-owner", "Other owner")
        val ownerMember = user("owner-member", "Marina Freitas")
        val ownerPendingMember = user("owner-pending-member", "Pending member")
        val adminMember = user("admin-member", "Admin member")
        val athleteMember = user("athlete-member", "Athlete member")
        val ownerGroup = group("Owner group", actor, monthlyFee = 1000)
        val adminGroup = group("Admin group", otherOwner)
        val athleteGroup = group("Athlete group", otherOwner)
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) VALUES ('$adminGroup','$actor','ADMIN',now(),now()),('$athleteGroup','$actor','ATHLETE',now(),now())")

        paidMonthly(ownerGroup, ownerMember, 1000, "2026-08-03 12:00:00+00")
        monthly(ownerGroup, ownerPendingMember, 500, "2026-08-20", "2026-08-01")
        monthly(ownerGroup, ownerPendingMember, 400, "2026-07-20", "2026-07-01")
        paidMonthly(adminGroup, adminMember, 200, "2026-08-06 12:00:00+00")
        paidMonthly(athleteGroup, athleteMember, 9999, "2026-08-10 12:00:00+00")
        expense(ownerGroup, actor, "Entrada do owner", 600, "2026-08-04", ExpenseDirection.IN)
        expense(ownerGroup, actor, "Saída do owner", 250, "2026-08-05", ExpenseDirection.OUT)
        expense(adminGroup, actor, "Entrada do admin", 100, "2026-08-07", ExpenseDirection.IN)
        expense(adminGroup, actor, "Saída do admin", 50, "2026-08-08", ExpenseDirection.OUT)
        expense(athleteGroup, actor, "Segredo do atleta", 8000, "2026-08-09", ExpenseDirection.OUT)
        return Fixture(actor, ownerGroup, adminGroup, athleteGroup)
    }

    private fun group(name: String, owner: UUID, monthlyFee: Long? = null): UUID {
        val id = UUID.randomUUID()
        val fee = monthlyFee?.toString() ?: "NULL"
        val dueDay = monthlyFee?.let { "10" } ?: "NULL"
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,monthly_fee_cents,monthly_due_day,created_at,updated_at) VALUES ('$id','$owner','${UUID.randomUUID()}','$name','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',$fee,$dueDay,now(),now())")
        return id
    }

    private fun user(subject: String, displayName: String): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'$displayName',now(),now())")
        return id
    }

    private fun paidMonthly(group: UUID, member: UUID, amount: Long, occurredAt: String) {
        val id = monthly(group, member, amount, "2026-08-10", "2026-08-01", "PAID")
        execute("INSERT INTO group_charge_events (id,charge_id,group_id,actor_user_id,old_status,new_status,occurred_at) VALUES ('${UUID.randomUUID()}','$id','$group','$member','PENDING','PAID',TIMESTAMPTZ '$occurredAt')")
    }

    private fun monthly(
        group: UUID,
        member: UUID,
        amount: Long,
        dueDate: String,
        billingMonth: String,
        status: String = "PENDING",
    ): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) VALUES ('$id','$group','$member','MONTHLY',DATE '$billingMonth',$amount,DATE '$dueDate','$status','$member','$member',TIMESTAMPTZ '2026-08-01 10:00:00+00',TIMESTAMPTZ '2026-08-01 10:00:00+00','${memberName(member)}')")
        return id
    }

    private fun memberName(member: UUID): String = query("SELECT display_name FROM access_users WHERE id = '$member'") { it.getString(1) }

    private fun expense(group: UUID, actor: UUID, description: String, amount: Long, date: String, direction: ExpenseDirection) {
        execute("INSERT INTO group_expenses (id,group_id,description,amount_cents,expense_date,category,custom_category,notes,direction,status,created_by_user_id,changed_by_user_id,version,created_at,updated_at) VALUES ('${UUID.randomUUID()}','$group','$description',$amount,DATE '$date','${if (direction == ExpenseDirection.IN) "RACHA" else "VENUE"}',NULL,'Nota','${direction.name}','ACTIVE','$actor','$actor',1,TIMESTAMPTZ '2026-08-01 10:00:00+00',TIMESTAMPTZ '2026-08-01 10:00:00+00')")
    }

    private fun flyway() = Flyway.configure()
        .dataSource(dataSource)
        .locations(*allGroupFeatureMigrationLocations())
        .cleanDisabled(false)
        .load()

    private fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    check(result.next())
                    read(result)
                }
            }
        }

    private data class Fixture(
        val actor: UUID,
        val ownerGroup: UUID,
        val adminGroup: UUID,
        val athleteGroup: UUID,
    )

    private companion object {
        val ZONE = ZoneId.of("America/Sao_Paulo")
    }
}
