package br.com.saqz.subscriptions.presentation.planactive.di

import br.com.saqz.subscriptions.presentation.planactive.PlanActiveViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 8d (VUL-111). Sem argumento de rota: `PlanActiveViewModel` só depende do gateway. */
fun planActivePresentationModule(): Module = module {
    viewModel { PlanActiveViewModel(gateway = get()) }
}
