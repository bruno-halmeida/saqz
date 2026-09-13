package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.composeapp.receivables.ReceivablesSessionBinding
import br.com.saqz.receivables.data.KtorReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import br.com.saqz.receivables.data.KtorFinancialManagementGateway
import br.com.saqz.receivables.presentation.FinancialManagementViewModel
import br.com.saqz.receivables.data.KtorRecurrenceGateway
import br.com.saqz.receivables.data.KtorPixRenewalGateway
import br.com.saqz.receivables.presentation.RecurrenceViewModel
import org.koin.dsl.module

import br.com.saqz.receivables.data.KtorFinancialOnboardingGateway
import br.com.saqz.receivables.presentation.ReceiptFinanceHomeViewModel
import br.com.saqz.receivables.presentation.ReceiptWalletViewModel
import br.com.saqz.receivables.presentation.FinancialOnboardingViewModel
import br.com.saqz.receivables.data.KtorChargeApprovalGateway
import br.com.saqz.receivables.presentation.ChargeApprovalViewModel
import br.com.saqz.receivables.data.KtorMemberPaymentsGateway
import br.com.saqz.receivables.data.KtorGroupReceivablesGateway
import br.com.saqz.receivables.domain.GroupReceivablesGateway
import br.com.saqz.receivables.domain.ReceiptAccountDirectory
import br.com.saqz.receivables.domain.ReceivablesRecoveryIdentity
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.receivables.presentation.ReceiptConfigurationViewModel
import br.com.saqz.receivables.presentation.MemberPaymentRuntime
import br.com.saqz.receivables.presentation.MemberPaymentViewModel
import br.com.saqz.receivables.presentation.MemberPaymentHistoryViewModel
import org.koin.core.module.dsl.viewModelOf

internal val receivablesModule = module {
    singleOf(::KtorFinancialManagementGateway) bind br.com.saqz.receivables.domain.FinancialManagementGateway::class
    viewModelOf(::FinancialManagementViewModel)
    singleOf(::KtorRecurrenceGateway) bind br.com.saqz.receivables.domain.RecurrenceGateway::class
    singleOf(::KtorPixRenewalGateway) bind br.com.saqz.receivables.domain.PixRenewalGateway::class
    viewModelOf(::RecurrenceViewModel)
    single<br.com.saqz.receivables.domain.ReceiptNoticesGateway> { br.com.saqz.receivables.data.KtorReceiptNoticesGateway(get(), get()) }
    viewModelOf(::ReceiptFinanceHomeViewModel)
    single<br.com.saqz.receivables.domain.ReceiptExportPort> {
        br.com.saqz.composeapp.receivables.ReceiptExportBinding(get())
    }
    single<br.com.saqz.receivables.domain.ReceiptWalletGateway> { br.com.saqz.receivables.data.KtorReceiptWalletGateway(get()) }
    viewModelOf(::ReceiptWalletViewModel)
    single<br.com.saqz.receivables.domain.port.ReceiptReauthenticationPort> {
        br.com.saqz.composeapp.receivables.ReceiptReauthenticationBinding(get())
    }
    singleOf(::KtorReceivablesAvailabilityGateway) bind ReceivablesAvailabilityGateway::class
    single<ReceivablesSessionContext> {
        val session = get<SessionAccessStateMachine>()
        ReceivablesSessionContext { session.activeSessionKey.value }
    }
    singleOf(::KtorGroupReceivablesGateway) bind GroupReceivablesGateway::class bind ReceiptAccountDirectory::class
    singleOf(::KtorMemberPaymentsGateway) bind br.com.saqz.receivables.domain.MemberPaymentsGateway::class
    singleOf(::KtorChargeApprovalGateway) bind br.com.saqz.receivables.domain.ChargeApprovalGateway::class
    singleOf(::KtorFinancialOnboardingGateway) bind br.com.saqz.receivables.domain.FinancialOnboardingGateway::class
    viewModelOf(::FinancialOnboardingViewModel)
    viewModelOf(::ChargeApprovalViewModel)
    viewModelOf(::ReceiptConfigurationViewModel)
    single<kotlin.time.Clock> { kotlin.time.Clock.System }
    viewModelOf(::MemberPaymentHistoryViewModel)
    singleOf(::MemberPaymentRuntime)
    viewModelOf(::MemberPaymentViewModel)
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
