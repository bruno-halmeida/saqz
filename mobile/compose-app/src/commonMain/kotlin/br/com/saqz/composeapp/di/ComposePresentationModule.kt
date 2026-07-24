package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.login.LoginViewModel
import br.com.saqz.access.presentation.namecompletion.NameCompletionViewModel
import br.com.saqz.access.presentation.phonecompletion.PhoneCompletionViewModel
import br.com.saqz.access.presentation.verification.VerificationViewModel
import br.com.saqz.composeapp.navigation.AccessOrchestrator
import br.com.saqz.composeapp.navigation.AccessRuntimeContract
import br.com.saqz.composeapp.navigation.AccessViewModel
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * C1: the app-shell presentation graph is now the session gate plus the access screens.
 * The orchestrator is a `factory` because it owns the auth-observation subscription that
 * [AccessViewModel] cancels in `onCleared`.
 */
internal val composePresentationModule = module {
    factoryOf(::AccessOrchestrator) { bind<AccessRuntimeContract>() }
    viewModelOf(::AccessViewModel)
    viewModelOf(::LoginViewModel)
    viewModelOf(::VerificationViewModel)
    viewModelOf(::NameCompletionViewModel)
    viewModelOf(::PhoneCompletionViewModel)
}
