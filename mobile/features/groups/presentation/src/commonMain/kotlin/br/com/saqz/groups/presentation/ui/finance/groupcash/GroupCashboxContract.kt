package br.com.saqz.groups.presentation.ui.finance.groupcash

import br.com.saqz.groups.presentation.photo.MemberPhoto
import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.pix.PixKeyType
import br.com.saqz.groups.presentation.pix.PixKeys
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort

@Immutable
data class GroupCashboxState(
    val isLoading: Boolean = true,
    /** Puxar para atualizar em andamento (GER-002); o caixa continua na tela. */
    val isRefreshing: Boolean = false,
    /** O último puxar falhou: o caixa fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
    /** DEB-151: o Pix do grupo acabou de ser copiado; a UI mostra o aviso e manda `DismissPixCopied`. */
    val pixCopied: Boolean = false,
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
    /** Caixa novo (FIN-006, DEB-148): a aba do placar que manda na lista de baixo. */
    val tab: CashboxTab = CashboxTab.Due,
    /** Entradas e saídas do mês do placar, do extrato. */
    val monthIn: List<LedgerUi> = emptyList(),
    val monthOut: List<LedgerUi> = emptyList(),
    val monthInLabel: String = "",
    val monthInCents: Long = 0L,
    val monthOutLabel: String = "",
    val dueLabel: String = "",
    /** `null`: ninguém respondeu o saldo inicial (ou o servidor não tem o campo). */
    val openingBalanceCents: Long? = null,
    val openingVersion: String? = null,
    val openingLoaded: Boolean = false,
    /** A pendência "N jogaram sem cobrança" (FIN-016); `null` sem ninguém a decidir. */
    val uncharged: UnchargedPendingUi? = null,
    val menuOpen: Boolean = false,
    val debtorMenuChargeId: String? = null,
    val openingSheetOpen: Boolean = false,
    val openingInput: String = "",
    val openingSaving: Boolean = false,
    val openingInvalid: Boolean = false,
    val openingFailed: Boolean = false,
    /** 409: outra pessoa mudou o saldo inicial; o valor novo já está na tela. */
    val openingConflict: Boolean = false,
) {
    /** Saldo inicial maior que zero: o saldo diz "Inclui o saldo inicial de R$ 250". */
    val openingLabel: String?
        get() = openingBalanceCents?.takeIf { it > 0L }?.let(::formatBrlShort)

    /** Caixa zerado: ninguém respondeu o saldo inicial e nada foi lançado — aparece a pergunta. */
    val askOpening: Boolean
        get() = openingLoaded && openingBalanceCents == null && cashboxEmpty

    val monthlyDebtors: List<DebtorUi> get() = debtors.filter { it.month == monthKey }
    val otherMonthlyDebtors: List<DebtorUi> get() = debtors.filter { it.month != null && it.month != monthKey }
    val gameDebtors: List<DebtorUi> get() = debtors.filter { it.month == null }

    /** Há cobrança pendente e o grupo não tem chave Pix: ninguém sabe como pagar (DEB-058). */
    val pixKeyNotice: Boolean
        get() = !isLoading && !loadFailed && debtors.isNotEmpty() && pix == null
}

enum class CashboxTab { In, Out, Due }

/** Uma linha de entrada ou saída: "Quadra" · "Terça, 27/10" · "−R$ 160". */
@Immutable
data class LedgerUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val amountLabel: String,
    val out: Boolean,
)

/** A faixa do caixa: "6 jogaram sem cobrança · Em 2 jogos de outubro". Um jogo só abre a revisão dele. */
@Immutable
data class UnchargedPendingUi(
    val peopleCount: Int,
    val gamesCount: Int,
    /** "outubro" quando todos os jogos são do mesmo mês; senão `null`. */
    val monthName: String?,
    /** Data do jogo ("20/10") quando há um jogo só. */
    val singleGameDate: String?,
    val singleGameId: String?,
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
    val referenceLabel: String = "",
    /** Desistiu depois de confirmar e a diária segue pendente: o organizador cobra ou dispensa. */
    val withdrew: Boolean = false,
    /** Foto de perfil de quem deve (MEM-006); convidado não tem. */
    val photo: MemberPhoto? = null,
    /** PRES-016: faltou sem avisar e a diária segue pendente; mesmo "Dispensar" de quem desistiu. */
    val noShow: Boolean = false,
    /** Linha de baixo do caixa novo: "Venceu 10/10 · R$ 80", "Terça, 27/10 · R$ 20", "Convidada Júlia · R$ 20". */
    val cashMeta: String = "",
    val dueDate: String = "",
) {
    val waivable: Boolean get() = withdrew || noShow
}

/** Cobrança de jogo cancelado que já estava paga ou dispensada: o organizador confere a devolução (DEB-053). */
@Immutable
data class ReviewUi(
    val chargeId: String,
    val name: String,
    val referenceLabel: String,
    val amountLabel: String,
    val chargeVersion: Long,
    val isUpdating: Boolean = false,
    val photo: MemberPhoto? = null,
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
    val photo: MemberPhoto? = null,
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

    /** Puxar para atualizar (GER-002): recarga por baixo com a bola na tela e aviso se falhar. */
    data object PullRefresh : GroupCashboxIntent

    data object DismissRefreshFailed : GroupCashboxIntent

    data object DismissPixCopied : GroupCashboxIntent
    data object ChargeMissing : GroupCashboxIntent
    data class ChargeIndividual(val chargeId: String) : GroupCashboxIntent
    data object Register : GroupCashboxIntent
    data object NewIncome : GroupCashboxIntent
    data object NewExpense : GroupCashboxIntent
    data class SelectTab(val tab: CashboxTab) : GroupCashboxIntent
    data object OpenMenu : GroupCashboxIntent
    data object DismissMenu : GroupCashboxIntent
    data class OpenDebtorMenu(val chargeId: String) : GroupCashboxIntent
    data object DismissDebtorMenu : GroupCashboxIntent
    data object OpenOpeningBalance : GroupCashboxIntent
    data object DismissOpeningBalance : GroupCashboxIntent
    data class OpeningInputChanged(val value: String) : GroupCashboxIntent
    data object SaveOpeningBalance : GroupCashboxIntent

    /** "Começar do zero": grava R$ 0 e fecha a pergunta. */
    data object StartFromZero : GroupCashboxIntent
    data object ReviewUncharged : GroupCashboxIntent
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
    data class OpenNewEntry(val groupId: String, val out: Boolean = false) : GroupCashboxEffect
    data class OpenUnchargedReview(val groupId: String, val gameId: String) : GroupCashboxEffect
    data class OpenUnchargedGames(val groupId: String) : GroupCashboxEffect
    data class OpenGroupSettings(val groupId: String) : GroupCashboxEffect
    data class OpenMonthlyGeneration(val groupId: String) : GroupCashboxEffect
    data class CopyPix(val key: String) : GroupCashboxEffect
    data object MutationSucceeded : GroupCashboxEffect
}
