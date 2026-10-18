package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.subscriptions.presentation.countdown.TrialCountdownIntent
import br.com.saqz.subscriptions.presentation.countdown.TrialCountdownViewModel
import br.com.saqz.subscriptions.presentation.ui.countdown.TrialCountdownSheet
import org.koin.compose.viewmodel.koinViewModel

/**
 * Folha "Faltam N dias do seu teste" ao abrir o app (e ao voltar a ele) nos últimos 3 dias do teste, no máximo
 * uma vez por dia. Quem decide é o servidor; "Ver planos" abre a mesma tela de planos do diálogo de assinatura.
 * Último filho do Box que ocupa a tela, porque a folha é uma sobreposição.
 */
@Composable
internal fun TrialCountdownHost(
    sessionReady: Boolean,
    onViewPlans: () -> Unit,
    viewModel: TrialCountdownViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, sessionReady) {
        if (sessionReady) viewModel.onIntent(TrialCountdownIntent.Check)
    }
    LifecycleResumeEffect(viewModel, sessionReady) {
        if (sessionReady) viewModel.onIntent(TrialCountdownIntent.Check)
        onPauseOrDispose { }
    }
    if (state.visible) {
        TrialCountdownSheet(
            daysLeft = state.daysLeft,
            usage = state.usage,
            endsAt = state.endsAt,
            onDismiss = { viewModel.onIntent(TrialCountdownIntent.Dismiss) },
            onViewPlans = onViewPlans,
        )
    }
}
