package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.onOptions
import org.koin.dsl.module

internal val accessPresentationModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        .onOptions { onClose { scope -> scope?.cancel() } }
    single {
        SessionAccessStateMachine(get(), get(), get(), get()).also { machine ->
            get<DelegatingSessionInvalidator>().delegate = machine
        }
    }
    single {
        AuthenticationStateMachine(get()) { transition ->
            get<SessionAccessStateMachine>().onIntent(SessionIntent.Accept(transition))
        }
    }
}
