package br.com.saqz.receivables.presentation

import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.receivables.domain.OpenCharge
import br.com.saqz.receivables.domain.OpenChargeKind
import br.com.saqz.receivables.domain.PaidCharge
import br.com.saqz.receivables.domain.PaidVia
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Rótulos das cobranças do atleta, com os textos aprovados nos protótipos (TEXTOS.md). São dinâmicos (data, nome,
 * valor) e por isso saem prontos para o estado, como no caixa do grupo.
 */
internal object PaymentLabels {
    private val weekdays = mapOf(
        DayOfWeek.MONDAY to "segunda", DayOfWeek.TUESDAY to "terça", DayOfWeek.WEDNESDAY to "quarta",
        DayOfWeek.THURSDAY to "quinta", DayOfWeek.FRIDAY to "sexta", DayOfWeek.SATURDAY to "sábado",
        DayOfWeek.SUNDAY to "domingo",
    )
    private val months = listOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto",
        "setembro", "outubro", "novembro", "dezembro")

    fun weekday(date: String): String? = runCatching { weekdays[LocalDate.parse(date).dayOfWeek] }.getOrNull()

    /** `2026-07-28` → `28/07`. */
    fun dayMonth(date: String): String {
        val parts = date.split('-')
        return if (parts.size == 3) "${parts[2]}/${parts[1]}" else date
    }

    /** `2026-08` → `agosto`. */
    fun monthName(billingMonth: String?): String? =
        billingMonth?.split('-')?.getOrNull(1)?.toIntOrNull()?.let { months.getOrNull(it - 1) }

    /** "Jogo de terça · 21/07". */
    fun game(date: String): String = weekday(date)?.let { "Jogo de $it · ${dayMonth(date)}" } ?: "Jogo · ${dayMonth(date)}"

    fun title(charge: OpenCharge): String = when {
        charge.kind == OpenChargeKind.MONTHLY -> "Mensalidade · ${monthName(charge.billingMonth) ?: dayMonth(charge.dueDate)}"
        charge.guestName != null -> "Convidado: ${charge.guestName}"
        else -> game(charge.dueDate)
    }

    /** Linha de apoio de uma cobrança em aberto: "Venceu em 21/07", ou o jogo do convidado. */
    fun support(charge: OpenCharge, refused: Boolean = false): String {
        val due = if (charge.overdue) "Venceu em ${dayMonth(charge.dueDate)}" else "Vence em ${dayMonth(charge.dueDate)}"
        return when {
            refused -> "$due · cartão recusado"
            charge.guestName != null && charge.kind != OpenChargeKind.MONTHLY -> game(charge.dueDate)
            else -> due
        }
    }

    /** Linha do resumo na tela de pagamento: "Sua diária", "Convidado: Rafa", "Mensalidade de agosto". */
    fun breakdown(charge: OpenCharge, single: Boolean): String = when {
        charge.kind == OpenChargeKind.MONTHLY -> "Mensalidade de ${monthName(charge.billingMonth) ?: dayMonth(charge.dueDate)}"
        charge.guestName != null -> "Convidado: ${charge.guestName}"
        single -> "Sua diária"
        else -> game(charge.dueDate)
    }

    fun history(charge: PaidCharge): String = when {
        charge.kind == OpenChargeKind.MONTHLY -> "Mensalidade · ${monthName(charge.billingMonth) ?: dayMonth(charge.dueDate)}"
        charge.guestName != null -> "Convidado: ${charge.guestName} · ${dayMonth(charge.dueDate)}"
        else -> game(charge.dueDate)
    }

    /** "R$ 20,00 · pelo app, no Pix". */
    fun historySupport(charge: PaidCharge): String = "${formatBrl(charge.amountCents)} · " + when (charge.via) {
        PaidVia.APP_PIX -> "pelo app, no Pix"
        PaidVia.APP_CARD -> "pelo app · cartão"
        PaidVia.PIX -> "Pix do organizador"
        PaidVia.CASH -> "em dinheiro"
        PaidVia.OTHER -> "por fora"
    }

    /** O que um conjunto de cobranças é, em minúsculas: "mensalidade de agosto", "jogo de terça", "3 cobranças". */
    fun subject(charges: List<OpenCharge>): String {
        val monthly = charges.filter { it.kind == OpenChargeKind.MONTHLY }
        val games = charges.map { it.gameId ?: it.dueDate }.distinct()
        return when {
            monthly.size == 1 && charges.size == 1 ->
                "mensalidade de ${monthName(monthly.single().billingMonth) ?: dayMonth(monthly.single().dueDate)}"
            monthly.isEmpty() && games.size == 1 -> weekday(charges.first().dueDate)?.let { "jogo de $it" } ?: "jogo"
            charges.size == 1 -> "1 cobrança"
            else -> "${charges.size} cobranças"
        }
    }
}
