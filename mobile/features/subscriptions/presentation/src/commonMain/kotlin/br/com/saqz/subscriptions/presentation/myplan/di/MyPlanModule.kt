package br.com.saqz.subscriptions.presentation.myplan.di

import br.com.saqz.subscriptions.presentation.myplan.MyPlanViewModel
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Só o `SubscriptionGateway` do `subscriptionsDataModule` — sem argumento de rota. */
fun myPlanPresentationModule(): Module = module {
    viewModel { br.com.saqz.subscriptions.presentation.trial.TrialEntryViewModel(get()) }
    viewModel { MyPlanViewModel(gateway = get(), trialGateway = get()) }
    viewModel { ChangePlanViewModel(gateway = get()) }
}
