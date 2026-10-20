package br.com.saqz.groups.presentation.ui.monthlyinvite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteEffect
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteIntent
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * "Convidar mensalista" (CONV-010). [onConfigureFee] abre o cartão "Mensalidade" de Editar grupo; [onOpenPlans] é a
 * escolha de plano. Voltar de Editar grupo recarrega a mensalidade sem perder a lista em montagem.
 */
@Composable
fun MonthlyInviteRoot(
    groupId: String,
    onBack: () -> Unit,
    onConfigureFee: (String) -> Unit,
    onOpenPlans: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonthlyInviteViewModel = koinViewModel(key = "monthly-invite/$groupId", parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    RefreshWhenVisible { viewModel.onIntent(MonthlyInviteIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is MonthlyInviteEffect.OpenFeeSettings -> onConfigureFee(effect.groupId)
            MonthlyInviteEffect.OpenPlans -> onOpenPlans()
            is MonthlyInviteEffect.Copy -> clipboard.setText(AnnotatedString(effect.text))
        }
    }
    MonthlyInviteScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
