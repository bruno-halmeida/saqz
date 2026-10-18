package br.com.saqz.groups.application.communication

import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals

/** Texto exato que cada pessoa lê no aviso: o que muda de jogo, de presença e de entrada no grupo. */
class CommunicationBodiesTest {
    private val wednesday = LocalDate.of(2026, 10, 14)
    private val night = LocalTime.of(20, 30)

    @Test fun `aviso de jogo cancelado diz dia, data e hora`() {
        assertEquals("O jogo de quarta 14/10 às 20h30 foi cancelado.", gameCancelledBody(wednesday, night))
        assertEquals("O jogo de domingo 05/07 às 07h05 foi cancelado.", gameCancelledBody(LocalDate.of(2026, 7, 5), LocalTime.of(7, 5)))
    }

    @Test fun `aviso de jogo que mudou mostra o dia antigo, o novo, o local e a quadra quando há`() {
        val thursday = LocalDate.of(2026, 10, 15)
        assertEquals(
            "O jogo mudou! Era quarta 14/10, agora é quinta 15/10 às 20h30, Arena Central. Quem não puder, avise no app.",
            gameScheduleChangedBody(wednesday, thursday, night, "Arena Central", null),
        )
        assertEquals(
            "O jogo mudou! Era quarta 14/10, agora é quinta 15/10 às 20h30, Arena Central, Quadra 2. Quem não puder, avise no app.",
            gameScheduleChangedBody(wednesday, thursday, night, "Arena Central", "Quadra 2"),
        )
    }

    @Test fun `aviso de jogo liberado traz horário e local e pede a confirmação`() {
        assertEquals(
            "Jogo: quarta, 14/10 às 20:30\nLocal: Arena\n\nO jogo está liberado. Confirme sua presença.",
            openGameBody(ReminderGame(wednesday, night, "Arena")),
        )
    }

    @Test fun `prazo aparece em horário de São Paulo antes de vencer e como encerrado a partir do instante dele`() {
        val deadline = Instant.parse("2026-10-14T17:00:00Z")
        assertEquals("Confirmações até qua 14h00", deadlineLine(deadline, Instant.parse("2026-10-14T16:59:59Z")))
        assertEquals("Prazo encerrado", deadlineLine(deadline, deadline))
        assertEquals("Prazo encerrado", deadlineLine(deadline, deadline.plusSeconds(1)))
        assertEquals("Confirmações até dom 07h05", deadlineLine(Instant.parse("2026-10-18T10:05:00Z"), Instant.parse("2026-10-17T00:00:00Z")))
    }

    @Test fun `vagas mostram confirmados sobre capacidade e lista completa quando lota`() {
        assertEquals("Vagas: 10/12", vacanciesLine(10, 12))
        assertEquals("Vagas: 11/12", vacanciesLine(11, 12))
        assertEquals("Lista completa", vacanciesLine(12, 12))
        assertEquals("Lista completa", vacanciesLine(13, 12))
    }

    @Test fun `avisos de entrada no grupo dizem quem e qual grupo`() {
        assertEquals("Você entrou no Vôlei de quinta.", entryApprovedBody("Vôlei de quinta"))
        assertEquals("Ana pediu para entrar no Vôlei de quinta.", entryRequestBody("Ana", "Vôlei de quinta"))
        assertEquals("Ana entrou no Vôlei de quinta. A galera começou a chegar.", firstMemberBody("Ana", "Vôlei de quinta"))
    }

    @Test fun `primeira confirmação cita quem confirmou e o jogo`() {
        assertEquals(
            "Ana confirmou presença no jogo de quarta, 14/10 às 20:30. A lista começou a fechar sozinha.",
            firstConfirmationBody("Ana", wednesday, night),
        )
    }

    @Test fun `aviso de vaga confirmada fala com a pessoa ou, para convidado, com o anfitrião`() {
        assertEquals(
            "Sua vaga está confirmada no jogo de quarta, 14/10 às 20:30, em Arena.",
            attendanceConfirmedBody(wednesday, night, "Arena"),
        )
        assertEquals(
            "A vaga de Caio está confirmada no jogo de quarta, 14/10 às 20:30, em Arena.",
            attendanceConfirmedBody(wednesday, night, "Arena", "Caio"),
        )
    }

    @Test fun `aviso de vaga removida diz que foi a organização`() {
        assertEquals(
            "Sua vaga no jogo de quarta, 14/10 às 20:30 foi removida pela organização.",
            attendanceRemovedBody(wednesday, night),
        )
        assertEquals(
            "A vaga de Caio no jogo de quarta, 14/10 às 20:30 foi removida pela organização.",
            attendanceRemovedBody(wednesday, night, "Caio"),
        )
    }

    @Test fun `aviso de grupo excluído não cita o nome do grupo`() {
        assertEquals("Um dos seus grupos foi excluído pelo organizador.", GROUP_DELETED_BODY)
    }

    @Test fun `texto de exatamente 2000 caracteres passa inteiro e com um a mais é cortado no último nome completo`() {
        val game = ReminderGame(wednesday, night, "Arena")
        fun body(name: String) = reminderBody(game, ReminderRoster(listOf(name), emptyList(), emptyList()))
        val padding = REMINDER_BODY_LIMIT - body("").length
        val exact = body("a".repeat(padding))
        assertEquals(REMINDER_BODY_LIMIT, exact.length)
        assertEquals(false, exact.endsWith("…"))

        val names = (1..300).map { "Atleta número %03d".format(it) }
        val clipped = reminderBody(game, ReminderRoster(names, emptyList(), emptyList()))
        assertEquals(true, clipped.length <= REMINDER_BODY_LIMIT)
        assertEquals(true, clipped.endsWith("\n…"))
        val kept = clipped.removeSuffix("\n…").lines().takeLastWhile { it.startsWith("Atleta número") }
        assertEquals(names.take(kept.size), kept)
        assertEquals(true, clipped.length > REMINDER_BODY_LIMIT - 40, "corta o mínimo necessário: ${clipped.length}")
    }

    @Test fun `somar passos da fila conta itens, entregas e falhas`() {
        val run = QueueRun() + QueueStep(delivered = 2, failed = 1) + QueueStep(delivered = 0, failed = 3) + QueueStep()
        assertEquals(QueueRun(processed = 3, delivered = 2, failed = 4), run)
    }
}
