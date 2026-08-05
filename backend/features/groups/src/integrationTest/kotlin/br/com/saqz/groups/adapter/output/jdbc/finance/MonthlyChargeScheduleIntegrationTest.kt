package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.testing.*
import br.com.saqz.postgrestesting.TestPostgres
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonthlyChargeScheduleIntegrationTest{
    private lateinit var dataSource:DriverManagerDataSource
    @BeforeEach fun reset(){dataSource=TestPostgres.migrated(*allGroupFeatureMigrationLocations(),owner=this).dataSource}

    @Test fun `generates the monthly charge of a member due today`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth)
        val charges=schedule().run()
        assertEquals(listOf(f.member),charges.map{it.memberId})
        assertEquals(1,count("group_charges"));assertEquals(1,count("group_charge_events"))
        assertEquals(3000,int("SELECT amount_cents FROM group_charges"))
        assertEquals("PENDING",string("SELECT status FROM group_charges"))
        assertEquals(TODAY,date("SELECT due_date FROM group_charges"))
        assertEquals(TODAY.withDayOfMonth(1),date("SELECT billing_month FROM group_charges"))
    }

    @Test fun `a second run on the same day never duplicates the charge`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth)
        val first=schedule().run().single()
        val second=schedule().run().single()
        assertEquals(first.id,second.id)
        assertEquals(1,count("group_charges"));assertEquals(1,count("group_charge_events"))
    }

    @Test fun `falls back to the group default when the member has no override`(){
        val f=fixture();groupDefaults(f.group,feeCents=4500,dueDay=TODAY.dayOfMonth);monthly(f.group,f.member,feeCents=null,dueDay=null)
        assertEquals(listOf(f.member),schedule().run().map{it.memberId})
        assertEquals(4500,int("SELECT amount_cents FROM group_charges"))
        assertEquals(TODAY,date("SELECT due_date FROM group_charges"))
    }

    @Test fun `the member override wins over the group default`(){
        val f=fixture();groupDefaults(f.group,feeCents=4500,dueDay=TODAY.dayOfMonth%28+1);monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth)
        assertEquals(listOf(f.member),schedule().run().map{it.memberId})
        assertEquals(3000,int("SELECT amount_cents FROM group_charges"))
    }

    @Test fun `ignores an avulso member even with a fee and a due day filled in`(){
        val f=fixture();groupDefaults(f.group,feeCents=4500,dueDay=TODAY.dayOfMonth);monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth,type="AVULSO")
        assertEquals(emptyList(),schedule().run())
        assertEquals(0,count("group_charges"))
    }

    @Test fun `ignores a member with no fee on the membership and none on the group`(){
        val f=fixture();monthly(f.group,f.member,feeCents=null,dueDay=TODAY.dayOfMonth)
        assertEquals(emptyList(),schedule().run())
        assertEquals(0,count("group_charges"))
    }

    @Test fun `ignores a member whose due day has not arrived yet`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth+1)
        assertEquals(emptyList(),schedule().run())
        assertEquals(0,count("group_charges"))
    }

    @Test fun `catches up on a due day the job never ran for`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=5)
        val charges=schedule(today=TODAY.withDayOfMonth(20)).run()
        assertEquals(listOf(f.member),charges.map{it.memberId})
        assertEquals(TODAY.withDayOfMonth(5),date("SELECT due_date FROM group_charges"))
        assertEquals(TODAY.withDayOfMonth(1),date("SELECT billing_month FROM group_charges"))
    }

    @Test fun `the catch up never duplicates a charge the month already has`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=5)
        val onTime=schedule(today=TODAY.withDayOfMonth(5)).run().single()
        val catchUp=schedule(today=TODAY.withDayOfMonth(20)).run().single()
        assertEquals(onTime.id,catchUp.id)
        assertEquals(1,count("group_charges"));assertEquals(1,count("group_charge_events"))
    }

    @Test fun `the first run of a new month leaves the previous month alone`(){
        val f=fixture();monthly(f.group,f.member,feeCents=3000,dueDay=5)
        assertEquals(emptyList(),schedule(today=TODAY.plusMonths(1).withDayOfMonth(1)).run())
        assertEquals(0,count("group_charges"))
    }

    @Test fun `skips a member whose fee exceeds the charge ceiling and reports it`(){
        val f=fixture();monthly(f.group,f.member,feeCents=100_000_000,dueDay=TODAY.dayOfMonth)
        val skipped=mutableListOf<UUID>()
        assertEquals(emptyList(),schedule{member,_->skipped+=member.memberId}.run())
        assertEquals(listOf(f.member),skipped);assertEquals(0,count("group_charges"))
    }

    @Test fun `one broken membership does not stop the others`(){
        val f=fixture();val other=user("other")
        execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) VALUES ('${f.group}','$other','ATHLETE',now(),now())")
        monthly(f.group,f.member,feeCents=3000,dueDay=TODAY.dayOfMonth);monthly(f.group,other,feeCents=3000,dueDay=TODAY.dayOfMonth)
        val charges=MonthlyChargeSchedule(JdbcChargeTransactionRepository(dataSource),explodingOn(f.member),{TODAY},{_,_->}).run()
        assertEquals(listOf(other),charges.map{it.memberId})
        assertEquals(1,count("group_charges"))
    }

    private fun schedule(today:LocalDate=TODAY,onSkipped:(MonthlyDueMembership,String)->Unit={_,_->}):MonthlyChargeSchedule{
        val repository=JdbcChargeTransactionRepository(dataSource)
        return MonthlyChargeSchedule(repository,ChargeTransactions(JdbcTransactionRunner(dataSource),repository){NOW},{today},onSkipped)
    }
    private fun explodingOn(member:UUID):ChargeTransactions{
        val delegate=JdbcChargeTransactionRepository(dataSource)
        val repository=object:ChargeTransactionRepository by delegate{override fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant)=if(memberId==member) error("injected") else delegate.createMonthlyCharge(command,memberId,now)}
        return ChargeTransactions(JdbcTransactionRunner(dataSource),repository){NOW}
    }
    private fun monthly(group:UUID,member:UUID,feeCents:Long?,dueDay:Int?,type:String="MENSALISTA"){
        assertTrue(dueDay==null||dueDay in 1..28)
        execute("UPDATE group_memberships SET membership_type='$type',monthly_fee_cents=${feeCents ?: "NULL"},monthly_due_day=${dueDay ?: "NULL"} WHERE group_id='$group' AND user_id='$member'")
    }
    /** Default do grupo (V2 do :features:access): fee e dia andam juntos ou ficam os dois nulos. */
    private fun groupDefaults(group:UUID,feeCents:Long,dueDay:Int){
        assertTrue(dueDay in 1..28)
        execute("UPDATE access_groups SET monthly_fee_cents=$feeCents,monthly_due_day=$dueDay WHERE id='$group'")
    }
    private fun fixture():Fixture{val owner=user("owner");val member=user("member");val group=UUID.randomUUID();execute("INSERT INTO access_groups (id,owner_user_id,creation_key,name,time_zone,profile_status,modality,composition,created_at,updated_at) VALUES ('$group','$owner','${UUID.randomUUID()}','Group','America/Sao_Paulo','COMPLETE','COURT_VOLLEYBALL','MIXED',now(),now())");execute("INSERT INTO group_memberships (group_id,user_id,role,created_at,updated_at) VALUES ('$group','$member','ATHLETE',now(),now())");return Fixture(owner,member,group)}
    private fun user(subject:String):UUID{val id=UUID.randomUUID();execute("INSERT INTO access_users (id,firebase_subject,email_verified,display_name,created_at,updated_at) VALUES ('$id','$subject-${UUID.randomUUID()}',true,'User',now(),now())");return id}
    private data class Fixture(val owner:UUID,val member:UUID,val group:UUID)
    private fun execute(sql:String){dataSource.connection.use{c->c.createStatement().use{it.execute(sql)}}};private fun count(table:String)=int("SELECT count(*) FROM $table");private fun int(sql:String)=query(sql){it.getInt(1)};private fun string(sql:String)=query(sql){it.getString(1)};private fun date(sql:String)=query(sql){it.getObject(1,LocalDate::class.java)};private fun<T> query(sql:String,read:(java.sql.ResultSet)->T):T=dataSource.connection.use{c->c.createStatement().use{s->s.executeQuery(sql).use{r->check(r.next());read(r)}}}
    private companion object{val NOW:Instant=Instant.parse("2026-08-01T10:00:00Z");val TODAY:LocalDate=LocalDate.of(2026,8,12)}
}
