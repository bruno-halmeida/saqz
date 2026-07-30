package br.com.saqz.subscriptions.presentation.planselection.di

import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Só a 8a/8b (VUL-109). O `SubscriptionGateway` já está em `commonModules`
 * (`subscriptionsDataModule`, VUL-108) — esta tela não recebe argumento de rota, então
 * não precisa de estado inicial construído por fora como o grafo de grupos.
 */
fun planSelectionPresentationModule(): Module = module {
    viewModel { PlanSelectionViewModel(gateway = get()) }
}
