package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.testing.*
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
import java.time.*
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcChargeTransactionRepositoryIntegrationTest{
    private val postgres=PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));private lateinit var dataSource:DriverManagerDataSource
    @BeforeAll fun start(){postgres.startAndAwaitJdbc();dataSource=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password)}
    @BeforeEach fun reset(){flyway().clean();flyway().migrate()}
    @AfterAll fun stop()=postgres.stop()
    @Test fun `confirmed paid game persists one pending charge and audit`(){val f=fixture();val charge=f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);assertNotNull(charge);assertEquals(1,count("group_charges"));assertEquals(1,count("group_charge_events"));assertEquals("PENDING",string("SELECT status FROM group_charges"))}
    @Test fun `confirmation replay is equivalent without duplicate audit`(){val f=fixture();val first=f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);val second=f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);assertEquals(first?.id,second?.id);assertEquals(1,count("group_charges"));assertEquals(1,count("group_charge_events"))}
    @Test fun `waitlisted attendance stores no charge`(){val f=fixture();assertNull(f.service.attendance(f.game(AttendanceBillingOutcome.WAITLISTED),f.owner));assertEquals(0,count("group_charges"))}
    @Test fun `promotion creates same unique game identity`(){val f=fixture();f.service.attendance(f.game(AttendanceBillingOutcome.PROMOTED),f.owner);f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);assertEquals(1,count("group_charges"))}
    @Test fun `withdrawal leaves existing pending charge`(){val f=fixture();f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);assertNull(f.service.attendance(f.game(AttendanceBillingOutcome.WITHDRAWN),f.owner));assertEquals("PENDING",string("SELECT status FROM group_charges"))}
    @Test fun `game cancellation cancels pending and appends event`(){val f=fixture();f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);f.service.cancelGame(f.group,f.game,f.owner);assertEquals("CANCELLED",string("SELECT status FROM group_charges"));assertEquals(2,count("group_charge_events"))}
    @Test fun `game cancellation flags paid and waived for review`(){val f=fixture();f.service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner);execute("UPDATE group_charges SET status='PAID' ");f.service.cancelGame(f.group,f.game,f.owner);assertTrue(boolean("SELECT review_required FROM group_charges"));assertEquals("PAID",string("SELECT status FROM group_charges"))}
    @Test fun `monthly generation creates selected active members only`(){val f=fixture();val result=assertIs<MonthlyGenerationResult.Success>(f.service.generate(f.monthly(setOf(f.member))));assertEquals(listOf(f.member),result.charges.map{it.memberId});assertEquals(1,count("group_charges"));assertEquals(3000,int("SELECT amount_cents FROM group_charges"))}
    @Test fun `monthly retry never rewrites original amount`(){val f=fixture();val first=assertIs<MonthlyGenerationResult.Success>(f.service.generate(f.monthly(setOf(f.member),3000))).charges.single();val retry=assertIs<MonthlyGenerationResult.Success>(f.service.generate(f.monthly(setOf(f.member),9000))).charges.single();assertEquals(first.id,retry.id);assertEquals(3000,retry.amountCents);assertEquals(1,count("group_charge_events"))}
    @Test fun `injected repository failure rolls back charge and audit together`(){val f=fixture();val delegate=JdbcChargeTransactionRepository(dataSource);val failing=object:ChargeTransactionRepository by delegate{override fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge{delegate.createGameCharge(input,actorId,now);error("injected")}};val service=ChargeTransactions(JdbcTransactionRunner(dataSource),failing){NOW};assertFailsWith<IllegalStateException>{service.attendance(f.game(AttendanceBillingOutcome.CONFIRMED),f.owner)};assertEquals(0,count("group_charges"));assertEquals(0,count("group_charge_events"))}

    private fun fixture():Fixture{val owner=user("owner");val member=user("member");val group=UUID.randomUUID();execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())");execute("INSERT INTO group_memberships VALUES ('$group','$member','ATHLETE',now(),now())");val game=UUID.randomUUID();execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',24,2500,'PUBLISHED',now(),now())");return Fixture(owner,member,group,game,ChargeTransactions(JdbcTransactionRunner(dataSource),JdbcChargeTransactionRepository(dataSource)){NOW})}
    private fun user(subject:String):UUID{val id=UUID.randomUUID();execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())");return id}
    private data class Fixture(val owner:UUID,val member:UUID,val group:UUID,val game:UUID,val service:ChargeTransactions){fun game(outcome:AttendanceBillingOutcome)=GameChargeInput(group,game,member,2500,LocalDate.of(2026,8,10),outcome);fun monthly(members:Set<UUID>,amount:Long=3000)=MonthlyGenerationCommand(UUID.randomUUID(),group,owner,YearMonth.of(2026,8),amount,LocalDate.of(2026,8,10),members)}
    private fun flyway()=Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load();private fun execute(sql:String){dataSource.connection.use{c->c.createStatement().use{it.execute(sql)}}};private fun count(table:String)=int("SELECT count(*) FROM $table");private fun int(sql:String)=query(sql){it.getInt(1)};private fun string(sql:String)=query(sql){it.getString(1)};private fun boolean(sql:String)=query(sql){it.getBoolean(1)};private fun<T> query(sql:String,read:(java.sql.ResultSet)->T):T=dataSource.connection.use{c->c.createStatement().use{s->s.executeQuery(sql).use{r->check(r.next());read(r)}}}
    private companion object{val NOW:Instant=Instant.parse("2026-08-01T10:00:00Z")}
}
