package br.com.saqz.groups.presentation.members

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

/** Pílulas do 2k, na ordem do export: "Todos · 26", "Admins · 2", "Pendentes · 2". */
enum class GroupMembersFilter { All, Admins, Pending }

/**
 * Linhas possíveis do sheet. Quais aparecem sai de [sheetActions], não de dois sheets:
 * o 2k abre o sheet de membro comum e o 2l o de admin, mas é o mesmo painel.
 */
enum class GroupMemberAction { ViewProfile, EditMember, Promote, Demote, Remove }

@Immutable
data class MemberUi(
    val id: String,
    val name: String,
    /** "Ponteira · desde março" — montado por quem carrega, não pela tela. */
    val meta: String,
    val isAdmin: Boolean,
    /** Pinta o "· você" ao lado do nome e tira o toque da linha. */
    val isSelf: Boolean,
    /** "18 jogos · 92% de presença", só no cabeçalho do sheet. */
    val stats: String,
    /** Dono e admin: editar elenco e remover. Promover/rebaixar continua só com o dono. */
    val canManageAthletes: Boolean = true,
    /** Somente o OWNER promove e rebaixa. */
    val canManageRoles: Boolean = true,
    /** O dono do grupo não sai do elenco nem perde o papel. */
    val isOwner: Boolean = false,
)

@Immutable
data class JoinRequestUi(
    val id: String,
    val name: String,
    /** "Entrou pelo código · há 2h". */
    val meta: String,
    /** true → chip "Pendente"; false → botões de aceitar e recusar. */
    val awaitingReview: Boolean,
)

@Immutable
data class GroupMembersState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val query: String = "",
    val filter: GroupMembersFilter = GroupMembersFilter.All,
    /** Todo mundo do grupo, admins inclusive — é o número da pílula "Todos". */
    val totalCount: Int = 0,
    val adminCount: Int = 0,
    val pendingCount: Int = 0,
    val joinRequests: List<JoinRequestUi> = emptyList(),
    val admins: List<MemberUi> = emptyList(),
    val members: List<MemberUi> = emptyList(),
    /** Quantas linhas de membro comum estão na tela — "Mostrando 5 de 24 membros". */
    val shownCount: Int = 0,
    /** != null abre o sheet do 2l. */
    val selected: MemberUi? = null,
)

/**
 * O "24" do cabeçalho de Membros e o "de 24" do rodapé da lista contam só quem não é
 * admin: no export a pílula diz 26 e a seção diz 24, com 2 admins entre os dois.
 */
val GroupMembersState.memberCount: Int get() = totalCount - adminCount

/**
 * A diferença entre o sheet do 2k e o do 2l. Admin troca "Editar jogador" por
 * "Ver perfil" e "Tornar admin" por "Remover admin"; remover do grupo fecha os dois.
 * O dono do grupo é imutável: ninguém rebaixa nem remove. Admin promovido edita e
 * remove elenco, mas não promove — isso fica com o OWNER.
 */
fun MemberUi.sheetActions(): List<GroupMemberAction> = when {
    isOwner -> listOf(GroupMemberAction.ViewProfile)
    !canManageAthletes && !canManageRoles -> listOf(GroupMemberAction.ViewProfile)
    isAdmin -> buildList {
        add(GroupMemberAction.ViewProfile)
        if (canManageRoles) add(GroupMemberAction.Demote)
        if (canManageAthletes) add(GroupMemberAction.Remove)
    }
    else -> buildList {
        add(if (canManageAthletes) GroupMemberAction.EditMember else GroupMemberAction.ViewProfile)
        if (canManageRoles) add(GroupMemberAction.Promote)
        if (canManageAthletes) add(GroupMemberAction.Remove)
    }
}

sealed interface GroupMembersIntent {
    data object Retry : GroupMembersIntent

    data class UpdateQuery(val value: String) : GroupMembersIntent

    data class SelectFilter(val filter: GroupMembersFilter) : GroupMembersIntent

    data class OpenMember(val memberId: String) : GroupMembersIntent

    data object DismissSheet : GroupMembersIntent

    /** Age sobre `state.selected`; sem ninguém selecionado, não faz nada. */
    data class PerformAction(val action: GroupMemberAction) : GroupMembersIntent

    data class AcceptRequest(val requestId: String) : GroupMembersIntent

    data class DeclineRequest(val requestId: String) : GroupMembersIntent

    data object Invite : GroupMembersIntent
}

sealed interface GroupMembersEffect {
    data class OpenMemberProfile(val memberId: String) : GroupMembersEffect

    /** Fluxo 3 (3g). */
    data class OpenMemberEditor(val memberId: String) : GroupMembersEffect

    /** Fluxo 3 (3a). */
    data class OpenInvite(val groupId: String) : GroupMembersEffect
}
