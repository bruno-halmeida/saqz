package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.athlete.JdbcAthleteRepository
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.athlete.RemoveAthlete
import br.com.saqz.groups.application.athlete.RemoveAthleteResult
import br.com.saqz.groups.application.finance.charge.ChargeTransactions
import br.com.saqz.groups.application.finance.charge.MonthlyChargeSchedule
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * DEB-026 (SAI-006): cobrança já criada de quem sai ou é removido continua pendente no caixa, e o mês seguinte não gera
 * mensalidade nova para quem não é mais membro. Remoção e saída não mexem em `group_charges`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PendenciaDeQuemSaiIntegrationTest {
    private lateinit var dataSource: DriverManagerDataSource

    @BeforeEach fun reset() { dataSource = TestPostgres.migrated(*allGroupFeatureMigrationLocations(), owner = this).dataSource }

    private class World(val owner: UUID, val admin: UUID, val group: UUID, val mensalista: UUID, val avulso: UUID, val stays: UUID)

    @Test fun `mensalista que sai mantem a mensalidade pendente e nao recebe a do mes seguinte`() {
        val w = world()
        val monthly = charge(w, w.mensalista, "MONTHLY", "2026-10-01", "2026-10-05")

        assertSame(RemoveAthleteResult.Success, remove().leave(w.mensalista, w.group))

        assertEquals("PENDING", status(monthly))
        assertEquals(1, count("SELECT count(*) FROM group_membership_removals WHERE group_id='${w.group}' AND user_id='${w.mensalista}'"))
        runMonthlyJob(LocalDate.of(2026, 11, 20))
        assertEquals(0, count("SELECT count(*) FROM group_charges WHERE member_user_id='${w.mensalista}' AND billing_month = DATE '2026-11-01'"))
        // Controle: quem continua mensalista no grupo recebe a mensalidade de novembro.
        assertEquals(1, count("SELECT count(*) FROM group_charges WHERE member_user_id='${w.stays}' AND billing_month = DATE '2026-11-01'"))
        assertEquals("PENDING", status(monthly))
    }

    @Test fun `mensalista removido pelo dono mantem a mensalidade pendente e nao recebe a do mes seguinte`() {
        val w = world()
        val monthly = charge(w, w.mensalista, "MONTHLY", "2026-10-01", "2026-10-05")

        assertSame(RemoveAthleteResult.Success, remove().execute(w.owner, w.group, w.mensalista))

        assertEquals("PENDING", status(monthly))
        runMonthlyJob(LocalDate.of(2026, 11, 20))
        assertEquals(0, count("SELECT count(*) FROM group_charges WHERE member_user_id='${w.mensalista}' AND billing_month = DATE '2026-11-01'"))
        assertEquals(1, count("SELECT count(*) FROM group_charges WHERE member_user_id='${w.stays}' AND billing_month = DATE '2026-11-01'"))
    }

    @Test fun `avulso removido pelo admin mantem a diaria pendente`() {
        val w = world()
        val diaria = gameCharge(w, w.avulso)

        assertSame(RemoveAthleteResult.Success, remove().execute(w.admin, w.group, w.avulso))

        assertEquals("PENDING", status(diaria))
        assertEquals(2500, count("SELECT amount_cents FROM group_charges WHERE id='$diaria'"))
    }

    @Test fun `avulso que sai mantem a diaria pendente`() {
        val w = world()
        val diaria = gameCharge(w, w.avulso)

        assertSame(RemoveAthleteResult.Success, remove().leave(w.avulso, w.group))

        assertEquals("PENDING", status(diaria))
    }

    @Test fun `quem volta ao grupo reencontra as cobrancas antigas`() {
        val w = world()
        val monthly = charge(w, w.mensalista, "MONTHLY", "2026-10-01", "2026-10-05")
        remove().leave(w.mensalista, w.group)
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('${w.group}','${w.mensalista}','ATHLETE',now(),now(),'MENSALISTA')")

        assertEquals(1, count("SELECT count(*) FROM group_charges WHERE id='$monthly' AND member_user_id='${w.mensalista}' AND status='PENDING'"))
    }

    private fun runMonthlyJob(today: LocalDate) {
        val charges = ChargeTransactions(JdbcTransactionRunner(dataSource), JdbcChargeTransactionRepository(dataSource)) { Instant.now() }
        MonthlyChargeSchedule(JdbcChargeTransactionRepository(dataSource), charges, { today }) { m, why -> error("pulou ${m.memberId}: $why") }.run()
    }

    private fun remove() = RemoveAthlete(JdbcTransactionRunner(dataSource), JdbcGroupReadRepository(dataSource), JdbcAthleteRepository(dataSource), GroupAccessPolicy())

    private fun world(): World {
        val owner = user("owner"); val admin = user("admin")
        val group = UUID.randomUUID()
        execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,monthly_fee_cents,monthly_due_day,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Grupo','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',5000,5,now(),now())")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type) VALUES ('$group','$admin','ADMIN',now(),now(),'AVULSO')")
        val mensalista = user("mensalista"); val avulso = user("avulso"); val stays = user("stays")
        for ((id, type) in listOf(mensalista to "MENSALISTA", avulso to "AVULSO", stays to "MENSALISTA")) {
            execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at,membership_type,mensalista_since) VALUES ('$group','$id','ATHLETE',now(),now(),'$type',${if (type == "MENSALISTA") "DATE '2026-09-01'" else "NULL"})")
        }
        return World(owner, admin, group, mensalista, avulso, stays)
    }

    private fun charge(w: World, member: UUID, kind: String, month: String, due: String): UUID {
        val id = UUID.randomUUID()
        execute("INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name,status) VALUES ('$id','${w.group}','$member','$kind',DATE '$month',5000,DATE '$due','${w.owner}','${w.owner}',now(),now(),'User','PENDING')")
        return id
    }

    private fun gameCharge(w: World, member: UUID): UUID {
        val game = UUID.randomUUID()
        execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,published_at,created_at,updated_at) VALUES ('$game','${w.group}','Treino',(CURRENT_DATE + 2),TIME '19:30','America/Sao_Paulo',now() + interval '2 days',90,now() + interval '1 day','Arena','Rua Central 100',4,2500,'PUBLISHED',now(),now(),now())")
        val id = UUID.randomUUID()
        execute("INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,guest_seq,amount_cents,due_date,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name,status) VALUES ('$id','${w.group}','$member','GAME','$game',0,2500,CURRENT_DATE + 2,'${w.owner}','${w.owner}',now(),now(),'User','PENDING')")
        return id
    }

    private fun status(charge: UUID) = string("SELECT status::text FROM group_charges WHERE id='$charge'")
    private fun user(subject: String): UUID { val id = UUID.randomUUID(); execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())"); return id }
    private fun execute(sql: String) { dataSource.connection.use { c -> c.createStatement().use { it.execute(sql) } } }
    private fun count(sql: String) = query(sql) { it.getInt(1) }
    private fun string(sql: String) = query(sql) { it.getString(1) }
    private fun <T> query(sql: String, read: (java.sql.ResultSet) -> T): T = dataSource.connection.use { c -> c.createStatement().use { s -> s.executeQuery(sql).use { r -> check(r.next()); read(r) } } }
}
