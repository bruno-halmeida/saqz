package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.finance.charge.ChargeManagementRepository
import br.com.saqz.groups.application.finance.charge.ChargeWithEvents
import br.com.saqz.groups.application.read.GroupFinanceDefaultsReadModel
import br.com.saqz.groups.application.read.GroupReadKey
import br.com.saqz.groups.application.read.GroupReadRepository
import br.com.saqz.groups.application.read.GroupReadSnapshot
import br.com.saqz.groups.domain.AccessName
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupAccessPolicy
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeChange
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.finance.charge.ChargeStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

// Isenção da mensalidade (DEB-115, MEM-003): quem pode, para quem, e o que acontece com a mensalidade do mês.
class UpdateAthleteExemptionTest {
    private val groupId = UUID.randomUUID()
    private val adminId = UUID.randomUUID()
    private val memberId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-20T15:00:00Z")

    @Test
    fun `dono isenta um mensalista e a pendente do mes atual vira dispensada com a nota`() {
        val fixture = fixture(GroupRole.OWNER)
        val current = fixture.charges.seed(YearMonth.of(2026, 10))

        val result = fixture.update.execute(adminId, command(exempt = true))

        assertTrue(assertIs<UpdateAthleteResult.Success>(result).athlete.monthlyFeeExempt)
        val waived = fixture.charges.rows.getValue(current.id)
        assertEquals(ChargeStatus.WAIVED, waived.charge.status)
        assertEquals(2, waived.charge.version)
        val event = waived.events.single()
        assertEquals(MONTHLY_FEE_EXEMPTION_NOTE, event.note)
        assertEquals(adminId, event.actorId)
        assertEquals(ChargeStatus.PENDING, event.oldStatus)
    }

    @Test
    fun `mes anterior pendente, cobranca de jogo e ordem eletronica ficam como estao`() {
        val fixture = fixture(GroupRole.OWNER)
        val previous = fixture.charges.seed(YearMonth.of(2026, 9))
        val electronic = fixture.charges.seed(YearMonth.of(2026, 10), electronicOrder = true)

        fixture.update.execute(adminId, command(exempt = true))

        assertEquals(ChargeStatus.PENDING, fixture.charges.rows.getValue(previous.id).charge.status)
        assertEquals(ChargeStatus.PENDING, fixture.charges.rows.getValue(electronic.id).charge.status)
        assertTrue(fixture.charges.updates.isEmpty())
    }

    @Test
    fun `ligar de novo quem ja e isento nao mexe nas cobrancas`() {
        val fixture = fixture(GroupRole.OWNER, exempt = true)
        fixture.charges.seed(YearMonth.of(2026, 10))

        assertIs<UpdateAthleteResult.Success>(fixture.update.execute(adminId, command(exempt = true)))

        assertTrue(fixture.charges.updates.isEmpty())
    }

    @Test
    fun `admin isenta a si mesmo`() {
        val fixture = fixture(GroupRole.ADMIN, target = adminId)

        val result = fixture.update.execute(adminId, command(exempt = true, target = adminId))

        assertTrue(assertIs<UpdateAthleteResult.Success>(result).athlete.monthlyFeeExempt)
    }

    @Test
    fun `atleta nao se isenta`() {
        val fixture = fixture(GroupRole.ATHLETE, target = adminId)

        val result = fixture.update.execute(adminId, command(exempt = true, target = adminId))

        assertSame(UpdateAthleteResult.AccessForbidden, result)
        assertTrue(fixture.athletes.writes.isEmpty())
    }

    @Test
    fun `campo ausente mantem a isencao (apps antigos)`() {
        val fixture = fixture(GroupRole.OWNER, exempt = true)

        fixture.update.execute(adminId, command(exempt = null))

        assertEquals(true, fixture.athletes.writes.single().monthlyFeeExempt)
    }

    @Test
    fun `tirar a isencao grava falso sem mexer em cobranca`() {
        val fixture = fixture(GroupRole.OWNER, exempt = true)
        fixture.charges.seed(YearMonth.of(2026, 10))

        fixture.update.execute(adminId, command(exempt = false))

        assertEquals(false, fixture.athletes.writes.single().monthlyFeeExempt)
        assertTrue(fixture.charges.updates.isEmpty())
    }

    @Test
    fun `virar avulso zera a isencao`() {
        val fixture = fixture(GroupRole.OWNER, exempt = true)

        fixture.update.execute(adminId, command(exempt = null, type = AthleteMembershipType.AVULSO))

        assertEquals(false, fixture.athletes.writes.single().monthlyFeeExempt)
    }

    @Test
    fun `avulso marcado como isento e recusado no campo`() {
        val fixture = fixture(GroupRole.OWNER)

        val result = fixture.update.execute(adminId, command(exempt = true, type = AthleteMembershipType.AVULSO))

        assertEquals(setOf("monthlyFeeExempt"), assertIs<UpdateAthleteResult.Invalid>(result).fieldErrors.keys)
        assertTrue(fixture.athletes.writes.isEmpty())
    }

    private fun command(
        exempt: Boolean?,
        type: AthleteMembershipType = AthleteMembershipType.MENSALISTA,
        target: UUID = memberId,
    ) = UpdateAthleteCommand(groupId, target, null, type, active = true, monthlyFeeExempt = exempt)

    private fun fixture(role: GroupRole, exempt: Boolean = false, target: UUID = memberId): Fixture {
        val athletes = FakeAthletes(
            AthleteMembership(
                target, AccessName.from("Pessoa"), role.takeIf { target == adminId } ?: GroupRole.ATHLETE,
                position = null, membershipType = AthleteMembershipType.MENSALISTA, active = true,
                monthlyFeeExempt = exempt,
            ),
        )
        val charges = FakeCharges(target)
        val read = object : GroupReadRepository {
            override fun find(key: GroupReadKey) = GroupReadSnapshot(
                groupId, AccessName.from("Grupo"), IanaTimeZone.from("America/Sao_Paulo"), role, 1,
                financeDefaults = GroupFinanceDefaultsReadModel(defaultGameFeeCents = null, monthlyFeeCents = 10000, monthlyDueDay = 10),
            )
        }
        val runner = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        return Fixture(
            UpdateAthlete(runner, read, athletes, GroupAccessPolicy(), charges, { now }, UUID::randomUUID),
            athletes,
            charges,
        )
    }

    private data class Fixture(val update: UpdateAthlete, val athletes: FakeAthletes, val charges: FakeCharges)

    private class FakeAthletes(private var row: AthleteMembership) : AthleteRepository {
        val writes = mutableListOf<UpdateAthleteCommand>()
        override fun find(groupId: UUID, userId: UUID) = row.takeIf { it.userId == userId }
        override fun update(command: UpdateAthleteCommand): AthleteMembership {
            writes += command
            row = row.copy(membershipType = command.membershipType, monthlyFeeExempt = command.monthlyFeeExempt ?: false)
            return row
        }
        override fun updateOwn(command: UpdateOwnAthleteProfileCommand) = error("não usado")
        override fun updatePosition(groupId: UUID, userId: UUID, position: br.com.saqz.groups.domain.AthletePosition?) = error("não usado")
        override fun remove(groupId: UUID, userId: UUID) = error("não usado")
    }

    private inner class FakeCharges(private val member: UUID) : ChargeManagementRepository {
        val rows = linkedMapOf<UUID, ChargeWithEvents>()
        val updates = mutableListOf<ChargeChange>()
        fun seed(month: YearMonth, electronicOrder: Boolean = false): Charge {
            val charge = Charge(
                UUID.randomUUID(), groupId, member, ChargeIdentity.Monthly(month), 10000,
                LocalDate.of(month.year, month.month, 10), electronicOrder = electronicOrder,
            )
            rows[charge.id] = ChargeWithEvents(charge, emptyList())
            return charge
        }
        override fun role(actorId: UUID, groupId: UUID) = GroupRole.OWNER
        override fun list(groupId: UUID, memberId: UUID?) = rows.values.filter { memberId == null || it.charge.memberId == memberId }
        override fun find(groupId: UUID, chargeId: UUID) = rows[chargeId]
        override fun update(change: ChargeChange, expectedVersion: Long): Boolean {
            updates += change
            val old = rows.getValue(change.charge.id)
            rows[change.charge.id] = ChargeWithEvents(change.charge, old.events + change.event)
            return true
        }
        override fun resolveReview(chargeId: UUID, groupId: UUID, actorId: UUID, expectedVersion: Long, at: Instant, resolutionId: UUID) = false
    }
}
