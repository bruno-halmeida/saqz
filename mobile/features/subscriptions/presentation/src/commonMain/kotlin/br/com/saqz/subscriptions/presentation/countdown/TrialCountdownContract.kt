package br.com.saqz.subscriptions.presentation.countdown

import androidx.compose.runtime.Immutable
import br.com.saqz.subscriptions.domain.trial.TrialUsage

/** A folha "Faltam N dias do seu teste": só visível nos últimos 3 dias do teste, no máximo uma vez por dia. */
@Immutable
data class TrialCountdownState(
    val visible: Boolean = false,
    val daysLeft: Int = 0,
    val usage: TrialUsage? = null,
)

sealed interface TrialCountdownIntent {
    /** O app abriu (ou voltou ao primeiro plano com a sessão pronta): pergunta ao servidor se a folha cabe hoje. */
    data object Check : TrialCountdownIntent
    data object Dismiss : TrialCountdownIntent
}

/** Últimos dias em que a folha aparece: faltam 3, 2, 1 ou é o último dia. */
const val TRIAL_COUNTDOWN_DAYS = 3
