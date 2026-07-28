package br.com.saqz.groups.presentation.members

import br.com.saqz.core.common.mvi.MviViewModel

/**
 * 2k e 2l. Promover, rebaixar, remover, aceitar e recusar mexem na própria lista e por
 * isso são intent, não efeito — só abrir perfil, editor e convite saem da tela.
 *
 * ponytail: a lista chega pronta pelo construtor e nunca é recarregada. Quando o
 * `GroupMembershipGateway` for ligado, [initialState] vira um Flow coletado no `init` e
 * as mutações abaixo passam a chamar o gateway antes de reprojetar; nenhuma tela muda.
 * Teto: sem gateway, a mudança otimista é a única — nada reverte se o servidor recusar.
 */
class GroupMembersViewModel(
    private val groupId: String,
    initialState: GroupMembersState = GroupMembersState(),
) : MviViewModel<GroupMembersState, GroupMembersIntent, GroupMembersEffect>(initialState) {

    private var roster: List<MemberUi> = initialState.admins + initialState.members
    private var requests: List<JoinRequestUi> = initialState.joinRequests

    init {
        project()
    }

    override fun onIntent(intent: GroupMembersIntent) {
        when (intent) {
            is GroupMembersIntent.UpdateQuery -> {
                update { it.copy(query = intent.value) }
                project()
            }

            is GroupMembersIntent.SelectFilter -> {
                update { it.copy(filter = intent.filter) }
                project()
            }

            is GroupMembersIntent.OpenMember -> openMember(intent.memberId)
            GroupMembersIntent.DismissSheet -> update { it.copy(selected = null) }
            is GroupMembersIntent.PerformAction -> perform(intent.action)
            is GroupMembersIntent.AcceptRequest -> accept(intent.requestId)
            is GroupMembersIntent.DeclineRequest -> decline(intent.requestId)
            GroupMembersIntent.Invite -> emit(GroupMembersEffect.OpenInvite(groupId))
        }
    }

    /** A própria linha não abre sheet — no 2k ela é a única sem toque. */
    private fun openMember(memberId: String) {
        val member = roster.firstOrNull { it.id == memberId } ?: return
        if (member.isSelf) return
        update { it.copy(selected = member) }
    }

    private fun perform(action: GroupMemberAction) {
        val selected = state.value.selected ?: return
        when (action) {
            GroupMemberAction.ViewProfile -> emit(GroupMembersEffect.OpenMemberProfile(selected.id))
            GroupMemberAction.EditMember -> emit(GroupMembersEffect.OpenMemberEditor(selected.id))
            GroupMemberAction.Promote -> setAdmin(selected.id, admin = true)
            GroupMemberAction.Demote -> setAdmin(selected.id, admin = false)
            GroupMemberAction.Remove -> roster = roster.filterNot { it.id == selected.id }
        }
        update { it.copy(selected = null) }
        project()
    }

    /** Otimista: a linha troca de seção antes de qualquer confirmação. */
    private fun setAdmin(memberId: String, admin: Boolean) {
        roster = roster.map { if (it.id == memberId) it.copy(isAdmin = admin) else it }
    }

    private fun accept(requestId: String) {
        val request = requests.firstOrNull { it.id == requestId } ?: return
        requests = requests - request
        // ponytail: o aceito entra com o que o pedido já mostrava. O gateway devolverá
        // posição e mensalidade reais e substituirá esta linha; até lá, `stats` vazio é
        // o que esconde a estatística no cabeçalho do sheet.
        roster = roster + MemberUi(
            id = request.id,
            name = request.name,
            meta = request.meta,
            isAdmin = false,
            isSelf = false,
            stats = "",
        )
        project()
    }

    private fun decline(requestId: String) {
        val request = requests.firstOrNull { it.id == requestId } ?: return
        requests = requests - request
        project()
    }

    /**
     * Recorta a lista completa em três seções conforme busca e filtro. As contagens das
     * pílulas ficam sempre inteiras: elas dizem quanta gente existe, não quanta sobrou
     * do recorte.
     *
     * ponytail: busca e filtro em memória, sobre a lista que a tela já tem. Passar de
     * algumas centenas de membros, filtrar no servidor.
     */
    private fun project() = update { state ->
        val query = state.query.trim()
        val people = roster.filter { it.name.containsQuery(query) }
        val pending = requests.filter { it.name.containsQuery(query) }
        val admins = people.filter { it.isAdmin }
        val members = people.filterNot { it.isAdmin }
        val showsPeople = state.filter != GroupMembersFilter.Pending
        state.copy(
            totalCount = roster.size,
            adminCount = roster.count { it.isAdmin },
            pendingCount = requests.size,
            joinRequests = if (state.filter == GroupMembersFilter.Admins) emptyList() else pending,
            admins = if (showsPeople) admins else emptyList(),
            members = if (state.filter == GroupMembersFilter.All) members else emptyList(),
            shownCount = if (state.filter == GroupMembersFilter.All) members.size else 0,
        )
    }
}

private fun String.containsQuery(query: String): Boolean =
    query.isEmpty() || contains(query, ignoreCase = true)
