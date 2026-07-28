package br.com.saqz.groups.presentation.details

import br.com.saqz.core.common.mvi.MviViewModel

/**
 * Só roteia toque em saída — o detalhe do grupo não tem estado próprio a coordenar
 * enquanto o gateway não estiver ligado.
 *
 * ponytail: o estado chega pronto pelo construtor. Quando o `GroupGateway` entrar, esse
 * parâmetro vira um `Flow` coletado no `init` (como o `LoginViewModel` faz com o
 * `AuthenticationStateMachine`) e nenhuma tela muda. Teto conhecido: hoje `isLoading`
 * nunca sai de onde chegou.
 */
class GroupDetailsViewModel(
    private val groupId: String,
    initialState: GroupDetailsState = GroupDetailsState(),
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(initialState) {

    override fun onIntent(intent: GroupDetailsIntent) {
        when (intent) {
            GroupDetailsIntent.CreateNextGame -> emit(GroupDetailsEffect.OpenCreateGame(groupId))
            GroupDetailsIntent.EditGroup -> emit(GroupDetailsEffect.OpenEdit(groupId))
            // A quadra é campo do formulário de grupo: "Editar" na linha do 2f abre a
            // mesma tela de edição, não uma tela de quadra.
            GroupDetailsIntent.EditVenue -> emit(GroupDetailsEffect.OpenEdit(groupId))
            GroupDetailsIntent.ManageMembers -> emit(GroupDetailsEffect.OpenMembers(groupId))
            GroupDetailsIntent.ViewAllMembers -> emit(GroupDetailsEffect.OpenMembers(groupId))
            GroupDetailsIntent.ManageSchedule -> emit(GroupDetailsEffect.OpenSchedule(groupId))
            GroupDetailsIntent.OpenSchedule -> emit(GroupDetailsEffect.OpenSchedule(groupId))
            GroupDetailsIntent.InviteByLink -> emit(GroupDetailsEffect.OpenInviteLink(groupId))
            GroupDetailsIntent.Invite -> emit(GroupDetailsEffect.OpenInviteLink(groupId))
            GroupDetailsIntent.OpenCashbox -> emit(GroupDetailsEffect.OpenCashbox(groupId))
            GroupDetailsIntent.OpenVenueMap -> emit(GroupDetailsEffect.OpenMap)
            GroupDetailsIntent.Leave -> emit(GroupDetailsEffect.Left)
            // Os cinco toques que o export desenha sem destino nenhum: no protótipo são
            // `<button>` sem href. Ficam no contrato porque o desenho os mostra, e não
            // emitem nada até o fluxo dono existir — comentado em VUL-69.
            GroupDetailsIntent.ConfirmAttendance,
            GroupDetailsIntent.ViewGame,
            GroupDetailsIntent.NotifyPending,
            GroupDetailsIntent.OpenNotices,
            GroupDetailsIntent.OpenChat,
            -> Unit
        }
    }
}
