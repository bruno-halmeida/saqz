package br.com.saqz.groups.adapter.output.jdbc.migration

import br.com.saqz.groups.testing.allGroupFeatureMigrationLocations
import br.com.saqz.groups.testing.startAndAwaitJdbc
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinanceMigrationIntegrationTest {
    private val postgres=PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));private lateinit var dataSource:DriverManagerDataSource
    @BeforeAll fun start(){postgres.startAndAwaitJdbc();dataSource=DriverManagerDataSource(postgres.jdbcUrl,postgres.username,postgres.password)}
    @BeforeEach fun reset(){flyway().clean();flyway().migrate()}
    @AfterAll fun stop()=postgres.stop()

    @Test fun `v5 creates finance and audit tables`()=assertEquals(4,int("SELECT count(*) FROM information_schema.tables WHERE table_name IN ('group_charges','group_charge_events','group_expenses','group_expense_events')"))
    @Test fun `game charge stores immutable identity and audit fields`(){val f=fixture();val c=charge(f,"GAME",game=f.game);assertEquals(2500,int("SELECT amount_cents FROM group_charges WHERE id='$c'"));assertEquals("PENDING",string("SELECT status FROM group_charges WHERE id='$c'"))}
    @Test fun `monthly charge stores normalized month`(){val f=fixture();val c=charge(f,"MONTHLY",month="2026-08-01");assertEquals("2026-08-01",string("SELECT billing_month::text FROM group_charges WHERE id='$c'"))}
    @Test fun `charge rejects non positive amount`(){val f=fixture();fails{charge(f,"GAME",game=f.game,amount=0)}}
    @Test fun `charge rejects contradictory game identity`(){val f=fixture();fails{charge(f,"GAME",game=null)}}
    @Test fun `monthly charge rejects non first day`(){val f=fixture();fails{charge(f,"MONTHLY",month="2026-08-02")}}
    @Test fun `one game charge per member is unique`(){val f=fixture();charge(f,"GAME",game=f.game);fails{charge(f,"GAME",game=f.game)}}
    @Test fun `one monthly charge per member and month is unique`(){val f=fixture();charge(f,"MONTHLY",month="2026-08-01");fails{charge(f,"MONTHLY",month="2026-08-01")}}
    @Test fun `charge game must belong to same group`(){val f=fixture();val other=fixture("other");fails{charge(f,"GAME",game=other.game)}}
    @Test fun `charge rejects unknown status`(){val f=fixture();val c=charge(f,"GAME",game=f.game);fails{execute("UPDATE group_charges SET status='SETTLED' WHERE id='$c'")}}
    @Test fun `charge rejects invalid version`(){val f=fixture();val c=charge(f,"GAME",game=f.game);fails{execute("UPDATE group_charges SET version=0 WHERE id='$c'")}}
    @Test fun `charge event cannot be updated`(){val f=fixture();val e=chargeEvent(f,charge(f,"GAME",game=f.game));fails{execute("UPDATE group_charge_events SET note='changed' WHERE id='$e'")}}
    @Test fun `charge event cannot be deleted`(){val f=fixture();val e=chargeEvent(f,charge(f,"GAME",game=f.game));fails{execute("DELETE FROM group_charge_events WHERE id='$e'")}}
    @Test fun `expense stores exact organizer fields`(){val f=fixture();val e=expense(f);assertEquals("VENUE",string("SELECT category FROM group_expenses WHERE id='$e'"));assertEquals(5000,int("SELECT amount_cents FROM group_expenses WHERE id='$e'"))}
    @Test fun `other expense requires custom category`(){val f=fixture();fails{expense(f,category="OTHER",custom=null)}}
    @Test fun `standard expense rejects custom category`(){val f=fixture();fails{expense(f,category="VENUE",custom="Custom")}}
    @Test fun `expense rejects invalid description amount notes and status`(){val f=fixture();fails{expense(f,description="X")};fails{expense(f,amount=0)};fails{expense(f,notes=" ")};val e=expense(f);fails{execute("UPDATE group_expenses SET status='PAID' WHERE id='$e'")}}
    @Test fun `expense events are append only and schema has no payment rail fields`(){val f=fixture();val expense=expense(f);val event=UUID.randomUUID();execute("INSERT INTO group_expense_events (id,expense_id,group_id,actor_user_id,action,description,amount_cents,expense_date,category,status,version,occurred_at) VALUES ('$event','$expense','${f.group}','${f.actor}','CREATED','Aluguel',5000,DATE '2026-08-01','VENUE','ACTIVE',1,now())");fails{execute("DELETE FROM group_expense_events WHERE id='$event'")};val sql=Files.readString(Path.of("src/main/resources/db/migration/V5__add_group_finance.sql")).lowercase();listOf("processor","credential","webhook","settlement","refund","balance","transfer").forEach{assertFalse(sql.contains(it))}}

    private fun fixture(subject:String="finance"):Fixture{val actor=user("$subject-owner");val member=user("$subject-member");val group=UUID.randomUUID();execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$actor','${UUID.randomUUID()}','Finance Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())");execute("INSERT INTO group_memberships VALUES ('$group','$member','ATHLETE',now(),now())");val game=UUID.randomUUID();execute("INSERT INTO games (id,group_id,title,local_date,local_time,zone_id,starts_at,duration_minutes,confirmation_deadline,venue_name,venue_address,capacity,game_fee_cents,status,created_at,updated_at) VALUES ('$game','$group','Treino',DATE '2026-08-12',TIME '19:30','America/Sao_Paulo',TIMESTAMPTZ '2026-08-12 22:30Z',90,TIMESTAMPTZ '2026-08-11 22:30Z','Arena','Rua Central 100',24,2500,'PUBLISHED',now(),now())");return Fixture(group,actor,member,game)}
    private fun user(subject:String):UUID{val id=UUID.randomUUID();execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())");return id}
    private fun charge(f:Fixture,kind:String,game:UUID?=null,month:String?=null,amount:Long=2500):UUID{val id=UUID.randomUUID();execute("INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,billing_month,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at) VALUES ('$id','${f.group}','${f.member}','$kind',${game.q()},${month?.let{"DATE '$it'"}?:"NULL"},$amount,DATE '2026-08-10','PENDING','${f.actor}','${f.actor}',now(),now())");return id}
    private fun chargeEvent(f:Fixture,charge:UUID):UUID{val id=UUID.randomUUID();execute("INSERT INTO group_charge_events VALUES ('$id','$charge','${f.group}','${f.actor}',NULL,'PENDING',NULL,now())");return id}
    private fun expense(f:Fixture,description:String="Aluguel",amount:Long=5000,category:String="VENUE",custom:String?=null,notes:String?="Quadra"):UUID{val id=UUID.randomUUID();execute("INSERT INTO group_expenses (id,group_id,description,amount_cents,expense_date,category,custom_category,notes,status,created_by_user_id,changed_by_user_id,created_at,updated_at) VALUES ('$id','${f.group}','$description',$amount,DATE '2026-08-01','$category',${custom.q()},${notes.q()},'ACTIVE','${f.actor}','${f.actor}',now(),now())");return id}
    private fun flyway()=Flyway.configure().dataSource(dataSource).locations(*allGroupFeatureMigrationLocations()).cleanDisabled(false).load()
    private fun execute(sql:String){connection().use{c->c.createStatement().use{it.execute(sql)}}}
    private fun int(sql:String)=query(sql){it.getInt(1)};private fun string(sql:String)=query(sql){it.getString(1)}
    private fun <T> query(sql:String,read:(java.sql.ResultSet)->T):T=connection().use{c->c.createStatement().use{s->s.executeQuery(sql).use{r->check(r.next());read(r)}}}
    private fun connection():Connection=dataSource.connection
    private fun fails(block:()->Unit){assertFailsWith<Exception>{block()}}
    private fun Any?.q()=this?.let{"'$it'"}?:"NULL"
    private data class Fixture(val group:UUID,val actor:UUID,val member:UUID,val game:UUID)
}
