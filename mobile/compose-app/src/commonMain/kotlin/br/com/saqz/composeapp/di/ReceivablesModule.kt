package br.com.saqz.composeapp.di

import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.receivables.AutopayOfferMemoryBinding
import br.com.saqz.composeapp.receivables.ReceivablesSessionBinding
import br.com.saqz.composeapp.receivables.SettleLinkInbox
import br.com.saqz.receivables.data.KtorAthletePaymentsGateway
import br.com.saqz.receivables.data.KtorAutopayGateway
import br.com.saqz.receivables.data.KtorFinancialManagementGateway
import br.com.saqz.receivables.data.KtorFinancialOnboardingGateway
import br.com.saqz.receivables.data.KtorGroupDebtorsGateway
import br.com.saqz.receivables.data.KtorGroupPaymentsGateway
import br.com.saqz.receivables.data.KtorGroupReceivablesGateway
import br.com.saqz.receivables.data.KtorOwnChargeHistoryGateway
import br.com.saqz.receivables.data.KtorReceivablesAvailabilityGateway
import br.com.saqz.receivables.data.KtorSettleLinkGateway
import br.com.saqz.receivables.domain.AthletePaymentsGateway
import br.com.saqz.receivables.domain.AutopayGateway
import br.com.saqz.receivables.domain.AutopayOfferMemory
import br.com.saqz.receivables.domain.GroupDebtorsGateway
import br.com.saqz.receivables.domain.GroupPaymentsGateway
import br.com.saqz.receivables.domain.GroupReceivablesGateway
import br.com.saqz.receivables.domain.OwnChargeHistoryGateway
import br.com.saqz.receivables.domain.ReceiptAccountDirectory
import br.com.saqz.receivables.domain.ReceivablesAvailabilityGateway
import br.com.saqz.receivables.domain.ReceivablesRecoveryIdentity
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.receivables.domain.SettleLinkGateway
import br.com.saqz.receivables.presentation.AthleteChargesViewModel
import br.com.saqz.receivables.presentation.AutopayViewModel
import br.com.saqz.receivables.presentation.FeePolicyViewModel
import br.com.saqz.receivables.presentation.FinancialManagementViewModel
import br.com.saqz.receivables.presentation.FinancialOnboardingViewModel
import br.com.saqz.receivables.presentation.GroupReceivablesViewModel
import br.com.saqz.receivables.presentation.PaymentReviewViewModel
import br.com.saqz.receivables.presentation.PaymentViewModel
import br.com.saqz.receivables.presentation.ReceiptConfigurationViewModel
import br.com.saqz.receivables.presentation.ReceiptFinanceHomeViewModel
import br.com.saqz.receivables.presentation.ReceiptWalletViewModel
import br.com.saqz.receivables.presentation.ReceivablesCoordinator
import br.com.saqz.receivables.presentation.SettleLinkViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

internal val receivablesModule = module {
    singleOf(::KtorFinancialManagementGateway) bind br.com.saqz.receivables.domain.FinancialManagementGateway::class
    viewModelOf(::FinancialManagementViewModel)
    single<br.com.saqz.receivables.domain.ReceiptNoticesGateway> { br.com.saqz.receivables.data.KtorReceiptNoticesGateway(get(), get()) }
    viewModelOf(::ReceiptFinanceHomeViewModel)
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
    singleOf(::KtorFinancialOnboardingGateway) bind br.com.saqz.receivables.domain.FinancialOnboardingGateway::class
    viewModelOf(::FinancialOnboardingViewModel)
    viewModelOf(::ReceiptConfigurationViewModel)
    single<kotlin.time.Clock> { kotlin.time.Clock.System }
    // Recebimento pelo app v1 (DEB-141): atleta, gestor e o link da hora do acerto.
    singleOf(::KtorAthletePaymentsGateway) bind AthletePaymentsGateway::class
    singleOf(::KtorOwnChargeHistoryGateway) bind OwnChargeHistoryGateway::class
    singleOf(::KtorAutopayGateway) bind AutopayGateway::class
    singleOf(::KtorSettleLinkGateway) bind SettleLinkGateway::class
    singleOf(::KtorGroupPaymentsGateway) bind GroupPaymentsGateway::class
    singleOf(::KtorGroupDebtorsGateway) bind GroupDebtorsGateway::class
    singleOf(::AutopayOfferMemoryBinding) bind AutopayOfferMemory::class
    singleOf(::SettleLinkInbox)
    viewModelOf(::AthleteChargesViewModel)
    viewModelOf(::PaymentViewModel)
    viewModelOf(::AutopayViewModel)
    viewModelOf(::SettleLinkViewModel)
    viewModelOf(::GroupReceivablesViewModel)
    viewModelOf(::FeePolicyViewModel)
    viewModelOf(::PaymentReviewViewModel)
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
