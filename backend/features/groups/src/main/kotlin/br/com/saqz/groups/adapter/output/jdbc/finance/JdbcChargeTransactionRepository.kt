package br.com.saqz.groups.adapter.output.jdbc.finance

import br.com.saqz.groups.application.finance.charge.*
import br.com.saqz.groups.domain.finance.charge.*
import org.springframework.jdbc.core.simple.JdbcClient
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.sql.DataSource

class JdbcChargeTransactionRepository(dataSource:DataSource, private val electronicCancellation: br.com.saqz.sharedkernel.group.GroupChargePaymentCancellation = br.com.saqz.sharedkernel.group.GroupChargePaymentCancellation { _, _, _ -> }, private val receivables: br.com.saqz.sharedkernel.group.GroupReceivables = br.com.saqz.sharedkernel.group.GroupReceivables.NONE):ChargeTransactionRepository,MonthlyDueMembershipRepository{
    private val jdbc=JdbcClient.create(dataSource)
    override fun createGameCharge(input:GameChargeInput,actorId:UUID,now:Instant):Charge{
        requireActiveGroup(input.groupId)
        val id=UUID.randomUUID();val amount=requireNotNull(input.gameFeeCents);val inserted=priced(jdbc.sql(INSERT_GAME),input.groupId,amount).param("id",id).param("group",input.groupId).param("member",input.memberId).param("game",input.gameId).param("amount",amount).param("due",input.dueDate).param("actor",actorId).param("now",Timestamp.from(now)).param("guest",input.guestSeq).update()
        val charge=findGame(input.groupId,input.gameId,input.memberId,input.guestSeq)?:error("charge insert lost")
        if(inserted==1)event(charge,null,ChargeStatus.PENDING,actorId,now)
        return charge
    }
    override fun reconcileGameCancellation(groupId:UUID,gameId:UUID,actorId:UUID,now:Instant){
        requireActiveGroup(groupId)
        val electronicOrders=jdbc.sql("SELECT electronic_order_id FROM group_charges WHERE group_id=:group AND game_id=:game AND status='PENDING' AND electronic_order_id IS NOT NULL FOR UPDATE").param("group",groupId).param("game",gameId).query(UUID::class.java).list().filterNotNull()
        electronicOrders.forEach { electronicCancellation.request(it, actorId, now) }
        val pending=jdbc.sql("SELECT charges.id FROM group_charges charges JOIN access_groups groups ON groups.id=charges.group_id AND groups.deleted_at IS NULL WHERE charges.group_id=:group AND charges.game_id=:game AND charges.status='PENDING' AND charges.electronic_order_id IS NULL FOR UPDATE OF charges").param("group",groupId).param("game",gameId).query(UUID::class.java).list().filterNotNull()
        jdbc.sql("UPDATE group_charges SET status='CANCELLED',changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE group_id=:group AND game_id=:game AND status='PENDING' AND electronic_order_id IS NULL AND EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL)").param("actor",actorId).param("now",Timestamp.from(now)).param("group",groupId).param("game",gameId).update()
        pending.forEach{eventId->event(find(eventId)?:error("cancelled charge lost"),ChargeStatus.PENDING,ChargeStatus.CANCELLED,actorId,now)}
        jdbc.sql("UPDATE group_charges SET review_required=true,changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE group_id=:group AND game_id=:game AND (status IN ('PAID','WAIVED') OR electronic_order_id IS NOT NULL) AND NOT review_required AND EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL)").param("actor",actorId).param("now",Timestamp.from(now)).param("group",groupId).param("game",gameId).update()
        jdbc.sql("UPDATE games SET finance_review_required=true WHERE group_id=:group AND id=:game AND EXISTS (SELECT 1 FROM group_charges WHERE group_id=:group AND game_id=:game AND (status IN ('PAID','WAIVED') OR electronic_order_id IS NOT NULL))").param("group",groupId).param("game",gameId).update()
    }
    override fun cancelGuestCharge(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int,actorId:UUID,now:Instant){
        requireActiveGroup(groupId)
        val id=jdbc.sql("SELECT id FROM group_charges WHERE group_id=:group AND game_id=:game AND member_user_id=:member AND guest_seq=:guest AND kind='GAME' AND status='PENDING' AND electronic_order_id IS NULL FOR UPDATE").param("group",groupId).param("game",gameId).param("member",memberId).param("guest",guestSeq).query(UUID::class.java).optional().orElse(null)?:return
        jdbc.sql("UPDATE group_charges SET status='CANCELLED',changed_by_user_id=:actor,version=version+1,updated_at=:now WHERE id=:id").param("actor",actorId).param("now",Timestamp.from(now)).param("id",id).update()
        event(find(id)!!,ChargeStatus.PENDING,ChargeStatus.CANCELLED,actorId,now)
    }
    override fun settleGameCharge(charge:Charge,method:PaidMethod,actorId:UUID,at:Instant):Charge{
        val updated=jdbc.sql("UPDATE group_charges SET status='PAID',paid_method=:method,changed_by_user_id=:actor,version=version+1,updated_at=now() WHERE id=:id AND status='PENDING' AND electronic_order_id IS NULL AND settled_by IS NULL").param("method",method.name,Types.OTHER).param("actor",actorId).param("id",charge.id).update()
        // O evento PAID leva a data do recebimento: é por ela que a diária entra no caixa e no extrato.
        if(updated==1)event(find(charge.id)!!,ChargeStatus.PENDING,ChargeStatus.PAID,actorId,at)
        return find(charge.id)?:charge
    }
    override fun findGameCharge(groupId:UUID,gameId:UUID,memberId:UUID,guestSeq:Int):Charge?=findGame(groupId,gameId,memberId,guestSeq)
    override fun billsGameCharge(groupId:UUID,memberId:UUID,guestSeq:Int):Boolean{
        if(guestSeq>0)return true
        return jdbc.sql("SELECT membership_type FROM group_memberships WHERE group_id=:group AND user_id=:member").param("group",groupId).param("member",memberId).query(String::class.java).optional().orElse(null)!="MENSALISTA"
    }
    override fun members(groupId:UUID):GroupMembers?{
        val exists=jdbc.sql("SELECT count(*) FROM access_groups WHERE id=:group AND deleted_at IS NULL").param("group",groupId).query(Int::class.java).single()>0;if(!exists)return null
        val rows=jdbc.sql("SELECT memberships.user_id,memberships.active,memberships.monthly_fee_exempt FROM group_memberships memberships JOIN access_groups groups ON groups.id=memberships.group_id AND groups.deleted_at IS NULL WHERE memberships.group_id=:group").param("group",groupId).query{rs,_->Triple(rs.getObject("user_id",UUID::class.java),rs.getBoolean("active"),rs.getBoolean("monthly_fee_exempt"))}.list()
        return GroupMembers(rows.map{it.first}.toSet(),rows.filter{it.second}.map{it.first}.toSet(),rows.filter{it.third}.map{it.first}.toSet())
    }
    override fun createMonthlyCharge(command:MonthlyGenerationCommand,memberId:UUID,now:Instant):Charge{
        requireActiveGroup(command.groupId)
        val id=UUID.randomUUID();val inserted=priced(jdbc.sql(INSERT_MONTHLY),command.groupId,command.amountCents).param("id",id).param("group",command.groupId).param("member",memberId).param("month",command.month.atDay(1)).param("amount",command.amountCents).param("due",command.dueDate).param("actor",command.actorId).param("now",Timestamp.from(now)).update()
        val charge=findMonthly(command.groupId,command.month,memberId)?:error("monthly insert lost")
        if(inserted==1)event(charge,null,ChargeStatus.PENDING,command.actorId,now)
        return charge
    }
    // Elegivel e o MENSALISTA com mensalidade efetiva. Efetiva = override do membro senao o default
    // do grupo, coluna a coluna, igual ao effectiveMonthlyFeeCents/effectiveMonthlyDueDay do editor
    // de membro. O AVULSO fica de fora mesmo com fee preenchida: o editor preserva os overrides
    // quando o organizador troca o tipo, entao a fee sozinha nao diz que ele e mensalista.
    // Membro inativo entra e e descartado por ChargeTransactions.generate, dono da regra de inativo.
    // O <= pega o mes todo ate o dia pedido, nao so o dia: dia sem execucao nao deixa buraco.
    override fun dueUpTo(dayOfMonth:Int):List<MonthlyDueMembership> =jdbc.sql("SELECT memberships.group_id,groups.owner_user_id,memberships.user_id,coalesce(memberships.monthly_fee_cents,groups.monthly_fee_cents) AS fee_cents,coalesce(memberships.monthly_due_day,groups.monthly_due_day) AS due_day,groups.monthly_late_join_policy,memberships.mensalista_since FROM group_memberships memberships JOIN access_groups groups ON groups.id=memberships.group_id AND groups.deleted_at IS NULL WHERE memberships.membership_type='MENSALISTA' AND NOT memberships.monthly_fee_exempt AND coalesce(memberships.monthly_due_day,groups.monthly_due_day)<=:day AND coalesce(memberships.monthly_fee_cents,groups.monthly_fee_cents) IS NOT NULL ORDER BY memberships.group_id,memberships.user_id").param("day",dayOfMonth).query{rs,_->MonthlyDueMembership(rs.getObject("group_id",UUID::class.java),rs.getObject("owner_user_id",UUID::class.java),rs.getObject("user_id",UUID::class.java),rs.getLong("fee_cents"),rs.getInt("due_day"),MonthlyLateJoinPolicy.valueOf(rs.getString("monthly_late_join_policy")),rs.getObject("mensalista_since",LocalDate::class.java))}.list()
    // FIN-010: com recebimentos ativos o valor do atleta nasce com a cobrança e não muda depois (nem com a tarifa nem com a política).
    private fun priced(statement:JdbcClient.StatementSpec,groupId:UUID,amount:Long):JdbcClient.StatementSpec{val price=receivables.price(groupId,amount);return statement.param("payable",price?.payableCents,Types.BIGINT).param("feePolicy",price?.feePolicy,Types.VARCHAR).param("feeSchedule",price?.feeScheduleId,Types.OTHER)}
    private fun event(charge:Charge,old:ChargeStatus?,new:ChargeStatus,actor:UUID,now:Instant){val inserted=jdbc.sql("INSERT INTO group_charge_events (id,charge_id,group_id,actor_user_id,old_status,new_status,occurred_at) SELECT :id,:charge,:group,:actor,:old,:new,:now WHERE EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL)").param("id",UUID.randomUUID()).param("charge",charge.id).param("group",charge.groupId).param("actor",actor).param("old",old?.name,Types.OTHER).param("new",new.name,Types.OTHER).param("now",Timestamp.from(now)).update();check(inserted==1){"Grupo de cobrança excluído ou inexistente"}}
    private fun findGame(group:UUID,game:UUID,member:UUID,guest:Int)=jdbc.sql("$SELECT WHERE c.group_id=:group AND c.game_id=:game AND c.member_user_id=:member AND c.guest_seq=:guest").param("group",group).param("game",game).param("member",member).param("guest",guest).query(::map).optional().orElse(null)
    private fun findMonthly(group:UUID,month:YearMonth,member:UUID)=jdbc.sql("$SELECT WHERE c.group_id=:group AND c.billing_month=:month AND c.member_user_id=:member").param("group",group).param("month",month.atDay(1)).param("member",member).query(::map).optional().orElse(null)
    private fun find(id:UUID)=jdbc.sql("$SELECT WHERE c.id=:id").param("id",id).query(::map).optional().orElse(null)
    private fun requireActiveGroup(groupId:UUID){val active=jdbc.sql("SELECT id FROM access_groups WHERE id=:group AND deleted_at IS NULL FOR UPDATE").param("group",groupId).query(UUID::class.java).optional().orElse(null);check(active!=null){"Grupo excluído ou inexistente"}}
    private fun map(rs:ResultSet,@Suppress("UNUSED_PARAMETER") row:Int)=Charge(rs.getObject("id",UUID::class.java),rs.getObject("group_id",UUID::class.java),rs.getObject("member_user_id",UUID::class.java),rs.getObject("game_id",UUID::class.java)?.let(ChargeIdentity::Game)?:ChargeIdentity.Monthly(YearMonth.from(rs.getObject("billing_month",java.time.LocalDate::class.java))),rs.getLong("amount_cents"),rs.getObject("due_date",java.time.LocalDate::class.java),ChargeStatus.valueOf(rs.getString("status")),rs.getLong("version"),rs.getBoolean("review_required"),rs.getString("paid_method")?.let(PaidMethod::valueOf))
    private companion object{
        const val SELECT="SELECT c.id,c.group_id,c.member_user_id,c.game_id,c.billing_month,c.amount_cents,c.due_date,c.status,c.paid_method,c.version,c.review_required FROM group_charges c JOIN access_groups g ON g.id=c.group_id AND g.deleted_at IS NULL"
        const val INSERT_GAME="""INSERT INTO group_charges (id,group_id,member_user_id,kind,game_id,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name,guest_seq,guest_display_name,payable_cents,fee_policy,fee_schedule_id) SELECT :id,:group,:member,'GAME',:game,:amount,:due,'PENDING',:actor,:actor,:now,:now,(SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member),:guest,(SELECT ga.member_display_name FROM game_attendance ga WHERE ga.game_id=:game AND ga.member_user_id=:member AND ga.guest_seq=:guest AND :guest>0),:payable,:feePolicy,:feeSchedule WHERE EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL) ON CONFLICT (group_id,game_id,member_user_id,guest_seq) WHERE kind='GAME' DO NOTHING"""
        const val INSERT_MONTHLY="""INSERT INTO group_charges (id,group_id,member_user_id,kind,billing_month,amount_cents,due_date,status,created_by_user_id,changed_by_user_id,created_at,updated_at,member_display_name,payable_cents,fee_policy,fee_schedule_id) SELECT :id,:group,:member,'MONTHLY',:month,:amount,:due,'PENDING',:actor,:actor,:now,:now,(SELECT coalesce(nickname, display_name) FROM access_users WHERE id=:member),:payable,:feePolicy,:feeSchedule WHERE EXISTS (SELECT 1 FROM access_groups g WHERE g.id=:group AND g.deleted_at IS NULL) ON CONFLICT (group_id,billing_month,member_user_id) WHERE kind='MONTHLY' DO NOTHING"""
    }
}
