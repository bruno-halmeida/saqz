package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.subscriptions.presentation.myplan.MyPlanEffect
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyPlanRoot(
    onBack: () -> Unit,
    onOpenChangePlan: () -> Unit = {},
    refreshVersion: Int = 0,
    viewModel: MyPlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadedVersion = rememberSaveable(viewModel) { refreshVersion }
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion != loadedVersion) viewModel.onIntent(MyPlanIntent.Refresh)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            MyPlanEffect.OpenChangePlan -> onOpenChangePlan()
        }
    }
    MyPlanScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
    )
}
