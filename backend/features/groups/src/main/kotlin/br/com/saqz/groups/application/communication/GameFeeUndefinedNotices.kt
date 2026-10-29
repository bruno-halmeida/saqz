package br.com.saqz.groups.application.communication

import br.com.saqz.groups.application.create.TransactionRunner
import java.util.UUID

/** Marca do aviso aos grupos sem valor por jogo (DEB-146), no canal FIRST_MEMBER. */
const val GAME_FEE_UNDEFINED_EVENT = "GAME_FEE_UNDEFINED"

fun gameFeeUndefinedTitle(groupName: String) = "$groupName: defina o valor por jogo"
const val GAME_FEE_UNDEFINED_BODY = "Hoje o avulso joga sem diária. Toque para definir."

interface GameFeeUndefinedNoticeRepository {
    /** Grupos ativos com valor vazio e sem a marca "Sem diária" que ainda não receberam o aviso. */
    fun pending(): List<UUID>
    /** Grava e entrega o aviso a dono e admins, uma vez por grupo; false se o grupo já tem valor ou já recebeu. */
    fun publish(groupId: UUID): Boolean
}

/**
 * Aviso único a dono e admins dos grupos "não definidos" (JOGO-008). Liga só com o app novo nas lojas
 * (`saqz.groups.game-fee-notice.enabled`): o toque leva ao "Valor por jogo", que o app antigo não tem.
 * Um grupo por transação; rodar de novo não repete (índice único e `request_id` por grupo).
 */
class GameFeeUndefinedNotices(
    private val transactionRunner: TransactionRunner,
    private val repository: GameFeeUndefinedNoticeRepository,
) {
    fun run(): Int = repository.pending().count { group -> transactionRunner.inTransaction { repository.publish(group) } }
}
