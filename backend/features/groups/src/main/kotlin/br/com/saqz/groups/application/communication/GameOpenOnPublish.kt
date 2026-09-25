package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.domain.game.Game
import java.util.UUID

/**
 * Publicar o próximo jogo do grupo avisa na hora. [enabled] é a mesma chave do cron do aviso
 * (`saqz.notifications.reminder.enabled`): desligada, a publicação fica em silêncio como antes.
 */
class GameOpenOnPublish(
    private val communication: GroupCommunicationService,
    private val enabled: Boolean,
) : GameSideEffectPort {
    override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) {
        if (enabled && GameSideEffect.ATTENDANCE_OPENED in effects) {
            communication.announceOpenGame(game.groupId, game.id)
        }
    }
}
