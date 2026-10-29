package br.com.saqz.groups.presentation

import kotlin.math.roundToLong

/** DEB-146 · JOGO-008: as duas opções do bloco "Valor por jogo". Nada escolhido = `null` (não salva). */
enum class GameFeeChoice { Charge, NoFee }

/** O que falta no bloco para salvar; `null` = escolha válida. */
enum class GameFeeProblem { Missing, Invalid }

/** Escolha que o servidor já guarda: valor = Cobrar diária, marca = Sem diária, nenhum dos dois = não definido. */
fun gameFeeChoiceOf(cents: Long?, noGameFee: Boolean): GameFeeChoice? = when {
    cents != null -> GameFeeChoice.Charge
    noGameFee -> GameFeeChoice.NoFee
    else -> null
}

/** Sem escolha não salva; Cobrar diária precisa de um valor de R$ 0,01 a R$ 999.999,99 (vazio e 0 não valem). */
fun gameFeeProblem(choice: GameFeeChoice?, cents: Long?): GameFeeProblem? = when (choice) {
    null -> GameFeeProblem.Missing
    GameFeeChoice.NoFee -> null
    GameFeeChoice.Charge -> if (cents == null || cents !in 1L..GameFeeMaxCents) GameFeeProblem.Invalid else null
}

/** A mensalidade ÷ 4, arredondada ao real; `null` sem mensalidade. */
fun suggestedGameFeeCents(monthlyFeeCents: Long?): Long? {
    val monthly = monthlyFeeCents?.takeIf { it > 0 } ?: return null
    return ((monthly / WeeksPerMonth) / CentsPerReal).roundToLong().coerceAtLeast(1) * CentsPerReal.toLong()
}

const val GameFeeMaxCents = 99_999_999L
private const val WeeksPerMonth = 4.0
private const val CentsPerReal = 100.0
