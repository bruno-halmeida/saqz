package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.finance.charge.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

class JdbcChargeManagementRepository(dataSource:DataSource):ChargeManagementRepository{
    private val jdbc=JdbcClient.create(dataSource)
    override fun role(actorId:UUID,groupId:UUID):GroupRole?=jdbc.sql(ROLE).param("actor",actorId).param("group",groupId).query(String::class.java).optional().map(GroupRole::valueOf).orElse(null)
    override fun list(groupId:UUID,memberId:UUID?):List<ChargeWithEvents>{val charges=jdbc.sql("$SELECT_CHARGE WHERE c.group_id=:group ${if(memberId==null)"" else "AND c.member_user_id=:member"} ORDER BY c.due_date,c.id").param("group",groupId).let{if(memberId==null)it else it.param("member",memberId)}.query(::mapCharge).list();return charges.map{ChargeWithEvents(it,events(it.id))}}
    override fun find(groupId:UUID,chargeId:UUID)=jdbc.sql("$SELECT_CHARGE WHERE c.group_id=:group AND c.id=:id").param("group",groupId).param("id",chargeId).query(::mapCharge).optional().orElse(null)?.let{ChargeWithEvents(it,events(it.id))}
    override fun update(change:ChargeChange,expectedVersion:Long):Boolean{val c=change.charge;val updated=jdbc.sql("UPDATE group_charges SET status=:status,changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE id=:id AND group_id=:group AND version=:version").param("status",c.status.name).param("actor",change.event.actorId).param("now",Timestamp.from(change.event.occurredAt)).param("id",c.id).param("group",c.groupId).param("version",expectedVersion).update();if(updated!=1)return false;val e=change.event;jdbc.sql("INSERT INTO group_charge_events (id,charge_id,group_id,actor_user_id,old_status,new_status,note,occurred_at) VALUES (:id,:charge,:group,:actor,:old,:new,:note,:now)").param("id",e.id).param("charge",e.chargeId).param("group",c.groupId).param("actor",e.actorId).param("old",e.oldStatus.name).param("new",e.newStatus.name).param("note",e.note,java.sql.Types.VARCHAR).param("now",Timestamp.from(e.occurredAt)).update();return true}
    private fun events(charge:UUID)=jdbc.sql("SELECT id,charge_id,actor_user_id,old_status,new_status,note,occurred_at FROM group_charge_events WHERE charge_id=:charge ORDER BY occurred_at,id").param("charge",charge).query{rs,_->ChargeEvent(rs.getObject("id",UUID::class.java),rs.getObject("charge_id",UUID::class.java),rs.getObject("actor_user_id",UUID::class.java),ChargeStatus.valueOf(rs.getString("old_status")?:"PENDING"),ChargeStatus.valueOf(rs.getString("new_status")),rs.getString("note"),rs.getTimestamp("occurred_at").toInstant())}.list()
    private fun mapCharge(rs:ResultSet,@Suppress("UNUSED_PARAMETER") row:Int)=Charge(rs.getObject("id",UUID::class.java),rs.getObject("group_id",UUID::class.java),rs.getObject("member_user_id",UUID::class.java),rs.getObject("game_id",UUID::class.java)?.let(ChargeIdentity::Game)?:ChargeIdentity.Monthly(YearMonth.from(rs.getObject("billing_month",java.time.LocalDate::class.java))),rs.getLong("amount_cents"),rs.getObject("due_date",java.time.LocalDate::class.java),ChargeStatus.valueOf(rs.getString("status")),rs.getLong("version"),rs.getBoolean("review_required"))
    private companion object{const val ROLE="""SELECT CASE WHEN g.owner_user_id=:actor THEN 'OWNER' ELSE m.role END FROM access_groups g LEFT JOIN group_memberships m ON m.group_id=g.id AND m.user_id=:actor WHERE g.id=:group AND (g.owner_user_id=:actor OR m.user_id IS NOT NULL)""";const val SELECT_CHARGE="SELECT c.id,c.group_id,c.member_user_id,c.game_id,c.billing_month,c.amount_cents,c.due_date,c.status,c.version,c.review_required FROM group_charges c"}
}
