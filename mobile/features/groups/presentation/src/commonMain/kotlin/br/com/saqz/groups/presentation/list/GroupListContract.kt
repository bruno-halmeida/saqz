package br.com.saqz.groups.presentation.list

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.model.GroupModality

/**
 * 2n — lista de grupos. 2o não é tela separada: é [isEmpty], a lista sem grupo e sem convite.
 */
@Immutable
data class GroupListState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val groups: List<GroupCardUi> = emptyList(),
    val invite: GroupInviteUi? = null,
) {
    val isEmpty: Boolean = !isLoading && !loadFailed && groups.isEmpty() && invite == null

    // O "1" de "3 grupos · 1 pedindo confirmação": derivado da lista, nunca campo próprio.
    val awaitingConfirmation: Int = groups.count { it.nextGame?.needsConfirmation == true }
}

@Immutable
data class GroupCardUi(
    val id: String,
    val name: String,
    /** "Quadra · Tatuapé · 26 membros" — montado antes de chegar ao estado. */
    val meta: String,
    val modality: GroupModality,
    val isAdmin: Boolean,
    val photoUrl: String? = null,
    /** `null` pinta "Nenhum jogo marcado por enquanto." no lugar da linha do jogo. */
    val nextGame: GroupCardGameUi? = null,
)

@Immutable
data class GroupCardGameUi(
    /** "Ter, 28/07 · 19h30 · 9 de 12" — data já formatada é String no estado (AGENTS.md §8). */
    val label: String,
    val attendance: GroupCardAttendance,
) {
    val needsConfirmation: Boolean = attendance == GroupCardAttendance.Pending
}

enum class GroupCardAttendance { Pending, Going, Maybe, Out }

@Immutable
data class GroupInviteUi(val id: String, val groupName: String, val invitedBy: String)

sealed interface GroupListIntent {
    data class OpenGroup(val id: String) : GroupListIntent

    data object CreateGroup : GroupListIntent

    data object JoinWithCode : GroupListIntent

    data class AcceptInvite(val id: String) : GroupListIntent

    data class DeclineInvite(val id: String) : GroupListIntent

    data object Retry : GroupListIntent
}

sealed interface GroupListEffect {
    data class OpenGroup(val id: String) : GroupListEffect

    /**
     * Criar grupo exige plano: o "+" de 2n abre o Fluxo 8 · Planos e o app só volta para o
     * formulário 2a com o plano ativo. Atalhar para `GroupsRoute.Create` apaga a regra.
     */
    data object OpenPlans : GroupListEffect

    data object OpenJoinWithCode : GroupListEffect
}
