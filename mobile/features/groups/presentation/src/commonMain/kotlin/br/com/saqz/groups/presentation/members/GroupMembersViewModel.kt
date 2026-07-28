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
 *
 * ponytail: **quem constrói esta ViewModel precisa passar um [initialState] com dado
 * real.** O default é `GroupMembersState()`, que tem `isLoading = true` e as três listas
 * vazias — registrar a rota com ele deixa a tela em esqueleto para sempre, porque não há
 * nada aqui que saia do carregando por conta própria. Quem registra é o VUL-72; o teto
 * cai quando o gateway entrar e o `init` puder virar o carregando ele mesmo.
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
        // Intent inválido retorna cedo (AGENTS.md §4): a linha nem existe no sheet daquele
        // papel, então pedi-la é chamada forjada — "editar jogador" para um admin ou
        // "ver perfil" para um membro comum não emite efeito nem mexe no estado.
        if (action !in selected.sheetActions()) return
        when (action) {
            GroupMemberAction.ViewProfile -> emit(GroupMembersEffect.OpenMemberProfile(selected.id))
            GroupMemberAction.EditMember -> emit(GroupMembersEffect.OpenMemberEditor(selected.id))
            GroupMemberAction.Promote -> setAdmin(selected.id, admin = true)
            GroupMemberAction.Demote -> setAdmin(selected.id, admin = false)
            GroupMemberAction.Remove -> remove(selected)
        }
        update { it.copy(selected = null) }
        project()
    }

    /** Otimista: a linha troca de seção antes de qualquer confirmação. */
    private fun setAdmin(memberId: String, admin: Boolean) {
        roster = roster.map { if (it.id == memberId) it.copy(isAdmin = admin) else it }
        update { it.copy(adminCount = it.adminCount + if (admin) 1 else -1) }
    }

    private fun remove(member: MemberUi) {
        roster = roster.filterNot { it.id == member.id }
        update {
            it.copy(
                totalCount = it.totalCount - 1,
                adminCount = if (member.isAdmin) it.adminCount - 1 else it.adminCount,
            )
        }
    }

    private fun accept(requestId: String) {
        val request = decidable(requestId) ?: return
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
        update { it.copy(totalCount = it.totalCount + 1, pendingCount = it.pendingCount - 1) }
        project()
    }

    private fun decline(requestId: String) {
        val request = decidable(requestId) ?: return
        requests = requests - request
        update { it.copy(pendingCount = it.pendingCount - 1) }
        project()
    }

    /**
     * Pedido que ainda espera triagem não tem botão nenhum no 2k — mostra só o chip
     * "Pendente". Decidi-lo é intent inválido e retorna cedo (AGENTS.md §4), como a ação
     * fora do papel no sheet.
     */
    private fun decidable(requestId: String): JoinRequestUi? =
        requests.firstOrNull { it.id == requestId }?.takeIf { !it.awaitingReview }

    /**
     * Recorta em três seções, conforme busca e filtro, as linhas que a tela tem em mãos.
     *
     * **As contagens não saem daqui.** No 2k as pílulas dizem "Todos · 26" e o rodapé diz
     * "Mostrando 5 de 24 membros": 26 e 24 são o grupo inteiro, 5 é o que veio carregado.
     * Recalcular os totais a partir das linhas visíveis faria um grupo de 26 com 6 linhas
     * carregadas anunciar "Todos · 6" e "Mostrando 4 de 4" — o rodapé perderia justamente
     * a informação que ele existe para dar. Os totais vêm de quem constrói o estado e só
     * se movem pelo delta de uma decisão (promover, remover, aceitar, recusar).
     *
     * ponytail: busca e filtro em memória, sobre a lista que a tela já tem. Passar de
     * algumas centenas de membros, filtrar no servidor — e aí os totais continuam sendo
     * os do servidor, que é exatamente o que esta separação já assume.
     */
    private fun project() = update { state ->
        val query = state.query.trim()
        val people = roster.filter { it.name.containsQuery(query) }
        val pending = requests.filter { it.name.containsQuery(query) }
        val admins = people.filter { it.isAdmin }
        val members = people.filterNot { it.isAdmin }
        val showsPeople = state.filter != GroupMembersFilter.Pending
        state.copy(
            joinRequests = if (state.filter == GroupMembersFilter.Admins) emptyList() else pending,
            admins = if (showsPeople) admins else emptyList(),
            members = if (state.filter == GroupMembersFilter.All) members else emptyList(),
            shownCount = if (state.filter == GroupMembersFilter.All) members.size else 0,
        )
    }
}

private fun String.containsQuery(query: String): Boolean =
    query.isEmpty() || contains(query, ignoreCase = true)
