package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.composeapp.receivables.ReceivablesSessionBinding
import br.com.saqz.receivables.data.KtorReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

import br.com.saqz.receivables.data.KtorMemberPaymentsGateway
import br.com.saqz.receivables.data.KtorGroupReceivablesGateway
import br.com.saqz.receivables.domain.GroupReceivablesGateway
import br.com.saqz.receivables.domain.ReceiptAccountDirectory
import br.com.saqz.receivables.domain.ReceivablesRecoveryIdentity
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.receivables.presentation.ReceiptConfigurationViewModel
import org.koin.core.module.dsl.viewModelOf

internal val receivablesModule = module {
    singleOf(::KtorReceivablesAvailabilityGateway) bind ReceivablesAvailabilityGateway::class
    single<ReceivablesSessionContext> {
        val session = get<SessionAccessStateMachine>()
        ReceivablesSessionContext { session.activeSessionKey.value }
    }
    singleOf(::KtorGroupReceivablesGateway) bind GroupReceivablesGateway::class bind ReceiptAccountDirectory::class
    singleOf(::KtorMemberPaymentsGateway) bind br.com.saqz.receivables.domain.MemberPaymentsGateway::class
    viewModelOf(::ReceiptConfigurationViewModel)
    single<ReceivablesRecoveryIdentity> {
        val session = get<SessionAccessStateMachine>()
        ReceivablesRecoveryIdentity {
            if (session.activeSessionKey.value == null) null
            else (session.state.value as? SessionAccessState.Ready)?.session?.user?.id
        }
    }
    singleOf(::ReceivablesCoordinator)
    single { ReceivablesSessionBinding(get<SessionAccessStateMachine>().activeSessionKey, get(), get()) }
}
