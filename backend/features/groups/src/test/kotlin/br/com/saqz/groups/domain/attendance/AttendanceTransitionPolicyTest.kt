package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class AttendanceTransitionPolicyTest {
    @Test fun `no response confirms below capacity`() = transition(context(), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `no response waitlists at capacity`() = transition(context(confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `no response waitlists over capacity`() = transition(context(confirmed = 3), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `no response may decline`() = transition(context(), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `confirmed member may withdraw to declined`() = transition(context(current = AttendanceStatus.CONFIRMED, confirmed = 1), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `declined member may confirm when a spot exists`() = transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `waitlisted member may withdraw to declined`() = transition(context(current = AttendanceStatus.WAITLISTED, confirmed = 2), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `confirmed retry is an unchanged confirmation`() = transition(context(current = AttendanceStatus.CONFIRMED, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
    @Test fun `declined retry is an unchanged decline`() = transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED)
    @Test fun `waitlisted retry keeps stable queue identity`() = transition(context(current = AttendanceStatus.WAITLISTED, confirmed = 0), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED)
    @Test fun `declined rejoin at capacity allocates a new sequence`() = transition(context(current = AttendanceStatus.DECLINED, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `capacity decrease never demotes an existing confirmation`() = transition(context(current = AttendanceStatus.CONFIRMED, capacity = 1, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
    @Test fun `capacity decrease blocks a declined member from confirmation`() = transition(context(current = AttendanceStatus.DECLINED, capacity = 1, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `draft rejects athlete self response`() = denied(context(status = GameStatus.DRAFT), AttendanceIntent.CONFIRM, AttendanceDenial.NOT_PUBLISHED)
    @Test fun `draft rejects organizer override`() = denied(organizer(status = GameStatus.DRAFT), AttendanceIntent.CONFIRM, AttendanceDenial.NOT_PUBLISHED)
    @Test fun `cancelled game freezes athlete response`() = denied(context(status = GameStatus.CANCELLED), AttendanceIntent.DECLINE, AttendanceDenial.FROZEN)
    @Test fun `cancelled game freezes organizer override`() = denied(organizer(status = GameStatus.CANCELLED), AttendanceIntent.DECLINE, AttendanceDenial.FROZEN)
    @Test fun `completed game freezes athlete response`() = denied(context(status = GameStatus.COMPLETED), AttendanceIntent.CONFIRM, AttendanceDenial.FROZEN)
    @Test fun `completed game freezes organizer override`() = denied(organizer(status = GameStatus.COMPLETED), AttendanceIntent.CONFIRM, AttendanceDenial.FROZEN)
    @Test fun `athlete may respond before deadline`() = transition(context(now = DEADLINE.minusSeconds(1)), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `athlete may respond exactly at deadline`() = transition(context(now = DEADLINE), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `athlete after deadline is accepted into the waitlist`() = transition(context(now = DEADLINE.plusNanos(1)), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `athlete cannot respond once the game started`() {
        denied(context(now = STARTS), AttendanceIntent.CONFIRM, AttendanceDenial.FROZEN)
        denied(context(now = STARTS.plusSeconds(60), current = AttendanceStatus.CONFIRMED), AttendanceIntent.DECLINE, AttendanceDenial.FROZEN)
    }
    @Test fun `promotion from the waitlist is denied once the game started for any source`() {
        val waiting = AttendanceStatus.WAITLISTED
        denied(context(now = STARTS, current = waiting), AttendanceIntent.PROMOTE, AttendanceDenial.FROZEN)
        denied(context(now = STARTS.plusSeconds(60), current = waiting).copy(source = AttendanceSource.SYSTEM), AttendanceIntent.PROMOTE, AttendanceDenial.FROZEN)
        denied(organizer(now = STARTS.plusSeconds(60), current = waiting), AttendanceIntent.PROMOTE, AttendanceDenial.FROZEN)
    }
    @Test fun `promotion from the waitlist still works one instant before the start`() =
        transition(organizer(now = STARTS.minusNanos(1), current = AttendanceStatus.WAITLISTED), AttendanceIntent.PROMOTE, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Ajuste do organizador")
    @Test fun `organizer may still override once the game started`() = transition(organizer(now = STARTS.plusSeconds(1), reason = "Chegou no jogo"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Chegou no jogo")
    @Test fun `organizer may override after deadline`() = transition(organizer(now = DEADLINE.plusSeconds(1), reason = "Presença confirmada"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Presença confirmada")
    @Test fun `organizer override requires reason`() = denied(organizer(reason = null), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_REQUIRED)
    @Test fun `organizer override rejects blank reason`() = denied(organizer(reason = "  "), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_REQUIRED)
    @Test fun `organizer override rejects one character reason`() = denied(organizer(reason = "X"), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer override rejects reason over limit`() = denied(organizer(reason = "x".repeat(501)), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer override rejects control character`() = denied(organizer(reason = "fora\ndo prazo"), AttendanceIntent.CONFIRM, AttendanceDenial.REASON_INVALID)
    @Test fun `organizer reason is trimmed into audit transition`() = transition(organizer(reason = "  Ajuste manual  "), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true, reason = "Ajuste manual")
    @Test fun `self transition never accepts audit reason`() = transition(context(reason = "client authored"), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true, reason = null)
    @Test fun `organizer confirmation still respects full capacity`() = transition(organizer(confirmed = 2, reason = "Lista de espera"), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true, reason = "Lista de espera")
    @Test fun `mensalista confirms into the free spot`() = transition(context(membership = AthleteMembershipType.MENSALISTA), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `mensalista without a free spot waits`() = transition(context(membership = AthleteMembershipType.MENSALISTA, confirmed = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `avulso waits even with a free spot`() = transition(context(membership = AthleteMembershipType.AVULSO), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `avulso rejoin after decline returns to the queue`() = transition(context(membership = AthleteMembershipType.AVULSO, current = AttendanceStatus.DECLINED), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `avulso confirmation retry never demotes a promoted member`() = transition(context(membership = AthleteMembershipType.AVULSO, current = AttendanceStatus.CONFIRMED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
    @Test fun `avulso may decline like any member`() = transition(context(membership = AthleteMembershipType.AVULSO, current = AttendanceStatus.WAITLISTED), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
    @Test fun `organizer confirms an avulso within capacity`() = transition(organizer(membership = AthleteMembershipType.AVULSO, reason = "Autorizado"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Autorizado")
    @Test fun `organizer confirmation of an avulso still respects full capacity`() = transition(organizer(membership = AthleteMembershipType.AVULSO, confirmed = 2, reason = "Lista de espera"), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true, reason = "Lista de espera")
    @Test
    fun `promotion confirms a waitlisted member and requests a charge`() = transition(
        organizer(current = AttendanceStatus.WAITLISTED, confirmed = 1, reason = "Promovido manualmente"),
        AttendanceIntent.PROMOTE,
        AttendanceStatus.CONFIRMED,
        changed = true,
        charge = true,
        reason = "Promovido manualmente",
    )

    @Test
    fun `promotion rejects a member outside the waitlist`() = denied(
        organizer(current = AttendanceStatus.DECLINED),
        AttendanceIntent.PROMOTE,
        AttendanceDenial.NOT_WAITLISTED,
    )

    @Test
    fun `promotion rejects a full game`() = denied(
        organizer(current = AttendanceStatus.WAITLISTED, confirmed = 2),
        AttendanceIntent.PROMOTE,
        AttendanceDenial.NO_CAPACITY,
    )

    // --- VUL-152: matriz de prioridade configurável ---
    // Prioridade ligada (default): mensalista toma vaga direto; avulso sempre espera.
    @Test fun `prioridade on - mensalista com vaga confirma`() = transition(context(membership = AthleteMembershipType.MENSALISTA, confirmed = 0, capacity = 2), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `prioridade on - mensalista sem vaga espera`() = transition(context(membership = AthleteMembershipType.MENSALISTA, confirmed = 2, capacity = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `prioridade on - avulso com vaga ainda espera`() = transition(context(membership = AthleteMembershipType.AVULSO, confirmed = 0, capacity = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `prioridade on - avulso sem vaga espera`() = transition(context(membership = AthleteMembershipType.AVULSO, confirmed = 2, capacity = 2), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)

    // Prioridade desligada: qualquer membro toma vaga direto se houver.
    @Test fun `prioridade off - mensalista com vaga confirma`() = transition(context(membership = AthleteMembershipType.MENSALISTA, confirmed = 0, capacity = 2, mensalistaPriority = false), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `prioridade off - mensalista sem vaga espera`() = transition(context(membership = AthleteMembershipType.MENSALISTA, confirmed = 2, capacity = 2, mensalistaPriority = false), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun `prioridade off - avulso com vaga confirma`() = transition(context(membership = AthleteMembershipType.AVULSO, confirmed = 0, capacity = 2, mensalistaPriority = false), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true)
    @Test fun `prioridade off - avulso sem vaga espera`() = transition(context(membership = AthleteMembershipType.AVULSO, confirmed = 2, capacity = 2, mensalistaPriority = false), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)

    // Organizador ignora a prioridade — decide a vaga diretamente.
    @Test fun `prioridade on - organizador confirma avulso com vaga`() = transition(organizer(membership = AthleteMembershipType.AVULSO, confirmed = 0, capacity = 2, reason = "Autorizado"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true, reason = "Autorizado")
    @Test fun `prioridade off - organizador confirma mensalista sem vaga espera`() = transition(organizer(membership = AthleteMembershipType.MENSALISTA, confirmed = 2, capacity = 2, mensalistaPriority = false, reason = "Sem vaga"), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true, reason = "Sem vaga")

    // Organizer não enxerga a faixa do prazo.
    @Test fun `prioridade on - mensalista promovido mantem confirmado apos prazo via organizer`() = transition(organizer(now = DEADLINE.plusSeconds(1), current = AttendanceStatus.CONFIRMED, confirmed = 1, capacity = 2, reason = "Pós-prazo"), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, reason = "Pós-prazo")

    @Test fun `only a newly confirmed transition requests a charge`() { transition(context(current = AttendanceStatus.DECLINED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, changed = true, charge = true); transition(context(current = AttendanceStatus.CONFIRMED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED, charge = false) }

    // --- VUL-239: convidado de jogo ---
    @Test fun guestAlwaysJoinsTheWaitlistEvenWithRoom() = transition(context(capacity = 12, confirmed = 3, guest = true), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED, changed = true, allocate = true)
    @Test fun guestPromotionCreatesTheCharge() = transition(context(current = AttendanceStatus.WAITLISTED, guest = true), AttendanceIntent.PROMOTE, AttendanceStatus.CONFIRMED, changed = true, charge = true)


    // --- Regra de 13/10/2026: a configuração do grupo decide, antes do prazo ---
    private val mensalista = AthleteMembershipType.MENSALISTA
    private val avulso = AthleteMembershipType.AVULSO

    private fun vou(mode: PromotionMode, priority: Boolean, membership: AthleteMembershipType, confirmed: Int = 0, now: Instant = DEADLINE.minusSeconds(1), current: AttendanceStatus? = null, guest: Boolean = false) =
        context(membership = membership, confirmed = confirmed, mensalistaPriority = priority, promotionMode = mode, now = now, current = current, guest = guest)

    private fun goesTo(status: AttendanceStatus, ctx: AttendanceDecisionContext) =
        if (status == AttendanceStatus.CONFIRMED) transition(ctx, AttendanceIntent.CONFIRM, status, changed = true, charge = true)
        else transition(ctx, AttendanceIntent.CONFIRM, status, changed = true, allocate = true)

    @Test fun `FIFO com prioridade - mensalista com vaga confirma`() = goesTo(AttendanceStatus.CONFIRMED, vou(PromotionMode.FIFO, true, mensalista))
    @Test fun `FIFO com prioridade - mensalista sem vaga espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.FIFO, true, mensalista, confirmed = 2))
    @Test fun `FIFO com prioridade - avulso com vaga espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.FIFO, true, avulso))
    @Test fun `FIFO sem prioridade - mensalista com vaga confirma`() = goesTo(AttendanceStatus.CONFIRMED, vou(PromotionMode.FIFO, false, mensalista))
    @Test fun `FIFO sem prioridade - mensalista sem vaga espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.FIFO, false, mensalista, confirmed = 2))
    @Test fun `FIFO sem prioridade - avulso com vaga confirma`() = goesTo(AttendanceStatus.CONFIRMED, vou(PromotionMode.FIFO, false, avulso))
    @Test fun `FIFO sem prioridade - avulso sem vaga espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.FIFO, false, avulso, confirmed = 2))
    @Test fun `MANUAL com prioridade - mensalista com vaga confirma`() = goesTo(AttendanceStatus.CONFIRMED, vou(PromotionMode.MANUAL, true, mensalista))
    @Test fun `MANUAL com prioridade - mensalista sem vaga espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.MANUAL, true, mensalista, confirmed = 2))
    @Test fun `MANUAL com prioridade - avulso espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.MANUAL, true, avulso))
    @Test fun `MANUAL sem prioridade - mensalista espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.MANUAL, false, mensalista))
    @Test fun `MANUAL sem prioridade - avulso espera`() = goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.MANUAL, false, avulso))

    @Test fun `depois do prazo todo Vou vai para a espera em qualquer configuracao`() {
        for (mode in PromotionMode.entries) for (priority in listOf(true, false)) for (membership in listOf(mensalista, avulso)) {
            goesTo(AttendanceStatus.WAITLISTED, vou(mode, priority, membership, now = DEADLINE.plusSeconds(1)))
            goesTo(AttendanceStatus.WAITLISTED, vou(mode, priority, membership, now = DEADLINE.plusSeconds(1), current = AttendanceStatus.DECLINED))
        }
    }
    @Test fun `depois do prazo Vou de quem ja esta confirmado ou na espera nao muda`() {
        transition(vou(PromotionMode.FIFO, true, mensalista, now = DEADLINE.plusSeconds(1), current = AttendanceStatus.CONFIRMED), AttendanceIntent.CONFIRM, AttendanceStatus.CONFIRMED)
        transition(vou(PromotionMode.MANUAL, true, avulso, now = DEADLINE.plusSeconds(1), current = AttendanceStatus.WAITLISTED), AttendanceIntent.CONFIRM, AttendanceStatus.WAITLISTED)
    }
    @Test fun `depois do prazo Nao vou e aceito para confirmado e para quem espera`() {
        for (mode in PromotionMode.entries) for (current in listOf(AttendanceStatus.CONFIRMED, AttendanceStatus.WAITLISTED)) {
            transition(vou(mode, true, mensalista, now = DEADLINE.plusSeconds(1), current = current), AttendanceIntent.DECLINE, AttendanceStatus.DECLINED, changed = true)
        }
    }
    @Test fun `convidado entra pela espera antes e depois do prazo`() {
        goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.FIFO, false, avulso, guest = true))
        goesTo(AttendanceStatus.WAITLISTED, vou(PromotionMode.MANUAL, true, mensalista, now = DEADLINE.plusSeconds(1), guest = true))
    }

    // --- DEB-045: prazo no início do jogo (ou depois) não tem janela de prioridade ---
    private fun emCimaDaHora(membership: AthleteMembershipType, mode: PromotionMode = PromotionMode.FIFO, confirmed: Int = 0, deadline: Instant = STARTS) =
        vou(mode, true, membership, confirmed = confirmed, now = STARTS.minusSeconds(60)).copy(confirmationDeadline = deadline)

    @Test fun `prazo no inicio - avulso com vaga confirma por ordem de chegada mesmo com prioridade ligada`() =
        goesTo(AttendanceStatus.CONFIRMED, emCimaDaHora(avulso))
    @Test fun `prazo no inicio - mensalista com vaga confirma`() = goesTo(AttendanceStatus.CONFIRMED, emCimaDaHora(mensalista))
    @Test fun `prazo no inicio - sem vaga todos esperam`() {
        goesTo(AttendanceStatus.WAITLISTED, emCimaDaHora(avulso, confirmed = 2))
        goesTo(AttendanceStatus.WAITLISTED, emCimaDaHora(mensalista, confirmed = 2))
    }
    @Test fun `prazo depois do inicio tambem nao tem prioridade`() =
        goesTo(AttendanceStatus.CONFIRMED, emCimaDaHora(avulso, deadline = STARTS.plusSeconds(60)))
    @Test fun `prazo no inicio em escolha manual - todos esperam`() {
        goesTo(AttendanceStatus.WAITLISTED, emCimaDaHora(avulso, PromotionMode.MANUAL))
        goesTo(AttendanceStatus.WAITLISTED, emCimaDaHora(mensalista, PromotionMode.MANUAL))
    }
    @Test fun `prazo antes do inicio segue com prioridade como antes`() =
        goesTo(AttendanceStatus.WAITLISTED, emCimaDaHora(avulso, deadline = STARTS.minusSeconds(30)).copy(now = STARTS.minusSeconds(60)))

    private fun transition(
        context: AttendanceDecisionContext,
        intent: AttendanceIntent,
        status: AttendanceStatus,
        changed: Boolean = false,
        allocate: Boolean = false,
        charge: Boolean = false,
        reason: String? = context.reason?.trim(),
    ) {
        val result = assertIs<AttendanceDecision.Transition>(AttendanceTransitionPolicy.decide(context, intent))
        assertEquals(status, result.newStatus)
        assertEquals(changed, result.changed)
        assertEquals(allocate, result.allocateWaitlistSequence)
        assertEquals(charge, result.createGameCharge)
        assertEquals(reason, result.reason)
    }

    private fun denied(context: AttendanceDecisionContext, intent: AttendanceIntent, reason: AttendanceDenial) =
        assertEquals(AttendanceDecision.Denied(reason), AttendanceTransitionPolicy.decide(context, intent))

    private fun context(
        status: GameStatus = GameStatus.PUBLISHED,
        now: Instant = DEADLINE.minusSeconds(1),
        capacity: Int = 2,
        confirmed: Int = 0,
        current: AttendanceStatus? = null,
        reason: String? = null,
        membership: AthleteMembershipType = AthleteMembershipType.MENSALISTA,
        mensalistaPriority: Boolean = true,
        guest: Boolean = false,
        promotionMode: PromotionMode = PromotionMode.FIFO,
    ) = AttendanceDecisionContext(status, DEADLINE, now, capacity, confirmed, current, AttendanceSource.SELF, reason, membership, mensalistaPriority, guest, promotionMode, STARTS)

    private fun organizer(
        status: GameStatus = GameStatus.PUBLISHED,
        now: Instant = DEADLINE.minusSeconds(1),
        capacity: Int = 2,
        confirmed: Int = 0,
        current: AttendanceStatus? = null,
        reason: String? = "Ajuste do organizador",
        membership: AthleteMembershipType = AthleteMembershipType.MENSALISTA,
        mensalistaPriority: Boolean = true,
    ) = AttendanceDecisionContext(status, DEADLINE, now, capacity, confirmed, current, AttendanceSource.ORGANIZER, reason, membership, mensalistaPriority, startsAt = STARTS)

    private companion object {
        val DEADLINE: Instant = Instant.parse("2026-08-11T22:30:00Z")
        val STARTS: Instant = Instant.parse("2026-08-12T01:00:00Z")
    }
}
