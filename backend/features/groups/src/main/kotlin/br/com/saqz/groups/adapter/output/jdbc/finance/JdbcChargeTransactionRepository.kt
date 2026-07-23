package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.finance.charge.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

class JdbcChargeTransactionRepository(dataSource:DataSource):ChargeTransactionRepository{
    private val jdbc=JdbcClient.create(dataSource)
    override fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge{
        val id=UUID.randomUUID();val inserted=jdbc.sql(INSERT_GAME).param("id",id).param("group",input.groupId).param("member",input.memberId).param("game",input.gameId).param("amount",requireNotNull(input.gameFeeCents)).param("due",input.dueDate).param("actor",actorId).param("now",Timestamp.from(now)).update()
        val charge=findGame(input.groupId,input.gameId,input.memberId)?:error("charge insert lost")
        if(inserted==1)event(charge,null,ChargeStatus.PENDING,actorId,now)
        return charge
    }
    override fun reconcileGameCancellation(groupId:UUID,gameId:UUID,actorId:UUID,now:Instant){
        val pending=jdbc.sql("SELECT id FROM group_charges WHERE group_id=:group AND game_id=:game AND status='PENDING' FOR UPDATE").param("group",groupId).param("game",gameId).query(UUID::class.java).list().filterNotNull()
        jdbc.sql("UPDATE group_charges SET status='CANCELLED',changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE group_id=:group AND game_id=:game AND status='PENDING'").param("actor",actorId).param("now",Timestamp.from(now)).param("group",groupId).param("game",gameId).update()
        pending.forEach{eventId->event(find(eventId)?:error("cancelled charge lost"),ChargeStatus.PENDING,ChargeStatus.CANCELLED,actorId,now)}
        jdbc.sql("UPDATE group_charges SET review_required=true,changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE group_id=:group AND game_id=:game AND status IN ('PAID','WAIVED') AND NOT review_required").param("actor",actorId).param("now",Timestamp.from(now)).param("group",groupId).param("game",gameId).update()
    }
    override fun members(groupId:UUID):GroupMembers?{
        val exists=jdbc.sql("SELECT count(*) FROM access_groups WHERE id=:group").param("group",groupId).query(Int::class.java).single()>0;if(!exists)return null
        val rows=jdbc.sql("SELECT user_id,active FROM group_memberships WHERE group_id=:group").param("group",groupId).query{rs,_->rs.getObject("user_id",UUID::class.java) to rs.getBoolean("active")}.list()
        return GroupMembers(rows.map{it.first}.toSet(),rows.filter{it.second}.map{it.first}.toSet())
    }
    override fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant):Charge{
        val id=UUID.randomUUID();val inserted=jdbc.sql(INSERT_MONTHLY).param("id",id).param("group",command.groupId).param("member",memberId).param("month",command.month.atDay(1)).param("amount",command.amountCents).param("due",command.dueDate).param("actor",command.actorId).param("now",Timestamp.from(now)).update()
        val charge=findMonthly(command.groupId,command.month,memberId)?:error("monthly insert lost")
        if(inserted==1)event(charge,null,ChargeStatus.PENDING,command.actorId,now)
        return charge
    }
    private fun event(charge:Charge,old:ChargeStatus?,new:ChargeStatus,actor:UUID,now:Instant){jdbc.sql("INSERT INTO group_charge_events (id,charge_id,group_id,actor_user_id,old_status,new_status,occurred_at) VALUES (:id,:charge,:group,:actor,:old,:new,:now)").param("id",UUID.randomUUID()).param("charge",charge.id).param("group",charge.groupId).param("actor",actor).param("old",old?.name,java.sql.Types.VARCHAR).param("new",new.name).param("now",Timestamp.from(now)).update()}
    private fun findGame(group:UUID,game:UUID,member:UUID)=jdbc.sql("$SELECT WHERE group_id=:group AND game_id=:game AND member_user_id=:member").param("group",group).param("game",game).param("member",member).query(::map).optional().orElse(null)
    private fun findMonthly(group:UUID,month:YearMonth,member:UUID)=jdbc.sql("$SELECT WHERE group_id=:group AND billing_month=:month AND member_user_id=:member").param("group",group).param("month",month.atDay(1)).param("member",member).query(::map).optional().orElse(null)
    private fun find(id:UUID)=jdbc.sql("$SELECT WHERE id=:id").param("id",id).query(::map).optional().orElse(null)
    private fun map(rs:ResultSet,@Suppress("UNUSED_PARAMETER") row:Int)=Charge(rs.getObject("id",UUID::class.java),rs.getObject("group_id",UUID::class.java),rs.getObject("member_user_id",UUID::class.java),rs.getObject("game_id",UUID::class.java)?.let(ChargeIdentity::Game)?:ChargeIdentity.Monthly(YearMonth.from(rs.getObject("billing_month",java.time.LocalDate::class.java))),rs.getLong("amount_cents"),rs.getObject("due_date",java.time.LocalDate::class.java),ChargeStatus.valueOf(rs.getString("status")),rs.getLong("version"),rs.getBoolean("review_required"))
    private companion object{
        const val SELECT="SELECT id,group_id,member_user_id,game_id,billing_month,amount_cents,due_date,status,version,review_required FROM group_charges"
        const val INSERT_GAME="""INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) VALUES (:id,:group,:member,'GAME',:game,:amount,:due,'PENDING',:actor,:actor,:now,:now,(SELECT display_name FROM access_users WHERE id=:member)) ON CONFLICT (group_id,game_id,member_user_id) WHERE kind='GAME' DO NOTHING"""
        const val INSERT_MONTHLY="""INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name) VALUES (:id,:group,:member,'MONTHLY',:month,:amount,:due,'PENDING',:actor,:actor,:now,:now,(SELECT display_name FROM access_users WHERE id=:member)) ON CONFLICT (group_id,billing_month,member_user_id) WHERE kind='MONTHLY' DO NOTHING"""
    }
}
