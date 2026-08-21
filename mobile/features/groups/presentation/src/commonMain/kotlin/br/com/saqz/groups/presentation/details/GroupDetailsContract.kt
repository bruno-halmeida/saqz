package br.com.saqz.groups.presentation.details

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi

/**
 * 2e e 2f do fluxo 2 — uma tela, duas visões. `isAdmin` não esconde um chip: ele troca
 * quais seções existem. O que a visão não tem chega `null`/vazio e o `Screen` não empilha.
 *
 * Todo valor exibido já vem formatado como `String` (AGENTS.md §8): a camada que carrega
 * decide "Ter, 28/07 · 19h30", não o composable.
 */
@Immutable
data class GroupDetailsState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val isAdmin: Boolean = false,
    val isOwner: Boolean = false,
    val header: GroupHeaderUi? = null,
    val nextGame: NextGameUi? = null,
    val attendance: AttendanceSummaryUi? = null,
    val cashbox: CashboxUi? = null,
    val ownCharges: OwnChargesUi? = null,
    val venue: VenueUi? = null,
    val latestNotice: NoticeUi? = null,
    val memberPreview: List<MemberPreviewUi> = emptyList(),
    val memberCount: Int = 0,
    val scheduleSummary: String? = null,
    val memberResponse: GroupDetailsResponseUi? = null,
    val responding: Boolean = false,
    val responseFailed: Boolean = false,
    val rosterStale: Boolean = false,
    val rosterRefreshing: Boolean = false,
    val membershipType: AthleteMembershipType? = null,
    val autoConfirmationVisible: Boolean = false,
    val autoConfirmationEnabled: Boolean = false,
    val autoConfirmationUpdating: Boolean = false,
    val autoConfirmationFailed: Boolean = false,
)

/** Nome, linha de resumo e — só no 2e — os chips de bairro/modalidade/agenda. */
@Immutable
data class GroupHeaderUi(
    val name: String,
    val subtitle: String,
    val summaryChips: List<GroupSummaryChipUi> = emptyList(),
    val photoUrl: String? = null,
)

/**
 * [highlighted] é o chip azul do export ("Terças e quintas"); os outros dois são
 * neutros. A visão não conhece `SaqzChipTone`, só quem desenha.
 */
@Immutable
data class GroupSummaryChipUi(val text: String, val highlighted: Boolean = false)

/** O card de destaque do 2e: data grande, local numa linha, pilha de avatares. */
@Immutable
data class NextGameUi(
    val gameId: String = "",
    val date: String,
    val venue: String,
    val deadline: String,
    val confirmationDeadline: String = "",
    val confirmedCount: Int,
    val capacity: Int,
    val confirmedNames: List<String> = emptyList(),
    val availableSpots: Int = 0,
    val confirmationOpen: Boolean = true,
    val hasGameFee: Boolean = false,
)

/** Os contadores 9 / 3 / 4 do 2f, com o título da própria linha e o "9/12" azul. */
@Immutable
data class AttendanceSummaryUi(
    val confirmedCount: Int,
    val capacity: Int,
    val going: Int,
    val notGoing: Int,
    val pending: Int,
    val availableSpots: Int = 0,
)

@Immutable
data class GroupDetailsResponseUi(
    val status: GroupDetailsResponseStatus,
    val waitlistPosition: Long? = null,
    val memberId: String? = null,
)

enum class GroupDetailsResponseStatus { Confirmed, Declined, Waitlisted }

/** A linha de caixa do 2f — saldo e mensalidades já num texto só. */
@Immutable
data class CashboxUi(val summary: String? = null)

/**
 * VUL-203 — as cobranças **do próprio usuário** naquele grupo (`ownCharges`). Nunca as de
 * terceiro: o caixa completo continua sendo o caminho de admin.
 *
 * O estado é nulo quando não há nada a mostrar — usuário sem cobrança alguma no grupo —, e
 * a seção some. [isLoading] e [failed] são locais à seção de propósito: nem o skeleton nem
 * o erro dela derrubam o resto da tela do grupo.
 *
 * [pix] só chega preenchido quando existe pendência: sem dívida, o card de copiar a chave
 * seria um convite a pagar o que já está pago.
 */
@Immutable
data class OwnChargesUi(
    val pending: List<OwnChargeUi> = emptyList(),
    val history: List<OwnChargeUi> = emptyList(),
    val pix: PixUi? = null,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
)

@Immutable
data class OwnChargeUi(
    val id: String,
    val title: String,
    val dueLabel: String,
    val amountLabel: String,
    val status: OwnChargeStatusUi,
)

enum class OwnChargeStatusUi { Pending, Paid, Waived, Cancelled }

@Immutable
data class VenueUi(val name: String, val address: String)

/** O aviso recente do 2e. [authorIsAdmin] acende o "(Admin)" azul ao lado do autor. */
@Immutable
data class NoticeUi(
    val author: String,
    val authorIsAdmin: Boolean,
    val body: String,
    val timestamp: String,
)

@Immutable
data class MemberPreviewUi(
    val id: String,
    val name: String,
    val meta: String,
    val status: MemberStatusUi? = null,
)

/** Os três tons de etiqueta que as linhas de membro do 2e usam. */
enum class MemberStatusUi { Admin, Going, Maybe }

sealed interface GroupDetailsIntent {
    data object Retry : GroupDetailsIntent

    // 2f — cabeçalho e gerenciamento
    data object CreateNextGame : GroupDetailsIntent

    data object EditGroup : GroupDetailsIntent

    data object NotifyPending : GroupDetailsIntent

    data object EditVenue : GroupDetailsIntent

    data object ManageMembers : GroupDetailsIntent

    data object ManageSchedule : GroupDetailsIntent

    data object InviteByLink : GroupDetailsIntent

    // 2e — jogo, atalhos, membros e rodapé
    data object ConfirmAttendance : GroupDetailsIntent

    data object ViewGame : GroupDetailsIntent

    data object OpenVenueMap : GroupDetailsIntent

    data object OpenNotices : GroupDetailsIntent

    data object OpenChat : GroupDetailsIntent

    data object OpenSchedule : GroupDetailsIntent

    data object ViewAllMembers : GroupDetailsIntent

    data object Invite : GroupDetailsIntent

    // Comum às duas visões
    data object OpenCashbox : GroupDetailsIntent

    data object Leave : GroupDetailsIntent

    data object RetryRoster : GroupDetailsIntent

    // VUL-203 — minhas cobranças
    data object RetryOwnCharges : GroupDetailsIntent

    data object CopyPix : GroupDetailsIntent

    data class Respond(val intent: AttendanceIntent) : GroupDetailsIntent

    data class ToggleAutoConfirmation(val enabled: Boolean) : GroupDetailsIntent
}

/**
 * As saídas do fluxo. `OpenCreateGame`, `OpenCashbox` e `OpenInviteLink` apontam para
 * fluxos que ainda não existem (4, 5 e 3) — a tela emite de qualquer jeito, e o ticket
 * de ligação resolve cada um para `TODO`. Botão escondido perderia a informação de para
 * onde ele vai.
 */
sealed interface GroupDetailsEffect {
    data class OpenEdit(val groupId: String) : GroupDetailsEffect

    data class OpenMembers(val groupId: String) : GroupDetailsEffect

    data class OpenSchedule(val groupId: String) : GroupDetailsEffect

    data class OpenCreateGame(val groupId: String) : GroupDetailsEffect

    data class OpenGame(val groupId: String, val gameId: String) : GroupDetailsEffect

    data class OpenCashbox(val groupId: String) : GroupDetailsEffect

    data class OpenInviteLink(val groupId: String) : GroupDetailsEffect

    data object OpenMap : GroupDetailsEffect

    data object Left : GroupDetailsEffect

    /** Copiar a chave Pix é da área de transferência, não do back stack: morre no Root. */
    data class CopyPix(val key: String) : GroupDetailsEffect
}
