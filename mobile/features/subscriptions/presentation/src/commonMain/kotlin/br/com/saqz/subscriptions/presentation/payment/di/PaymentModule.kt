package br.com.saqz.subscriptions.presentation.payment.di

import br.com.saqz.subscriptions.presentation.payment.PaymentViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * A 8c recebe [br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute.Payment]
 * via `parametersOf` (o `PaymentRoot` monta) e o `SavedStateHandle` do próprio Koin — nenhum
 * dos dois tem definição no grafo, por isso `viewModel { params -> ... }` e não `viewModelOf`
 * (mesmo motivo do `groupsPresentationModule`, ver o comentário lá).
 */
fun paymentPresentationModule(): Module = module {
    viewModel { params -> PaymentViewModel(params.get(), params.get(), get(), get()) }
}
