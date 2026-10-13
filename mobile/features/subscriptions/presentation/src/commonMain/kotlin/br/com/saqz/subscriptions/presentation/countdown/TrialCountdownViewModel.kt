package br.com.saqz.subscriptions.presentation.countdown

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.launch

/**
 * Decide se a folha de contagem do teste abre agora. Quem manda é o servidor: o teste precisa estar ativo, ser do
 * dono, faltar no máximo [TRIAL_COUNTDOWN_DAYS] dias e a folha ainda não ter aberto hoje. Ao abrir, o servidor é
 * avisado para não repetir no mesmo dia (nem em outro aparelho); o processo também lembra o dia, para não reabrir
 * depois de "Agora não" quando a rede falha.
 */
class TrialCountdownViewModel(private val gateway: TrialGateway) :
    MviViewModel<TrialCountdownState, TrialCountdownIntent, Nothing>(TrialCountdownState()) {
    private var checking = false
    // Dia (contado em dias que faltam) em que a folha já abriu neste processo: cobre a falha de rede ao avisar o servidor.
    private var shownForDaysLeft: Int? = null

    override fun handleIntent(intent: TrialCountdownIntent) {
        when (intent) {
            TrialCountdownIntent.Check -> check()
            TrialCountdownIntent.Dismiss -> update { it.copy(visible = false) }
        }
    }

    private fun check() {
        if (checking) return
        checking = true
        viewModelScope.launch {
            try {
                val access = (gateway.ownerTrial() as? SaqzResult.Success)?.value ?: return@launch
                val daysLeft = access.daysLeft ?: return@launch
                val due = access.isOwner && access.status == TrialStatus.Active && !access.countdownSeenToday &&
                    daysLeft in 0..TRIAL_COUNTDOWN_DAYS
                if (!due || daysLeft == shownForDaysLeft || state.value.visible) return@launch
                shownForDaysLeft = daysLeft
                update { it.copy(visible = true, daysLeft = daysLeft, usage = access.usage) }
                gateway.markCountdownSeen()
            } finally {
                checking = false
            }
        }
    }
}
