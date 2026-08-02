package br.com.saqz.groups.presentation.details

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

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
    val venue: VenueUi? = null,
    val latestNotice: NoticeUi? = null,
    val memberPreview: List<MemberPreviewUi> = emptyList(),
    val memberCount: Int = 0,
    val scheduleSummary: String? = null,
)

/** Nome, linha de resumo e — só no 2e — os chips de bairro/modalidade/agenda. */
@Immutable
data class GroupHeaderUi(
    val name: String,
    val subtitle: String,
    val summaryChips: List<GroupSummaryChipUi> = emptyList(),
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
    val date: String,
    val venue: String,
    val deadline: String,
    val confirmedSummary: String,
    val confirmedNames: List<String> = emptyList(),
)

/** Os contadores 9 / 3 / 4 do 2f, com o título da própria linha e o "9/12" azul. */
@Immutable
data class AttendanceSummaryUi(
    val title: String,
    val ratio: String,
    val going: Int,
    val maybe: Int,
    val pending: Int,
)

/** A linha de caixa do 2f — saldo e mensalidades já num texto só. */
@Immutable
data class CashboxUi(val summary: String)

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

    data class OpenCashbox(val groupId: String) : GroupDetailsEffect

    data class OpenInviteLink(val groupId: String) : GroupDetailsEffect

    data object OpenMap : GroupDetailsEffect

    data object Left : GroupDetailsEffect
}
