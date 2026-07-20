package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.expense.*
import br.com.saqz.groups.domain.finance.expense.*
import br.com.saqz.groups.testing.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.*
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcExpenseRepositoryIntegrationTest{
    private val postgres=PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));private lateinit var dataSource:DriverManagerDataSource
    @BeforeAll fun start(){postgres.startAndAwaitJdbc();dataSource=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password)};@BeforeEach fun reset(){flyway().clean();flyway().migrate()};@AfterAll fun stop()=postgres.stop()
    @Test fun `owner and admin create list and receive active total`(){val f=fixture();val saved=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft()));assertEquals(5000,saved.value.expense.snapshot.amountCents);assertEquals(5000,assertIs<ExpenseResult.Listed>(f.service.list(f.admin,f.group)).value.activeTotalCents)}
    @Test fun `athlete cannot read entries or totals`(){val f=fixture();assertSame(ExpenseResult.Forbidden,f.service.list(f.athlete,f.group));assertSame(ExpenseResult.Forbidden,f.service.create(f.athlete,f.group,draft()))}
    @Test fun `nonmember and missing group are privacy hidden`(){val f=fixture();assertSame(ExpenseResult.Hidden,f.service.list(UUID.randomUUID(),f.group));assertSame(ExpenseResult.Hidden,f.service.list(f.owner,UUID.randomUUID()))}
    @Test fun `create persists immutable creator snapshot event`(){val f=fixture();val saved=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft())).value;assertEquals(1,int("SELECT count(*) FROM group_expense_events"));assertEquals("CREATED",string("SELECT action FROM group_expense_events"));assertEquals(saved.expense.snapshot.description,string("SELECT description FROM group_expense_events"))}
    @Test fun `edit appends new snapshot without overwriting prior event`(){val f=fixture();val created=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft())).value.expense;val edited=assertIs<ExpenseResult.Saved>(f.service.edit(f.admin,f.group,created.id,1,draft().copy(amountCents=7000))).value;assertEquals(2,edited.expense.version);assertEquals(2,int("SELECT count(*) FROM group_expense_events"));assertEquals(listOf(5000,7000),ints("SELECT amount_cents FROM group_expense_events ORDER BY version"))}
    @Test fun `stale edit preserves authoritative expense and history`(){val f=fixture();val created=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft())).value.expense;f.service.edit(f.owner,f.group,created.id,1,draft().copy(amountCents=7000));assertSame(ExpenseResult.Conflict,f.service.edit(f.owner,f.group,created.id,1,draft().copy(amountCents=9000)));assertEquals(7000,int("SELECT amount_cents FROM group_expenses"));assertEquals(2,int("SELECT count(*) FROM group_expense_events"))}
    @Test fun `void is versioned append only and excluded from active total`(){val f=fixture();val created=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft())).value.expense;val voided=assertIs<ExpenseResult.Saved>(f.service.void(f.owner,f.group,created.id,1)).value;assertEquals(ExpenseStatus.VOIDED,voided.expense.status);assertEquals(0,assertIs<ExpenseResult.Listed>(f.service.list(f.owner,f.group)).value.activeTotalCents);assertEquals("VOIDED",string("SELECT action FROM group_expense_events ORDER BY version DESC LIMIT 1"))}
    @Test fun `voided expense rejects edit and repeated void`(){val f=fixture();val created=assertIs<ExpenseResult.Saved>(f.service.create(f.owner,f.group,draft())).value.expense;f.service.void(f.owner,f.group,created.id,1);assertSame(ExpenseResult.InvalidLifecycle,f.service.edit(f.owner,f.group,created.id,2,draft()));assertSame(ExpenseResult.InvalidLifecycle,f.service.void(f.owner,f.group,created.id,2))}
    @Test fun `injected event failure rolls back expense row`(){val f=fixture();val delegate=JdbcExpenseRepository(dataSource);val failing=object:ExpenseRepository by delegate{override fun create(expense:Expense,event:ExpenseEvent):Boolean{delegate.create(expense,event);error("injected")}};val service=ExpenseService(JdbcTransactionRunner(dataSource),failing,{UUID.randomUUID()}){NOW};assertFailsWith<IllegalStateException>{service.create(f.owner,f.group,draft())};assertEquals(0,int("SELECT count(*) FROM group_expenses"));assertEquals(0,int("SELECT count(*) FROM group_expense_events"))}
    private fun fixture():Fixture{val owner=user("owner");val admin=user("admin");val athlete=user("athlete");val group=UUID.randomUUID();execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())");execute("INSERT INTO group_memberships VALUES ('$group','$admin','ADMIN',now(),now()),('$group','$athlete','ATHLETE',now(),now())");return Fixture(owner,admin,athlete,group,ExpenseService(JdbcTransactionRunner(dataSource),JdbcExpenseRepository(dataSource),{UUID.randomUUID()}){NOW})}
    private fun user(s:String):UUID{val id=UUID.randomUUID();execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$s-${UUID.randomUUID()}',true,'User',now(),now())");return id};private fun draft()=ExpenseDraft("Aluguel",5000,LocalDate.of(2026,8,1),ExpenseCategory.VENUE,null,"Quadra")
    private data class Fixture(val owner:UUID,val admin:UUID,val athlete:UUID,val group:UUID,val service:ExpenseService)
    private fun flyway()=Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load();private fun execute(sql:String){dataSource.connection.use{c->c.createStatement().use{it.execute(sql)}}};private fun int(sql:String)=query(sql){it.getInt(1)};private fun string(sql:String)=query(sql){it.getString(1)};private fun ints(sql:String)=dataSource.connection.use{c->c.createStatement().use{s->s.executeQuery(sql).use{r->buildList{while(r.next())add(r.getInt(1))}}}};private fun<T> query(sql:String,read:(java.sql.ResultSet)->T):T=dataSource.connection.use{c->c.createStatement().use{s->s.executeQuery(sql).use{r->check(r.next());read(r)}}};private companion object{val NOW=Instant.parse("2026-08-01T10:00:00Z")}
}
