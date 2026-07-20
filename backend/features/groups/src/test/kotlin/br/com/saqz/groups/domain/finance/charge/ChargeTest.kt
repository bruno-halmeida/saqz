package br.com.saqz.groups.domain.finance.charge

import br.com.saqz.groups.domain.GroupRole
import java.time.*
import java.util.UUID
import kotlin.test.*

class ChargeTest {
    @Test fun `game identity retains immutable game id`(){val id=UUID.randomUUID();assertEquals(id,(charge(identity=ChargeIdentity.Game(id)).identity as ChargeIdentity.Game).gameId)}
    @Test fun `monthly identity retains immutable year month`(){assertEquals(YearMonth.of(2026,8),(charge(identity=ChargeIdentity.Monthly(YearMonth.of(2026,8))).identity as ChargeIdentity.Monthly).month)}
    @Test fun `amount must be positive`(){assertFailsWith<IllegalArgumentException>{charge(amount=0)}}
    @Test fun `amount has upper bound`(){assertFailsWith<IllegalArgumentException>{charge(amount=100_000_000)}}
    @Test fun `version must be positive`(){assertFailsWith<IllegalArgumentException>{charge(version=0)}}
    @Test fun `pending can become paid with server audit`(){val c=charge();val actor=UUID.randomUUID();val time=Instant.parse("2026-08-01T10:00:00Z");val event=UUID.randomUUID();val result=ChargeTransitions.apply(c,ChargeStatusCommand(ChargeStatus.PAID,"Recebido"),actor,time,event);assertEquals(ChargeStatus.PAID,result.charge.status);assertEquals(2,result.charge.version);assertEquals(actor,result.event.actorId);assertEquals(time,result.event.occurredAt);assertEquals(event,result.event.id)}
    @Test fun `pending can become waived`(){assertEquals(ChargeStatus.WAIVED,change(ChargeStatus.WAIVED).charge.status)}
    @Test fun `pending can become cancelled`(){assertEquals(ChargeStatus.CANCELLED,change(ChargeStatus.CANCELLED).charge.status)}
    @Test fun `pending cannot transition to itself`(){assertFailsWith<InvalidChargeTransition>{change(ChargeStatus.PENDING)}}
    @Test fun `paid is terminal`(){assertFailsWith<InvalidChargeTransition>{ChargeTransitions.apply(charge(status=ChargeStatus.PAID),ChargeStatusCommand(ChargeStatus.WAIVED),UUID.randomUUID(),Instant.now(),UUID.randomUUID())}}
    @Test fun `waived is terminal`(){assertFailsWith<InvalidChargeTransition>{ChargeTransitions.apply(charge(status=ChargeStatus.WAIVED),ChargeStatusCommand(ChargeStatus.CANCELLED),UUID.randomUUID(),Instant.now(),UUID.randomUUID())}}
    @Test fun `cancelled is terminal`(){assertFailsWith<InvalidChargeTransition>{ChargeTransitions.apply(charge(status=ChargeStatus.CANCELLED),ChargeStatusCommand(ChargeStatus.PAID),UUID.randomUUID(),Instant.now(),UUID.randomUUID())}}
    @Test fun `audit retains exact old and new status`(){val event=change(ChargeStatus.PAID).event;assertEquals(ChargeStatus.PENDING,event.oldStatus);assertEquals(ChargeStatus.PAID,event.newStatus)}
    @Test fun `note is trimmed but prior charge amount is unchanged`(){val original=charge();val result=ChargeTransitions.apply(original,ChargeStatusCommand(ChargeStatus.PAID,"  Recebido  "),UUID.randomUUID(),Instant.now(),UUID.randomUUID());assertEquals("Recebido",result.event.note);assertEquals(original.amountCents,result.charge.amountCents);assertEquals(original.identity,result.charge.identity)}
    @Test fun `blank or control note is rejected`(){assertFailsWith<InvalidChargeNote>{ChargeTransitions.apply(charge(),ChargeStatusCommand(ChargeStatus.PAID," "),UUID.randomUUID(),Instant.now(),UUID.randomUUID())};assertFailsWith<InvalidChargeNote>{ChargeTransitions.apply(charge(),ChargeStatusCommand(ChargeStatus.PAID,"bad\nnote"),UUID.randomUUID(),Instant.now(),UUID.randomUUID())}}
    @Test fun `owner and admin see group charges and totals`(){listOf(GroupRole.OWNER,GroupRole.ADMIN).forEach{role->val view=ChargeVisibility.project(UUID.randomUUID(),role,listOf(charge(amount=100),charge(amount=200,status=ChargeStatus.PAID)));assertEquals(2,view.charges.size);assertEquals(100,view.totals?.pendingCents);assertEquals(200,view.totals?.paidCents)}}
    @Test fun `athlete sees only own charges without totals`(){val actor=UUID.randomUUID();val view=ChargeVisibility.project(actor,GroupRole.ATHLETE,listOf(charge(member=actor),charge(member=UUID.randomUUID())));assertEquals(listOf(actor),view.charges.map{it.memberId});assertNull(view.totals)}
    @Test fun `non member is privacy hidden`(){assertFailsWith<ChargeResourceHidden>{ChargeVisibility.project(UUID.randomUUID(),null,listOf(charge()))}}

    private fun change(target:ChargeStatus)=ChargeTransitions.apply(charge(),ChargeStatusCommand(target),UUID.randomUUID(),Instant.parse("2026-08-01T10:00:00Z"),UUID.randomUUID())
    private fun charge(identity:ChargeIdentity=ChargeIdentity.Game(UUID.randomUUID()),amount:Long=2500,version:Long=1,status:ChargeStatus=ChargeStatus.PENDING,member:UUID=UUID.randomUUID())=Charge(UUID.randomUUID(),UUID.randomUUID(),member,identity,amount,LocalDate.of(2026,8,10),status,version)
}
