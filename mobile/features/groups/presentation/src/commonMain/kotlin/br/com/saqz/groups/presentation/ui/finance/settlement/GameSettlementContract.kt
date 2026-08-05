package br.com.saqz.groups.presentation.ui.finance.settlement

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi

@Immutable
data class GameSettlementState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val groupId: String = "",
    val groupName: String = "",
    val header: GameSettlementHeaderUi? = null,
    val monthlyMemberCount: Int = 0,
    val paidDiaristCount: Int = 0,
    val totalDiaristCount: Int = 0,
    val pendingDiaristCount: Int = 0,
    val progress: Float = 0f,
    val totalDiaristCents: Long = 0L,
    val unitDiaristCents: Long = 0L,
    val receivedDiaristCents: Long = 0L,
    val pendingDiaristCents: Long = 0L,
    val costCents: Long = 0L,
    val resultCents: Long = 0L,
    val diarists: List<GameSettlementDiaristUi> = emptyList(),
    val debtors: List<DebtorUi> = emptyList(),
    val pix: PixUi? = null,
    val updatingChargeId: String? = null,
    val operationFailed: Boolean = false,
    val chargeSheetOpen: Boolean = false,
    val chargeSheetChargeId: String? = null,
    val receiptSheetChargeId: String? = null,
) {
    val isSummary: Boolean
        get() = !isLoading && pendingDiaristCount == 0
}

@Immutable
data class GameSettlementHeaderUi(
    val dateTime: String,
    val venue: String,
    val playersCount: Int,
)

@Immutable
data class GameSettlementDiaristUi(
    val chargeId: String,
    val memberId: String,
    val name: String,
    val meta: String,
    val amountLabel: String,
    val amountCents: Long,
    val dueDate: String,
    val chargeVersion: Long,
    val status: ChargeStatus,
    val referenceLabel: String,
    val isUpdating: Boolean = false,
)

sealed interface GameSettlementIntent {
    data object Retry : GameSettlementIntent
    data object ChargeMissing : GameSettlementIntent
    data class ChargeIndividual(val chargeId: String) : GameSettlementIntent
    data class OpenReceipt(val chargeId: String) : GameSettlementIntent
    data class MarkReceived(val chargeId: String, val paidMethod: br.com.saqz.groups.domain.finance.PaidMethod) : GameSettlementIntent
    data object DismissChargeSheet : GameSettlementIntent
    data object DismissReceiptSheet : GameSettlementIntent
    data object CopyPix : GameSettlementIntent
    data object OpenCourtExpense : GameSettlementIntent
    data object EndSettlement : GameSettlementIntent
    data object OpenCashbox : GameSettlementIntent
}

sealed interface GameSettlementEffect {
    data class OpenNewEntry(val groupId: String) : GameSettlementEffect
    data class OpenCashbox(val groupId: String) : GameSettlementEffect
    data class CopyPix(val key: String) : GameSettlementEffect
}
