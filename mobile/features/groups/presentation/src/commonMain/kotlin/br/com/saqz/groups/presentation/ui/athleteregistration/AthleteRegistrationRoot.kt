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
    onSave: () -> Unit,
    onBack: () -> Unit,
    pendingRequest: Boolean = false,
    viewModel: AthleteRegistrationViewModel = koinViewModel(
        key = "athlete-registration/$groupId${if (pendingRequest) "/pending" else ""}",
        parameters = { parametersOf(groupId, pendingRequest) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            AthleteRegistrationEffect.Saved, AthleteRegistrationEffect.Unavailable -> onSave()
        }
    }
    AthleteRegistrationScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
