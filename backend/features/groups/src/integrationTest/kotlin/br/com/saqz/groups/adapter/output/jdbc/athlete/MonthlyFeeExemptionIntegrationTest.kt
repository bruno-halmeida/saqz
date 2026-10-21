package br.com.saqz.groups.adapter.output.jdbc.athlete

import br.com.saqz.groups.adapter.output.jdbc.finance.JdbcChargeManagementRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.MONTHLY_FEE_EXEMPTION_NOTE
import br.com.saqz.groups.application.athlete.UpdateAthlete
import br.com.saqz.groups.application.athlete.UpdateAthleteCommand
import br.com.saqz.groups.application.athlete.UpdateAthleteResult
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

// DEB-115: o PATCH de atleta grava a isenção e dispensa a pendente do mês atual na mesma transação (MEM-003).
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyFeeExemptionIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource
    private lateinit var update: UpdateAthlete

    @BeforeEach
    fun reset() {
        dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource
        update = UpdateAthlete(
            JdbcTransactionRunner(dataSource),
            JdbcGroupReadRepository(dataSource),
            JdbcAthleteRepository(dataSource),
            GroupAccessPolicy(),
            JdbcChargeManagementRepository(dataSource),
        )
    }

    @Test
    fun `isentar dispensa a pendente do mes atual com nota, evento, versao e quem isentou`() {
        val f = fixture()
        val current = charge(f, CURRENT_MONTH)
        val previous = charge(f, "$CURRENT_MONTH - interval '1 month'")

        val result = update.execute(f.owner, exempt(f, true))

        assertTrue(assertIs<UpdateAthleteResult.Success>(result).athlete.monthlyFeeExempt)
        assertEquals("WAIVED", string("SELECT status FROM group_charges WHERE id = '$current'"))
        assertEquals(2, int("SELECT version FROM group_charges WHERE id = '$current'"))
        assertEquals(f.owner.toString(), string("SELECT changed_by_user_id::text FROM group_charges WHERE id = '$current'"))
        assertEquals(
            MONTHLY_FEE_EXEMPTION_NOTE,
            string("SELECT note FROM group_charge_events WHERE charge_id = '$current' AND new_status = 'WAIVED'"),
        )
        assertEquals("PENDING", string("SELECT status FROM group_charges WHERE id = '$previous'"))
    }

    @Test
    fun `cobranca com ordem eletronica fica como esta`() {
        val f = fixture()
        val current = charge(f, CURRENT_MONTH, electronicOrder = true)

        update.execute(f.owner, exempt(f, true))

        assertEquals("PENDING", string("SELECT status FROM group_charges WHERE id = '$current'"))
        assertEquals(0, int("SELECT count(*) FROM group_charge_events WHERE charge_id = '$current'"))
    }

    @Test
    fun `campo ausente mantem a isencao e o GET devolve o campo`() {
        val f = fixture()
        update.execute(f.owner, exempt(f, true))

        update.execute(f.owner, exempt(f, null))

        assertEquals(true, JdbcAthleteRepository(dataSource).find(f.group, f.member)?.monthlyFeeExempt)
    }

    @Test
    fun `quem sai e volta ao grupo volta sem isencao`() {
        val f = fixture()
        update.execute(f.owner, exempt(f, true))

        JdbcAthleteRepository(dataSource).remove(f.group, f.member)
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, membership_type, created_at, updated_at) " +
                "VALUES ('${f.group}', '${f.member}', 'ATHLETE', 'MENSALISTA', now(), now())",
        )

        assertEquals(false, JdbcAthleteRepository(dataSource).find(f.group, f.member)?.monthlyFeeExempt)
    }

    private fun exempt(f: Fixture, value: Boolean?) = UpdateAthleteCommand(
        f.group, f.member, null, AthleteMembershipType.MENSALISTA, active = true, monthlyFeeExempt = value,
    )

    private fun charge(f: Fixture, monthSql: String, electronicOrder: Boolean = false): UUID {
        val id = UUID.randomUUID()
        val order = if (electronicOrder) "'${UUID.randomUUID()}'" else "NULL"
        execute(
            "INSERT INTO group_charges (id, group_id, member_user_id, kind, billing_month, amount_cents, due_date, " +
                "created_by_user_id, changed_by_user_id, created_at, updated_at, member_display_name, electronic_order_id) VALUES " +
                "('$id', '${f.group}', '${f.member}', 'MONTHLY', ($monthSql)::date, 5000, ($monthSql)::date + 9, " +
                "'${f.owner}', '${f.owner}', now(), now(), 'Pessoa', $order)",
        )
        return id
    }

    private fun fixture(): Fixture {
        val owner = user("owner")
        val group = UUID.randomUUID()
        execute(
            "INSERT INTO access_groups (id, owner_user_id, creation_key, name, time_zone, profile_status, modality, composition, " +
                "monthly_fee_cents, monthly_due_day, created_at, updated_at) VALUES ('$group', '$owner', '${UUID.randomUUID()}', " +
                "'Grupo', 'America/Sao_Paulo', 'COMPLETE', 'COURT_VOLLEYBALL', 'MIXED', 5000, 10, now(), now())",
        )
        execute("INSERT INTO group_memberships (group_id, user_id, role, created_at, updated_at) VALUES ('$group', '$owner', 'ADMIN', now(), now())")
        val member = user("member")
        execute(
            "INSERT INTO group_memberships (group_id, user_id, role, membership_type, created_at, updated_at) " +
                "VALUES ('$group', '$member', 'ATHLETE', 'MENSALISTA', now(), now())",
        )
        return Fixture(owner, member, group)
    }

    private fun user(subject: String): UUID {
        val id = UUID.randomUUID()
        execute(
            "INSERT INTO access_users (id, firebase_subject, email_verified, display_name, created_at, updated_at) " +
                "VALUES ('$id', '$subject-${UUID.randomUUID()}', true, 'Pessoa', now(), now())",
        )
        return id
    }

    private data class Fixture(val owner: UUID, val member: UUID, val group: UUID)

    private fun execute(sql: String) {
        dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } }
    }

    private fun int(sql: String): Int = query(sql) { it.getInt(1) }
    private fun string(sql: String): String = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } }
    }

    private companion object {
        const val CURRENT_MONTH = "date_trunc('month', now() AT TIME ZONE 'America/Sao_Paulo')"
    }
}
