package br.com.saqz.groups.domain.game

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/** Linha de "Jogos anteriores" (JOGO-011): só dono e admin recebem. */
data class PastGame(
    val id: String,
    val localDate: String,
    val localTime: String,
    val zoneId: String,
    val startsAt: String,
    val status: GameStatus,
    /** Encerrado: quem foi (sem quem faltou). Cancelado: quem estava confirmado. */
    val confirmedCount: Int,
    val capacity: Int,
    val venueName: String,
    val pendingChargeCount: Int,
    val pendingChargeCents: Long,
)

/** Uma página de 20; [nextCursor] nulo no fim. */
data class PastGamesPage(val games: List<PastGame>, val nextCursor: String?)

interface PastGamesGateway {
    suspend fun page(groupId: GroupId, cursor: String?): SaqzResult<PastGamesPage, GameError>

    /** PRES-016: marca ([marked]) ou desfaz a falta de quem confirmou; convidado vai pelo [guestSeq]. Idempotente. */
    suspend fun setNoShow(groupId: GroupId, gameId: String, memberId: String, guestSeq: Int, marked: Boolean): SaqzResult<Unit, GameError>
}
