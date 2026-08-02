package br.com.saqz.groups.presentation.ui.athleteregistration

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationEffect
import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AthleteRegistrationRoot(
    groupId: String,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AthleteRegistrationViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            AthleteRegistrationEffect.Saved -> onSaved()
        }
    }
    AthleteRegistrationScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
