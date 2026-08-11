package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.subscriptions.presentation.myplan.MyPlanViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyPlanRoot(
    onBack: () -> Unit,
    onOpenChangePlan: (() -> Unit)? = null,
    viewModel: MyPlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyPlanScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        onOpenChangePlan = onOpenChangePlan,
    )
}
