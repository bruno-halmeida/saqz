package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.presentation.pix.PixKeys
import br.com.saqz.groups.domain.finance.PaidMethod

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
    val chargeSheetOpen: Boolean = false,
    val chargeSheetChargeId: String? = null,
    val receiptSheetChargeId: String? = null,
    val waiveSheetChargeId: String? = null,
    val waiveFailed: Boolean = false,
    val cancelSheetChargeId: String? = null,
    val cancelFailed: Boolean = false,
    val reviews: List<ReviewUi> = emptyList(),
    val reviewSheetChargeId: String? = null,
    val reviewFailed: Boolean = false,
    val settled: List<SettledUi> = emptyList(),
    val undoSheetChargeId: String? = null,
    val undoFailed: Boolean = false,
) {
    /** Há cobrança pendente e o grupo não tem chave Pix: ninguém sabe como pagar (DEB-058). */
    val pixKeyNotice: Boolean
        get() = !isLoading && !loadFailed && debtors.isNotEmpty() && pix == null
}

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
    val referenceLabel: String = "",
    /** Desistiu depois de confirmar e a diária segue pendente: o organizador cobra ou dispensa. */
    val withdrew: Boolean = false,
)

/** Cobrança de jogo cancelado que já estava paga ou dispensada: o organizador confere a devolução (DEB-053). */
@Immutable
data class ReviewUi(
    val chargeId: String,
    val name: String,
    val referenceLabel: String,
    val amountLabel: String,
    val chargeVersion: Long,
    val isUpdating: Boolean = false,
)

/** Mensalidade do mês já recebida ou dispensada, sem ordem eletrônica: o organizador pode desfazer a baixa (DEB-052). */
@Immutable
data class SettledUi(
    val chargeId: String,
    val name: String,
    val referenceLabel: String,
    val amountLabel: String,
    val chargeVersion: Long,
    val received: Boolean,
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
) {
    /** O tipo da chave quando o `pixLabel` guardado é um dos quatro; texto livre de grupo antigo dá `null`. */
    val type: PixKeyType? get() = PixKeyType.fromLabel(label)

    /** A chave como se mostra (CPF e telefone com máscara). O que se copia é sempre [key], como guardada. */
    val displayKey: String get() = type?.let { PixKeys.format(it, key) } ?: key
}

sealed interface GroupCashboxIntent {
    data object Retry : GroupCashboxIntent

    /** Volta à tela: recarga por baixo, sem esqueleto nem tela de erro. */
    data object Refresh : GroupCashboxIntent
    data object ChargeMissing : GroupCashboxIntent
    data class ChargeIndividual(val chargeId: String) : GroupCashboxIntent
    data object Register : GroupCashboxIntent
    data object GenerateMonthly : GroupCashboxIntent
    data object ViewFullStatement : GroupCashboxIntent
    data class OpenReceipt(val chargeId: String) : GroupCashboxIntent
    data class MarkReceived(
        val chargeId: String,
        val paidMethod: PaidMethod = PaidMethod.Pix,
    ) : GroupCashboxIntent
    data class OpenWaive(val chargeId: String) : GroupCashboxIntent
    data class Waive(val chargeId: String) : GroupCashboxIntent
    data object DismissWaiveSheet : GroupCashboxIntent

    /** DEB-062: dono e admin cancelam qualquer cobrança pendente; cancelada não volta. */
    data class OpenCancel(val chargeId: String) : GroupCashboxIntent
    data class Cancel(val chargeId: String) : GroupCashboxIntent
    data object DismissCancelSheet : GroupCashboxIntent
    data class OpenReview(val chargeId: String) : GroupCashboxIntent
    data class ResolveReview(val chargeId: String) : GroupCashboxIntent
    data object DismissReviewSheet : GroupCashboxIntent
    data class OpenUndo(val chargeId: String) : GroupCashboxIntent
    data class Undo(val chargeId: String, val note: String = "") : GroupCashboxIntent
    data object DismissUndoSheet : GroupCashboxIntent
    data object DismissChargeSheet : GroupCashboxIntent
    data object DismissReceiptSheet : GroupCashboxIntent
    data object CopyPix : GroupCashboxIntent
    data object OpenPixSetup : GroupCashboxIntent
}

sealed interface GroupCashboxEffect {
    data class OpenStatement(val groupId: String) : GroupCashboxEffect
    data class OpenNewEntry(val groupId: String) : GroupCashboxEffect
    data class OpenGroupSettings(val groupId: String) : GroupCashboxEffect
    data class OpenMonthlyGeneration(val groupId: String) : GroupCashboxEffect
    data class CopyPix(val key: String) : GroupCashboxEffect
    data object MutationSucceeded : GroupCashboxEffect
}
