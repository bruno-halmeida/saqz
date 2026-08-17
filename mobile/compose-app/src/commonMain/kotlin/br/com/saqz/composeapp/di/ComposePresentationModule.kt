package br.com.saqz.composeapp.di

import br.com.saqz.composeapp.navigation.AccessOrchestrator
import br.com.saqz.composeapp.navigation.AccessRuntimeContract
import br.com.saqz.composeapp.navigation.AccessViewModel
import br.com.saqz.composeapp.subscriptiongate.SubscriptionGateViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * O que é do app-shell: o portão de sessão e o orquestrador que ele projeta.
 *
 * As ViewModels das telas de acesso mudaram para o [accessPresentationModule] no VUL-84,
 * para que os sete tickets de tela do fluxo 1 acrescentem a sua linha num arquivo só — e
 * num que tem dono. Este módulo não conhece as telas do fluxo 1.
 *
 * O orquestrador é `factory` porque é dono da assinatura de observação da autenticação, que
 * a [AccessViewModel] cancela no `onCleared`.
 */
internal val composePresentationModule = module {
    factoryOf(::AccessOrchestrator) { bind<AccessRuntimeContract>() }
    viewModelOf(::AccessViewModel)
    viewModelOf(::SubscriptionGateViewModel)
}
