package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceDenial
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O que `RespondAttendance` faz com a cobrança do jogo e com a fila quando a resposta muda a presença.
 * Regras: PROM-008 (cobrança de quem confirma, sobe e do convidado), PROM-001 (o que libera vaga),
 * PROM-003 (ordem de chegada), PRES-004 ("Não vou" libera a vaga), PRES-005 ("Vou" depois do prazo sobe na hora),
 * PRES-009 (repetir não muda nada). Quem é avulso ou mensalista é decidido no adaptador de cobrança
 * (`ChargeTransactionsTest`), aqui o que vale é a porta ser chamada, uma vez, com os argumentos certos.
 */
class RespondAttendanceChargesTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val athlete = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()
    private val repository = InMemoryAttendance(group, game, NOW).apply {
        roles[owner] = GroupRole.OWNER
        roles[athlete] = GroupRole.ATHLETE
        roles[other] = GroupRole.ATHLETE
    }
    private val charges = RecordingCharges()
    private val responses = RespondAttendance(DIRECT_TRANSACTION, repository, charges, { NOW }, UUID::randomUUID)
    private val guests = GameGuests(DIRECT_TRANSACTION, repository, responses) { NOW }

    @Test
    fun `confirmar com vaga cobra o jogo uma vez, de quem confirmou, assinado por ele`() {
        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.CONFIRMED, result.attendance.status)
        assertEquals(listOf(ChargeCall("confirmed", athlete, 0, athlete)), charges.calls)
    }

    @Test
    fun `organizador que confirma por outra pessoa cobra dela, assinado pelo organizador`() {
        responses.execute(owner, group, game, memberId = athlete, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Chegou na quadra")

        assertEquals(listOf(ChargeCall("confirmed", athlete, 0, owner)), charges.calls)
    }

    @Test
    fun `Vou que cai na espera nao cobra`() {
        repository.capacity = 0

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `repetir o Vou nao cobra de novo`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)

        assertEquals(1, charges.calls.size)
        assertEquals(1, repository.events.size)
    }

    @Test
    fun `Nao vou de quem estava confirmado nao estorna a cobranca dele`() {
        responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM)

        responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE)

        assertEquals(AttendanceStatus.DECLINED, repository.status(athlete))
        assertEquals(listOf(ChargeCall("confirmed", athlete, 0, athlete)), charges.calls)
    }

    @Test
    fun `convidado confirmado que sai tem a cobranca estornada com o numero dele`() {
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.CONFIRMED, guestSeq = 1)

        val result = assertIs<AttendanceCommandResult.Success>(guests.remove(athlete, group, game, athlete, 1))

        assertEquals(AttendanceStatus.DECLINED, result.attendance.status)
        assertEquals(listOf(ChargeCall("guestRemoved", athlete, 1, athlete)), charges.calls)
    }

    @Test
    fun `convidado na espera que sai nao tem cobranca para estornar`() {
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.WAITLISTED, sequence = 1, guestSeq = 1)

        guests.remove(athlete, group, game, athlete, 1)

        assertEquals(AttendanceStatus.DECLINED, repository.status(athlete, 1))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `convidado confirmado que o organizador remove tem a cobranca estornada assinada pelo organizador`() {
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.CONFIRMED, guestSeq = 2)

        guests.remove(owner, group, game, athlete, 2)

        assertEquals(listOf(ChargeCall("guestRemoved", athlete, 2, owner)), charges.calls)
    }

    @Test
    fun `convidado que entra pela espera nao cobra enquanto nao subir`() {
        repository.record(athlete, AttendanceStatus.CONFIRMED)

        val result = assertIs<AttendanceCommandResult.Success>(guests.add(athlete, group, game, "Rafa Moreira", UUID.randomUUID()))

        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `anfitriao que sai derruba os convidados antes da fila, estorna so os confirmados e a fila sobe nas vagas dos dois`() {
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.CONFIRMED, guestSeq = 1)
        repository.record(athlete, AttendanceStatus.WAITLISTED, sequence = 1, guestSeq = 2)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 2)
        repository.record(second, AttendanceStatus.WAITLISTED, sequence = 3)
        repository.record(third, AttendanceStatus.WAITLISTED, sequence = 4)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertEquals(AttendanceStatus.DECLINED, result.attendance.status)
        // Duas vagas liberadas (o anfitrião e o convidado confirmado); o convidado na espera cai sem abrir vaga.
        assertEquals(listOf(first, second), result.promoted.map { it.memberId })
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(second))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(third))
        assertEquals(AttendanceStatus.DECLINED, repository.status(athlete, 1))
        assertEquals(AttendanceStatus.DECLINED, repository.status(athlete, 2))
        assertEquals(
            listOf(
                ChargeCall("guestRemoved", athlete, 1, athlete),
                ChargeCall("promoted", first, 0, athlete),
                ChargeCall("promoted", second, 0, athlete),
            ),
            charges.calls,
        )
        val systemDrops = repository.events.filter { it.source == AttendanceSource.SYSTEM && it.newStatus == AttendanceStatus.DECLINED }
        assertEquals(listOf(1 to AttendanceStatus.CONFIRMED, 2 to AttendanceStatus.WAITLISTED), systemDrops.map { it.guestSeq to it.oldStatus })
        assertTrue(systemDrops.all { it.memberId == athlete && it.actorId == athlete })
        assertEquals(listOf(2L, 2L), listOf(first, second).map { repository.records.getValue(it to 0).version })
    }

    @Test
    fun `anfitriao na espera que sai nao abre vaga nenhuma`() {
        repository.capacity = 2
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(other, AttendanceStatus.CONFIRMED)
        repository.record(athlete, AttendanceStatus.WAITLISTED, sequence = 1)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 2)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertTrue(result.promoted.isEmpty())
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `em escolha manual a saida libera a vaga mas ninguem sobe sozinho`() {
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 2
        repository.record(athlete, AttendanceStatus.CONFIRMED)
        repository.record(other, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.DECLINE))

        assertTrue(result.promoted.isEmpty())
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `Vou depois do prazo com vagas livres entra no fim da fila e a fila ocupa as vagas na hora`() {
        repository.deadline = NOW.minusSeconds(60)
        repository.capacity = 4
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(other, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)
        repository.record(second, AttendanceStatus.WAITLISTED, sequence = 2)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        // Duas vagas livres (4 - 2): quem estava na frente sobe, quem acabou de chegar continua esperando.
        assertEquals(listOf(first, second), result.promoted.map { it.memberId })
        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertEquals(3L, result.attendance.waitlistSequence)
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(athlete))
        assertEquals(
            listOf(ChargeCall("promoted", first, 0, athlete), ChargeCall("promoted", second, 0, athlete)),
            charges.calls,
        )
    }

    @Test
    fun `Vou depois do prazo com vaga e fila vazia sobe na hora e o resultado e a presenca confirmada`() {
        repository.deadline = NOW.minusSeconds(60)
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.CONFIRMED, result.attendance.status)
        assertEquals(listOf(athlete), result.promoted.map { it.memberId })
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(athlete))
        assertEquals(listOf(ChargeCall("promoted", athlete, 0, athlete)), charges.calls)
        assertEquals(listOf(AttendanceStatus.WAITLISTED, AttendanceStatus.CONFIRMED), repository.events.map { it.newStatus })
    }

    @Test
    fun `Vou depois do prazo com o jogo lotado fica na espera sem promover ninguem`() {
        repository.deadline = NOW.minusSeconds(60)
        repository.capacity = 2
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(other, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertTrue(result.promoted.isEmpty())
        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `Vou no instante exato do prazo ainda segue o caminho de antes do prazo`() {
        repository.deadline = NOW
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.CONFIRMED, result.attendance.status)
        assertTrue(result.promoted.isEmpty())
        assertEquals(listOf(ChargeCall("confirmed", athlete, 0, athlete)), charges.calls)
    }

    @Test
    fun `Vou depois do prazo em escolha manual espera o organizador`() {
        repository.deadline = NOW.minusSeconds(60)
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)

        val result = assertIs<AttendanceCommandResult.Success>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceStatus.WAITLISTED, result.attendance.status)
        assertTrue(result.promoted.isEmpty())
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `promover pela resposta nao existe e a promocao so vem pelo comando proprio`() {
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        assertEquals(AttendanceCommandResult.Forbidden, responses.execute(owner, group, game, memberId = first, intent = AttendanceIntent.PROMOTE, source = AttendanceSource.ORGANIZER, reason = "Chegou"))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
    }

    @Test
    fun `quem nao e do grupo nao ve o jogo e atleta nao responde por outra pessoa`() {
        assertEquals(AttendanceCommandResult.Hidden, responses.execute(UUID.randomUUID(), group, UUID.randomUUID(), intent = AttendanceIntent.CONFIRM))
        assertEquals(AttendanceCommandResult.Hidden, responses.execute(other, group, game, memberId = athlete, intent = AttendanceIntent.CONFIRM))
        assertEquals(
            AttendanceCommandResult.Forbidden,
            responses.execute(athlete, group, game, memberId = other, intent = AttendanceIntent.CONFIRM, source = AttendanceSource.ORGANIZER, reason = "Chegou"),
        )
        assertTrue(repository.records.isEmpty())
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `jogo nao publicado nega a resposta sem gravar nem cobrar`() {
        repository.gameStatus = br.com.saqz.groups.domain.game.GameStatus.DRAFT

        val result = assertIs<AttendanceCommandResult.Denied>(responses.execute(athlete, group, game, intent = AttendanceIntent.CONFIRM))

        assertEquals(AttendanceDenial.NOT_PUBLISHED, result.reason)
        assertTrue(repository.records.isEmpty())
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `promocao manual sobe a pessoa, cobra assinado pelo organizador e grava o evento com o requestId`() {
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 3
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 4, version = 7)
        val requestId = UUID.randomUUID()

        val result = assertIs<AttendanceCommandResult.Success>(
            responses.promote(owner, group, game, first, requestId, "Chegou cedo"),
        )

        assertEquals(AttendanceStatus.CONFIRMED, result.attendance.status)
        assertEquals(null, result.attendance.waitlistSequence)
        assertEquals(8L, result.attendance.version)
        assertEquals(listOf(first), result.promoted.map { it.memberId })
        assertEquals(listOf(ChargeCall("promoted", first, 0, owner)), charges.calls)
        val event = result.event!!
        assertEquals(requestId, event.requestId)
        assertEquals(AttendanceStatus.WAITLISTED, event.oldStatus)
        assertEquals(AttendanceStatus.CONFIRMED, event.newStatus)
        assertEquals(AttendanceSource.ORGANIZER, event.source)
        assertEquals("Chegou cedo", event.reason)
        assertEquals(owner, event.actorId)
        assertEquals(first, event.memberId)
        assertEquals(NOW, event.occurredAt)
    }

    @Test
    fun `promocao manual respeita papel, modo do grupo, lotacao e quem nao esta na espera`() {
        repository.capacity = 1
        repository.record(owner, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        // Em ordem de chegada o organizador não escolhe.
        assertEquals(
            AttendanceCommandResult.Denied(AttendanceDenial.MANUAL_PROMOTION_ONLY),
            responses.promote(owner, group, game, first, UUID.randomUUID(), "Chegou"),
        )
        repository.promotionMode = PromotionMode.MANUAL
        assertEquals(AttendanceCommandResult.Forbidden, responses.promote(athlete, group, game, first, UUID.randomUUID(), "Chegou"))
        assertEquals(AttendanceCommandResult.Hidden, responses.promote(UUID.randomUUID(), group, game, first, UUID.randomUUID(), "Chegou"))
        // Sem vaga livre.
        assertEquals(
            AttendanceCommandResult.Denied(AttendanceDenial.NO_CAPACITY),
            responses.promote(owner, group, game, first, UUID.randomUUID(), "Chegou"),
        )
        // Quem já está confirmado (outro gestor colocou antes) não sobe de novo: o pedido dá certo sem mudar nada.
        repository.capacity = 3
        val already = assertIs<AttendanceCommandResult.Success>(responses.promote(owner, group, game, owner, UUID.randomUUID(), "Chegou"))
        assertEquals(AttendanceStatus.CONFIRMED, already.attendance.status)
        assertNull(already.event)
        // Quem saiu da espera com "Não vou" continua negado.
        repository.record(second, AttendanceStatus.DECLINED)
        assertEquals(
            AttendanceCommandResult.Denied(AttendanceDenial.NOT_WAITLISTED),
            responses.promote(owner, group, game, second, UUID.randomUUID(), "Chegou"),
        )
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(charges.calls.isEmpty())
        assertTrue(repository.events.isEmpty())
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
