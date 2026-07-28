package br.com.saqz.groups.presentation.list

import br.com.saqz.core.common.mvi.MviViewModel

/**
 * ponytail: o estado chega pronto pelo construtor — esta onda é só apresentação e não
 * existe gateway para consultar. Teto: `Retry` volta para "carregando" e para aí, porque
 * ninguém resolve a carga. Quando o `GroupGateway` entrar, este parâmetro vira um `Flow`
 * coletado no `init` (com guarda de geração) e nenhuma tela muda.
 */
class GroupListViewModel(
    initialState: GroupListState = GroupListState(),
) : MviViewModel<GroupListState, GroupListIntent, GroupListEffect>(initialState) {

    override fun onIntent(intent: GroupListIntent) {
        when (intent) {
            is GroupListIntent.OpenGroup -> emit(GroupListEffect.OpenGroup(intent.id))
            GroupListIntent.CreateGroup -> emit(GroupListEffect.OpenPlans)
            GroupListIntent.JoinWithCode -> emit(GroupListEffect.OpenJoinWithCode)
            is GroupListIntent.AcceptInvite -> dismissInvite(intent.id)
            is GroupListIntent.DeclineInvite -> dismissInvite(intent.id)
            GroupListIntent.Retry -> update { it.copy(isLoading = true, loadFailed = false) }
        }
    }

    // O cartão some na hora, sem esperar rede; o reenvio da resposta é do gateway, quando existir.
    private fun dismissInvite(id: String) {
        if (state.value.invite?.id != id) return
        update { it.copy(invite = null) }
    }
}
