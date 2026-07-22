package br.com.saqz.composeapp.di

import br.com.saqz.access.port.LocalAccessStatePort
import br.com.saqz.access.port.NativeAuthPort
import br.com.saqz.access.port.NativeLinkPort
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.presentation.SessionIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.createdAtStart
import org.koin.core.module.dsl.onOptions
import org.koin.dsl.module

internal val accessModule = module {
    single<NativeAuthPort> { get<SaqzNativePorts>().auth }
    single<NativeLinkPort> { get<SaqzNativePorts>().links }
    single<LocalAccessStatePort> { get<SaqzNativePorts>().localAccessState }
    single<NativeSharePort> { get<SaqzNativePorts>().share }
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single {
        SessionAccessStateMachine(get(), get(), get(), get()).also { machine ->
            get<DelegatingSessionInvalidator>().delegate = machine
        }
    }.onOptions { createdAtStart() }
    single {
        AuthenticationStateMachine(get()) { transition ->
            get<SessionAccessStateMachine>().onIntent(SessionIntent.Accept(transition))
        }
    }
}
