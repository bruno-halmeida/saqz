package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.domain.StoreLaunchPolicy
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.receivables.presentation.ChargeApprovalRoute
import br.com.saqz.receivables.presentation.FinancialManagementRoute
import br.com.saqz.receivables.presentation.FinancialOnboardingRoute
import br.com.saqz.receivables.presentation.MemberPaymentHistoryRoute
import br.com.saqz.receivables.presentation.MemberPaymentRoute
import br.com.saqz.receivables.presentation.ReceiptConfigurationRoute
import br.com.saqz.receivables.presentation.ReceiptFinanceHomeRoute
import br.com.saqz.receivables.presentation.ReceiptWalletRoute
import br.com.saqz.receivables.presentation.RecurrenceRoute
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute

/** Applied before composing destinations, including restored stacks and incoming links. */
internal fun NavKey.isAvailableAtLaunch(): Boolean = when (this) {
    SubscriptionsRoute.ChangePlan -> StoreLaunchPolicy.purchases
    is GroupsRoute.Thread -> notices || StoreLaunchPolicy.chat
    ReceiptFinanceHomeRoute, FinancialManagementRoute, FinancialOnboardingRoute,
    ReceiptWalletRoute, MemberPaymentHistoryRoute, is MemberPaymentRoute,
    is RecurrenceRoute, is ChargeApprovalRoute, is ReceiptConfigurationRoute -> StoreLaunchPolicy.receivables
    else -> true
}
