package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_names_more
import br.com.saqz.groups.resources.group_details_names_two
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_waiting_monthly
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant

/**
 * "Esperando você" do gestor, com as mesmas três linhas e as mesmas chaves da Início:
 *
 * - **mensalidades a receber**: só as pendentes da competência [monthKey] (o mês corrente no
 *   fuso de cobrança do grupo) — a mesma regra da Início; atraso de mês anterior é assunto do
 *   caixa, não desta linha;
 * - **acertar o jogo**: o jogo concluído MAIS RECENTE que ainda tem cobrança avulsa pendente.
 *   Some enquanto o guia de onboarding `ReviewFinances` fala desse mesmo jogo
 *   ([reviewingGameId]): duas chamadas para o mesmo acerto na mesma tela é ruído;
 * - **pedidos para entrar**: quem pediu, pelo nome.
 *
 * Nenhuma linha → `null`, e o bloco não existe.
 */
internal suspend fun groupWaiting(
    charges: List<Charge>,
    games: List<Game>,
    entryRequests: List<GroupEntryRequest>,
    monthKey: String,
    reviewingGameId: String?,
): GroupWaitingUi? {
    val pending = charges.filter { it.status == ChargeStatus.Pending }
    val waiting = GroupWaitingUi(
        entryRequests = entryRequests.toEntryRequestsRow(),
        monthly = pending.toMonthlyRow(monthKey),
        settle = games.toSettleRow(pending, reviewingGameId),
    )
    return waiting.takeIf { it.entryRequests != null || it.monthly != null || it.settle != null }
}

private suspend fun List<Charge>.toMonthlyRow(monthKey: String): GroupWaitingRowUi? {
    val open = filter { it.kind == ChargeKind.Monthly && it.month == monthKey }
    if (open.isEmpty()) return null
    val month = runCatching { LocalDate.parse("$monthKey-01") }.getOrNull()?.let { gameShortMonthLabel(it.month.ordinal + 1) }.orEmpty()
    val title = getString(Res.string.home_admin_waiting_monthly, open.size)
    val meta = getString(Res.string.home_admin_waiting_monthly_meta, formatBrl(open.sumOf { it.amountCents }), month)
    return GroupWaitingRowUi(title = title, meta = meta, contentDescription = "$title. $meta", count = open.size)
}

private suspend fun List<Game>.toSettleRow(pending: List<Charge>, reviewingGameId: String?): GroupSettleRowUi? {
    val dayCharges = pending.filter { it.kind == ChargeKind.Game }
    val latest = filter { game -> game.status == GameStatus.Completed && dayCharges.any { it.gameId == game.id } }
        .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) }.getOrNull()?.let { it to game } }
        .maxByOrNull { it.first }
    if (latest == null || latest.second.id == reviewingGameId) return null
    val (startsAt, game) = latest
    val open = dayCharges.filter { it.gameId == game.id }
    val date = startsAt.toLocalDateTime(gameTimeZone(game.zoneId)).date.gameDateLabel()
    val title = getString(Res.string.home_admin_waiting_settle, date)
    val meta = getString(Res.string.home_admin_waiting_settle_meta, open.size, formatBrl(open.sumOf { it.amountCents }))
    return GroupSettleRowUi(gameId = game.id, title = title, meta = meta, contentDescription = "$title. $meta")
}

private suspend fun List<GroupEntryRequest>.toEntryRequestsRow(): GroupWaitingRowUi? {
    if (isEmpty()) return null
    val names = map { it.displayName }
    val title = getString(Res.string.home_admin_waiting_entry_requests, size)
    val meta = when (size) {
        1 -> names.single()
        2 -> getString(Res.string.group_details_names_two, names[0], names[1])
        else -> getString(Res.string.group_details_names_more, names[0], names[1], size - 2)
    }
    return GroupWaitingRowUi(title = title, meta = meta, contentDescription = "$title. $meta", count = size)
}
