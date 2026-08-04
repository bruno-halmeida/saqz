package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.runtime.Immutable

@Immutable
data class GroupCashboxState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val groupName: String = "",
    val monthKey: String = "",
    val monthLabel: String = "",
    val monthlyMembersLabel: String = "",
    val balanceLabel: String = "",
    val receivedLabel: String = "",
    val openLabel: String = "",
    val expensesLabel: String = "",
    val monthlyProgressLabel: String = "",
    val monthlyProgress: Float = 0f,
    val balanceCents: Long = 0L,
    val receivedCents: Long = 0L,
    val openCents: Long = 0L,
    val expensesCents: Long = 0L,
    val paidMonthlyCount: Int = 0,
    val openMonthlyCount: Int = 0,
    val monthlyTotalCount: Int = 0,
    val cashboxEmpty: Boolean = false,
    val overdueBanner: OverdueBannerUi? = null,
    val debtors: List<DebtorUi> = emptyList(),
    val pix: PixUi? = null,
    val updatingChargeId: String? = null,
    val operationFailed: Boolean = false,
)

@Immutable
data class DebtorUi(
    val chargeId: String,
    val memberId: String,
    val name: String,
    val dueLabel: String,
    val amountLabel: String,
    val amountCents: Long,
    val chargeVersion: Long,
    val month: String?,
    val isOverdue: Boolean = false,
    val isUpdating: Boolean = false,
)

@Immutable
data class OverdueBannerUi(
    val message: String,
    val monthLabel: String?,
)

@Immutable
data class PixUi(
    val key: String,
    val label: String?,
)

sealed interface GroupCashboxIntent {
    data object Retry : GroupCashboxIntent
    data object ChargeMissing : GroupCashboxIntent
    data object Register : GroupCashboxIntent
    data object ViewFullStatement : GroupCashboxIntent
    data class MarkReceived(val chargeId: String) : GroupCashboxIntent
    data object CopyPix : GroupCashboxIntent
}

sealed interface GroupCashboxEffect {
    data class OpenStatement(val groupId: String) : GroupCashboxEffect
    data class CopyPix(val key: String) : GroupCashboxEffect
    data object MutationSucceeded : GroupCashboxEffect
}
