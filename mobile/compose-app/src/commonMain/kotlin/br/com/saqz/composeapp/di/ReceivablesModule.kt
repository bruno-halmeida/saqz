package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.composeapp.receivables.ReceivablesSessionBinding
import br.com.saqz.receivables.data.KtorReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val receivablesModule = module {
    singleOf(::KtorReceivablesAvailabilityGateway) bind ReceivablesAvailabilityGateway::class
    single<ReceivablesSessionContext> {
        val session = get<SessionAccessStateMachine>()
        ReceivablesSessionContext { (session.state.value as? SessionAccessState.Ready)?.session?.user?.id }
    }
    singleOf(::ReceivablesCoordinator)
    single { ReceivablesSessionBinding(get<SessionAccessStateMachine>().state, get(), get()) }
}
