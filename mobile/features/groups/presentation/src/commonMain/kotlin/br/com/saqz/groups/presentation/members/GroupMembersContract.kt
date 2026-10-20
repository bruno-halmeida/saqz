package br.com.saqz.groups.presentation.members

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError

/** Pílulas do 2k, na ordem do export: "Todos · 26", "Admins · 2", "Pendentes · 2"; "Convidados · N" do CONV-010. */
enum class GroupMembersFilter { All, Admins, Pending, Invited }

/**
 * Linhas possíveis do sheet. Quais aparecem sai de [sheetActions], não de dois sheets:
 * o 2k abre o sheet de membro comum e o 2l o de admin, mas é o mesmo painel.
 * [Report], [Block] e [Unblock] são de qualquer papel (App Store 1.2), nunca sobre si mesmo.
 */
enum class GroupMemberAction { ViewProfile, EditMember, Promote, Demote, Remove, Report, Block, Unblock }

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
    /** Quem olha bloqueou esta pessoa: o sheet troca "Bloquear" por "Desbloquear". */
    val isBlocked: Boolean = false,
)

@Immutable
data class JoinRequestUi(
    val id: String,
    val name: String,
    /** "Pediu há 2 h": quanto tempo faz, contado no fuso do grupo. */
    val meta: String,
)

/** Aviso de retorno depois de decidir um pedido, os mesmos de Convidar. */
sealed interface GroupMembersNotice {
    data class RequestApproved(val name: String) : GroupMembersNotice

    data object RequestRejected : GroupMembersNotice
}

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
    /** Só dono e admin decidem pedidos (a mesma regra do backend); para os outros não há "Pendentes". */
    val canReviewRequests: Boolean = false,
    /** A lista de pedidos não veio: o elenco continua na tela e um aviso oferece tentar de novo. */
    val requestsFailed: Boolean = false,
    /** O elenco não veio, mas os pedidos sim: eles ficam e o aviso oferece tentar de novo. */
    val rosterFailed: Boolean = false,
    /** Pedidos com aprovar/recusar em andamento: o segundo toque não chama a API de novo. */
    val busyRequestIds: Set<String> = emptySet(),
    /** != null abre a confirmação de "Recusar": recusar nunca age no primeiro toque. */
    val declineCandidate: JoinRequestUi? = null,
    val notice: GroupMembersNotice? = null,
    val joinRequests: List<JoinRequestUi> = emptyList(),
    val admins: List<MemberUi> = emptyList(),
    val members: List<MemberUi> = emptyList(),
    /** Quantas linhas de membro comum estão na tela — "Mostrando 5 de 24 membros". */
    val shownCount: Int = 0,
    /** != null abre o sheet do 2l. */
    val selected: MemberUi? = null,
    /** != null abre a confirmação de "Remover do grupo": remover nunca age no primeiro toque. */
    val confirmRemove: MemberUi? = null,
    /** Trocar papel/remover bateu em 403 SUBSCRIPTION_REQUIRED: abre a folha da assinatura. */
    val subscriptionRequired: Boolean = false,
    val trialEnded: Boolean = false,
    /** A ação falhou por outro motivo: a lista fica na tela e um aviso explica. */
    val actionFailed: Boolean = false,
    /** O servidor negou a ação (403): hoje só acontece ao remover um admin sem ser o dono. */
    val actionDenied: Boolean = false,
)

/**
 * O "24" do cabeçalho de Membros e o "de 24" do rodapé da lista contam só quem não é
 * admin: no export a pílula diz 26 e a seção diz 24, com 2 admins entre os dois.
 */
val GroupMembersState.memberCount: Int get() = totalCount - adminCount

/**
 * Todo membro consultável oferece "Ver perfil". Papel (dono, admin, atleta) e tipo de
 * participação (mensalista, avulso) são independentes: quem gerencia atletas vê "Editar jogador"
 * para qualquer alvo, dono e admin inclusive, e para si mesmo. Para alvo admin, "Tornar admin"
 * vira "Remover admin"; remover do grupo fecha os dois sheets.
 * O dono do grupo é imutável: ninguém rebaixa nem remove. Admin promovido edita e
 * remove atleta, mas não promove nem remove admin — isso fica com o OWNER. Sobre si mesmo, só perfil e edição.
 */
fun MemberUi.sheetActions(): List<GroupMemberAction> = managementActions() + moderationActions()

private fun MemberUi.managementActions(): List<GroupMemberAction> = when {
    !canManageAthletes && !canManageRoles -> listOf(GroupMemberAction.ViewProfile)
    isSelf || isOwner -> buildList {
        add(GroupMemberAction.ViewProfile)
        if (canManageAthletes) add(GroupMemberAction.EditMember)
    }
    isAdmin -> buildList {
        add(GroupMemberAction.ViewProfile)
        if (canManageAthletes) add(GroupMemberAction.EditMember)
        if (canManageRoles) add(GroupMemberAction.Demote)
        // Só o dono remove um admin (SAI-003); canManageRoles é o dono.
        if (canManageRoles) add(GroupMemberAction.Remove)
    }
    else -> buildList {
        add(GroupMemberAction.ViewProfile)
        if (canManageAthletes) add(GroupMemberAction.EditMember)
        if (canManageRoles) add(GroupMemberAction.Promote)
        if (canManageAthletes) add(GroupMemberAction.Remove)
    }
}

/** Denunciar e bloquear valem para qualquer outra pessoa do grupo, dono incluído. */
private fun MemberUi.moderationActions(): List<GroupMemberAction> = if (isSelf) {
    emptyList()
} else {
    listOf(GroupMemberAction.Report, if (isBlocked) GroupMemberAction.Unblock else GroupMemberAction.Block)
}

sealed interface GroupMembersIntent {
    data object Retry : GroupMembersIntent

    /** Volta à tela: recarga por baixo, sem esqueleto nem tela de erro. */
    data object Refresh : GroupMembersIntent

    data class UpdateQuery(val value: String) : GroupMembersIntent

    data class SelectFilter(val filter: GroupMembersFilter) : GroupMembersIntent

    data class OpenMember(val memberId: String) : GroupMembersIntent

    data object DismissSheet : GroupMembersIntent

    /** Age sobre `state.selected`; sem ninguém selecionado, não faz nada. */
    data class PerformAction(val action: GroupMemberAction) : GroupMembersIntent

    data object ConfirmRemove : GroupMembersIntent

    data object DismissRemove : GroupMembersIntent

    data object DismissSubscriptionRequired : GroupMembersIntent

    data object DismissActionFailed : GroupMembersIntent

    data class AcceptRequest(val requestId: String) : GroupMembersIntent

    data class DeclineRequest(val requestId: String) : GroupMembersIntent

    data object ConfirmDecline : GroupMembersIntent

    data object DismissDecline : GroupMembersIntent

    data object ClearNotice : GroupMembersIntent

    data object Invite : GroupMembersIntent
}

sealed interface GroupMembersEffect {
    data class OpenMemberProfile(val memberId: String) : GroupMembersEffect

    /** Fluxo 3 (3g). */
    data class OpenMemberEditor(val memberId: String) : GroupMembersEffect

    /** Fluxo 3 (3a). */
    data class OpenInvite(val groupId: String) : GroupMembersEffect

    /** Os sheets de denúncia e bloqueio são do `ModerationViewModel`; o Root repassa. */
    data class ReportMember(val groupId: String, val memberId: String, val name: String) : GroupMembersEffect

    data class BlockMember(val groupId: String, val memberId: String, val name: String) : GroupMembersEffect

    data class UnblockMember(val memberId: String) : GroupMembersEffect
}
