package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameSnapshot
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Efeitos de publicar e cancelar jogo que chegam às pessoas: aviso de jogo liberado e de jogo cancelado. */
class GameNoticesTest {
    private val group = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val repository = MemoryCommunicationRepository()
    private val communication = GroupCommunicationService(CountingTransaction(), FixedRoleGroups(group, GroupRole.OWNER), repository)
    private val game = Game(
        UUID.randomUUID(), group,
        GameSnapshot(
            "Vôlei", GameVenueSnapshot(UUID.randomUUID(), "Arena Central", "Rua 1", null),
            LocalDate.of(2026, 10, 14), LocalTime.of(20, 30), IanaTimeZone.from("America/Sao_Paulo"),
            Instant.parse("2026-10-14T23:30:00Z"), 120, 12, Instant.parse("2026-10-14T17:00:00Z"), null, null,
        ),
        GameStatus.PUBLISHED,
    )

    @Test fun `cancelar jogo publicado avisa quem ia, com o dia e a hora do jogo`() {
        GameCancelledNotice(communication).apply(game, actor, setOf(GameSideEffect.PARTICIPANTS_NOTIFIED_OF_CANCELLATION))
        assertEquals(
            listOf<Any>(group, actor, game.id, "O jogo de quarta 14/10 às 20h30 foi cancelado."),
            repository.cancelled.single(),
        )
    }

    @Test fun `cancelar sem o efeito de aviso, como rascunho, não avisa ninguém`() {
        GameCancelledNotice(communication).apply(game, actor, setOf(GameSideEffect.PENDING_CHARGES_CANCELLED))
        GameCancelledNotice(communication).apply(game, actor, emptySet())
        assertTrue(repository.cancelled.isEmpty())
    }

    @Test fun `publicar o próximo jogo avisa na hora quando o aviso está ligado`() {
        repository.single = ReminderCandidate(game.id, group, owner, ReminderGame(game.snapshot.localDate, game.snapshot.localTime, "Arena Central"))
        GameOpenOnPublish(communication, enabled = true).apply(game, actor, setOf(GameSideEffect.ATTENDANCE_OPENED))
        val sent = repository.published.single()
        assertEquals(MessageChannel.GAME_OPEN, sent.channel)
        assertEquals(game.id, sent.gameId)
        assertEquals(owner, sent.actor)
    }

    @Test fun `aviso desligado ou publicação que não abriu a presença ficam em silêncio`() {
        repository.single = ReminderCandidate(game.id, group, owner, ReminderGame(game.snapshot.localDate, game.snapshot.localTime, "Arena Central"))
        GameOpenOnPublish(communication, enabled = false).apply(game, actor, setOf(GameSideEffect.ATTENDANCE_OPENED))
        GameOpenOnPublish(communication, enabled = true).apply(game, actor, setOf(GameSideEffect.SCHEDULE_CHANGED))
        assertTrue(repository.published.isEmpty())
    }
}
