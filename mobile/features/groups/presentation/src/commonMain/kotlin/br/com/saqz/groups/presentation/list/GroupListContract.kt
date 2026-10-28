package br.com.saqz.groups.presentation.list

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.GroupUiError

/**
 * 2n — lista de grupos (GER-003). 2o não é tela separada: é [isEmpty], a lista sem grupo.
 */
@Immutable
data class GroupListState(
    val isLoading: Boolean = true,
    /** Puxar para atualizar em andamento (GER-002); os grupos continuam na tela. */
    val isRefreshing: Boolean = false,
    /** O último puxar falhou: a lista fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    /** Já na ordem da tela: pede Confirmar, depois o jogo mais perto, sem jogo no fim ([sortedForList]). */
    val groups: List<GroupCardUi> = emptyList(),
    /** Folha "Tenho um convite" do 2o: aberta, o texto colado e se o último envio não tinha código. */
    val inviteSheetOpen: Boolean = false,
    val inviteLink: String = "",
    val inviteLinkInvalid: Boolean = false,
    /** Grupo cujo "Confirmar" abriu a folha com Vou e Não vou; `null` = folha fechada. */
    val confirmGroupId: String? = null,
    val responding: Boolean = false,
    /** A resposta falhou por erro geral: a folha fica aberta com o aviso da Início. */
    val responseFailed: Boolean = false,
    /** O organizador está sem plano (grupo congelado): o aviso próprio da Início. */
    val responsePlanPaused: Boolean = false,
    /** Sem rede ("Sem conexão. Sua resposta não foi enviada."). */
    val responseOffline: Boolean = false,
) {
    val isEmpty: Boolean = !isLoading && !loadFailed && groups.isEmpty()

    // O "1" de "3 grupos · 1 esperando sua resposta": derivado da lista, nunca campo próprio.
    val awaitingConfirmation: Int = groups.count { it.nextGame?.needsConfirmation == true }

    val confirmGroup: GroupCardUi? = confirmGroupId?.let { id -> groups.firstOrNull { it.id == id } }
}

@Immutable
data class GroupCardUi(
    val id: String,
    val name: String,
    val isAdmin: Boolean,
    val photoUrl: String? = null,
    /**
     * A linha de onde: "CERET — Quadra 2" do jogo mostrado ou, sem jogo, a cidade do grupo. `null` some com a linha.
     */
    val place: String? = null,
    /** `null` pinta "Nenhum jogo marcado por enquanto." no lugar do selo e das vagas. */
    val nextGame: GroupCardGameUi? = null,
)

@Immutable
data class GroupCardGameUi(
    val gameId: String,
    /** O selo: "QUI", "28" e "19h30" já formatados no fuso do jogo (AGENTS.md §8). */
    val weekday: String,
    val day: String,
    val time: String,
    /** Em andamento (só quando o grupo não tem outro jogo): o selo diz "AGORA". */
    val live: Boolean,
    val confirmedCount: Int,
    val capacity: Int,
    /** "13 confirmados para 12 vagas": só acima da lotação, no lugar de "N de M vão" e das vagas abertas. */
    val overCapacityLabel: String? = null,
    val attendance: GroupCardAttendance,
    /** Início do jogo em segundos, para a ordem da lista. */
    val startsAtEpochSeconds: Long,
) {
    val needsConfirmation: Boolean = attendance == GroupCardAttendance.Pending
    val openSpots: Int = (capacity - confirmedCount).coerceAtLeast(0)
}

enum class GroupCardAttendance { Pending, Going, Waitlisted, Out }

sealed interface GroupListIntent {
    data class OpenGroup(val id: String) : GroupListIntent

    data object CreateGroup : GroupListIntent

    data object Retry : GroupListIntent

    /** Recarrega a lista sem esqueleto, mantendo os grupos na tela enquanto busca. */
    data object Refresh : GroupListIntent

    /** Puxar para atualizar (GER-002): como [Refresh], mas mostra a bola e avisa se falhar. */
    data object PullRefresh : GroupListIntent

    data object DismissRefreshFailed : GroupListIntent

    /**
     * A lista apareceu: voltou de outra tela, da troca de aba ou do segundo plano. A primeira
     * aparição é a carga do `init`; toda volta depois recarrega sem esqueleto.
     */
    data object Appeared : GroupListIntent

    // 2o · "Tenho um convite": a folha para colar o link, o mesmo caminho da Início sem grupo.
    data object OpenInviteSheet : GroupListInviteSheetIntent
    data object CloseInviteSheet : GroupListInviteSheetIntent
    data class InviteLinkChanged(val value: String) : GroupListInviteSheetIntent
    data object SubmitInviteLink : GroupListInviteSheetIntent

    // "Confirmar" na foto: a folha com Vou e Não vou (GER-003).
    data class OpenConfirm(val groupId: String) : GroupListConfirmIntent
    data object CloseConfirm : GroupListConfirmIntent
    data class Respond(val intent: AttendanceIntent) : GroupListConfirmIntent
}

/** Os intents da folha "Tenho um convite", agrupados para a ViewModel tratá-los num bloco só. */
sealed interface GroupListInviteSheetIntent : GroupListIntent

/** Os intents da folha do "Confirmar", pelo mesmo motivo. */
sealed interface GroupListConfirmIntent : GroupListIntent

sealed interface GroupListEffect {
    data class OpenGroup(val id: String) : GroupListEffect

    /**
     * Sem plano entitulador (ou limite atingido): o "+" de 2n abre o Fluxo 8 · Planos e o
     * app só volta para o formulário 2a com o plano ativo. Com plano e vaga de grupo o "+"
     * emite [OpenCreateGroup] — o Fluxo 8 fica para quem não pode criar.
     */
    data object OpenPlans : GroupListEffect

    /** Plano ativo com vaga de grupo: o "+" de 2n atalha para o formulário 2a. */
    data object OpenCreateGroup : GroupListEffect

    /** Código lido de um link colado; o fecho entrega ao `GroupInviteCoordinator`, como um deep link. */
    data class AcceptInviteCode(val code: String) : GroupListEffect

    /** "Vou" em jogo com vagas por posição: abre a escolha da posição, como na Início (DEB-107); nada é enviado antes. */
    data class PickPosition(val groupId: String, val gameId: String) : GroupListEffect
}
