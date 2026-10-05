package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.presentation.myplan.MyPlanEffect
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MyPlanRoot(
    onBack: () -> Unit,
    onOpenChangePlan: () -> Unit = {},
    onOpenSubscribe: () -> Unit = {},
    refreshVersion: Int = 0,
    /** Abre a gestão de assinaturas da loja deste aparelho; nulo quando não há loja. */
    onManageStoreSubscription: (() -> Unit)? = null,
    /** A loja que este aparelho sabe abrir: App Store no iOS, Google Play no Android. */
    deviceStore: SubscriptionProvider? = null,
    purchasesAvailable: Boolean = br.com.saqz.domain.StoreLaunchPolicy.purchases,
    viewModel: MyPlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onIntent(MyPlanIntent.Refresh)
        onPauseOrDispose { }
    }
    val loadedVersion = rememberSaveable(viewModel) { refreshVersion }
    LaunchedEffect(viewModel, refreshVersion) {
        if (refreshVersion != loadedVersion) viewModel.onIntent(MyPlanIntent.Refresh)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            MyPlanEffect.OpenChangePlan -> onOpenChangePlan()
            MyPlanEffect.OpenSubscribe -> onOpenSubscribe()
            MyPlanEffect.ManageStoreSubscription -> onManageStoreSubscription?.invoke()
        }
    }
    MyPlanScreen(
        state = state,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        deviceStore = deviceStore.takeIf { onManageStoreSubscription != null },
        purchasesAvailable = purchasesAvailable,
    )
}
