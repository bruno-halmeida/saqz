package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.domain.game.Game
import java.util.UUID

/**
 * Cancelar um jogo publicado avisa quem ia: confirmados e lista de espera, na central e no push, e o grupo do
 * WhatsApp vinculado. O rascunho cancelado nunca dispara efeitos (ver ChangeGameLifecycle), então não avisa ninguém.
 */
class GameCancelledNotice(private val communication: GroupCommunicationService) : GameSideEffectPort {
    override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) {
        if (GameSideEffect.PARTICIPANTS_NOTIFIED_OF_CANCELLATION !in effects) return
        communication.announceGameCancelled(game.groupId, actorId, game.id, game.snapshot.localDate, game.snapshot.localTime)
    }
}
