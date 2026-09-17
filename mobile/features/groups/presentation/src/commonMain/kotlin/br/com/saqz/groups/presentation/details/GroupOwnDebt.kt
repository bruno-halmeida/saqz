package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_own_charge_pix_receiver
import br.com.saqz.groups.resources.home_own_charges_count
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString

/**
 * O resumo da dívida do próprio usuário neste grupo, no molde do ticket da Início (VUL-220):
 * competência e vencimento são os da pendência MAIS ANTIGA, o valor é a soma das pendentes.
 *
 * [pending] e [pendingUi] chegam na mesma ordem (vencimento crescente) — a primeira de cada
 * é a mesma cobrança. Sem pendência não há dívida: devolve `null` e o ticket não existe.
 */
internal suspend fun groupOwnDebt(
    pending: List<Charge>,
    pendingUi: List<OwnChargeUi>,
    today: LocalDate,
    pixLabel: String?,
    hasPixKey: Boolean,
): GroupOwnDebtUi? {
    val oldest = pending.firstOrNull()
    val oldestUi = pendingUi.firstOrNull()
    if (oldest == null || oldestUi == null) return null
    return GroupOwnDebtUi(
        eyebrow = oldestUi.title,
        totalLabel = formatBrl(pending.sumOf { it.amountCents }),
        dueLabel = oldestUi.dueLabel,
        overdue = oldest.dueDate < today.toString(),
        countLabel = pending.size.takeIf { it > 1 }?.let { getString(Res.string.home_own_charges_count, it) },
        receiverLabel = pixLabel?.trim()?.takeIf { it.isNotEmpty() && hasPixKey }
            ?.let { getString(Res.string.home_own_charge_pix_receiver, it) },
    )
}
