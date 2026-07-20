package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.application.game.GameSideEffectPort
import br.com.saqz.groups.domain.game.Game
import java.util.UUID

class GameFinanceSideEffects(private val charges: ChargeTransactions) : GameSideEffectPort {
    override fun apply(game: Game, actorId: UUID, effects: Set<GameSideEffect>) {
        if (GameSideEffect.PENDING_CHARGES_CANCELLED in effects) {
            charges.cancelGame(game.groupId, game.id, actorId)
        }
    }
}
