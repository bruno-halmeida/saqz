package br.com.saqz.groups.presentation.ui.finance.settlement

import br.com.saqz.groups.presentation.photo.MemberPhoto
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
    val gameLocalDate: String = "",
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
    val diarists: List<GameSettlementDiaristUi> = emptyList(),
    val debtors: List<DebtorUi> = emptyList(),
    val pix: PixUi? = null,
    /** DEB-151: o Pix do grupo acabou de ser copiado; a UI mostra o aviso e manda `DismissPixCopied`. */
    val pixCopied: Boolean = false,
    val updatingChargeId: String? = null,
    val operationFailed: Boolean = false,
    val chargeSheetOpen: Boolean = false,
    val chargeSheetChargeId: String? = null,
    val receiptSheetChargeId: String? = null,
    val waiveSheetChargeId: String? = null,
    val waiveFailed: Boolean = false,
    val cancelSheetChargeId: String? = null,
    val cancelFailed: Boolean = false,
    val reviewSheetChargeId: String? = null,
    val reviewFailed: Boolean = false,
    val undoSheetChargeId: String? = null,
    val undoFailed: Boolean = false,
    /** FIN-016: quem jogou sem diária neste jogo (a decidir e "Não cobrar"). */
    val uncharged: List<UnchargedRowUi> = emptyList(),
    /** O jogo não tinha valor por jogo: a faixa diz isso. */
    val gameHadNoFee: Boolean = false,
    /** O ⋮ de uma diária: Cobrar, Dispensar, Cancelar ou Desfazer. */
    val menuChargeId: String? = null,
) {
    /** Quem jogou (FIN-007): fora quem ficou na espera e quem desistiu, que vão para "Não jogaram". */
    val played: List<GameSettlementDiaristUi> get() = diarists.filter { it.notPlayed == null }
    val notPlayed: List<GameSettlementDiaristUi> get() = diarists.filter { it.notPlayed != null }
    val playedPaidCount: Int get() = played.count { it.status == ChargeStatus.Paid }
    val playedChargeCount: Int get() = played.count { it.status == ChargeStatus.Paid || it.status == ChargeStatus.Pending }
    val playedPendingCents: Long get() = played.filter { it.status == ChargeStatus.Pending }.sumOf { it.amountCents }
    val playedDebtors: List<DebtorUi> get() = debtors.filter { debtor -> played.any { it.chargeId == debtor.chargeId } }

    /** Quantos ainda estão a decidir na revisão: é o número da faixa. */
    val unchargedPending: Int get() = uncharged.count { !it.skipped }

    val isSummary: Boolean
        get() = !isLoading && !loadFailed && updatingChargeId == null && pendingDiaristCount == 0
}

@Immutable
data class GameSettlementHeaderUi(
    val dateTime: String,
    val venue: String,
    val playersCount: Int,
    /** "Terça, 27/10". */
    val title: String = dateTime,
    /** "19h30 · CERET — Quadra 2". */
    val meta: String = venue,
    val completed: Boolean = false,
)

/** Por que uma diária ficou em "Não jogaram": na espera antes do início, não liberado depois, ou desistiu. */
enum class NotPlayedReason { Waiting, NotReleased, Withdrew }

/** Uma pessoa da seção "Sem cobrança" (FIN-016). */
@Immutable
data class UnchargedRowUi(
    val key: String,
    val name: String,
    val meta: String,
    val noShow: Boolean,
    val skipped: Boolean,
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
    val withdrew: Boolean = false,
    /** PRES-016: marcado como falta e a diária segue pendente; ganha "Dispensar" como quem desistiu. */
    val noShow: Boolean = false,
    /** Jogo cancelado com a cobrança já paga ou dispensada: o organizador confere se devolve (DEB-053). */
    val reviewRequired: Boolean = false,
    /** Recebida ou dispensada sem ordem eletrônica: o organizador pode desfazer a baixa (DEB-052). */
    val canUndo: Boolean = false,
    /** Foto de perfil (MEM-006); cobrança de convidado não tem. */
    val photo: MemberPhoto? = null,
    /** FIN-014: recebida pelo app, separada do "você marcou". */
    val appMethod: br.com.saqz.groups.domain.finance.AppPaymentMethod? = null,
    /** DEB-150: na espera ou desistiu; `null` = jogou. */
    val notPlayed: NotPlayedReason? = null,
    /** "Avulso · Pix", "Convite · paga Bia". */
    val cashMeta: String = meta,
) {
    /** Quem desistiu, faltou ou não foi liberado: a diária pendente pode ser dispensada. */
    val waivable: Boolean get() = (withdrew || noShow || notPlayed != null) && status == ChargeStatus.Pending
}

sealed interface GameSettlementIntent {
    data object Retry : GameSettlementIntent

    /** Volta à tela: recarga por baixo, sem esqueleto nem tela de erro. */
    data object Refresh : GameSettlementIntent
    data object ChargeMissing : GameSettlementIntent
    data class ChargeIndividual(val chargeId: String) : GameSettlementIntent
    data class OpenReceipt(val chargeId: String) : GameSettlementIntent
    data class MarkReceived(val chargeId: String, val paidMethod: br.com.saqz.groups.domain.finance.PaidMethod) : GameSettlementIntent
    data class OpenWaive(val chargeId: String) : GameSettlementIntent
    data class Waive(val chargeId: String) : GameSettlementIntent
    data object DismissWaiveSheet : GameSettlementIntent

    /** DEB-062: dono e admin cancelam qualquer cobrança pendente; cancelada não volta. */
    data class OpenCancel(val chargeId: String) : GameSettlementIntent
    data class Cancel(val chargeId: String) : GameSettlementIntent
    data object DismissCancelSheet : GameSettlementIntent
    data class OpenReview(val chargeId: String) : GameSettlementIntent
    data class ResolveReview(val chargeId: String) : GameSettlementIntent
    data object DismissReviewSheet : GameSettlementIntent
    data class OpenUndo(val chargeId: String) : GameSettlementIntent
    data class Undo(val chargeId: String, val note: String = "") : GameSettlementIntent
    data object DismissUndoSheet : GameSettlementIntent
    data object DismissChargeSheet : GameSettlementIntent
    data object DismissReceiptSheet : GameSettlementIntent
    data object CopyPix : GameSettlementIntent
    data object DismissPixCopied : GameSettlementIntent
    data object OpenCourtExpense : GameSettlementIntent
    data object EndSettlement : GameSettlementIntent
    data object OpenCashbox : GameSettlementIntent
    data object ReviewUncharged : GameSettlementIntent
    data class OpenMenu(val chargeId: String) : GameSettlementIntent
    data object DismissMenu : GameSettlementIntent
}

sealed interface GameSettlementEffect {
    data object Closed : GameSettlementEffect
    data class OpenNewEntry(val groupId: String, val localDate: String) : GameSettlementEffect
    data class OpenCashbox(val groupId: String) : GameSettlementEffect
    data class OpenUnchargedReview(val groupId: String, val gameId: String) : GameSettlementEffect
    data class CopyPix(val key: String) : GameSettlementEffect
    data object MutationSucceeded : GameSettlementEffect
}
