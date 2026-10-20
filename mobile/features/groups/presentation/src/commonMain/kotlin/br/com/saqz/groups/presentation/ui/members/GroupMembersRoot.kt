package br.com.saqz.groups.presentation.ui.members

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.domain.moderation.ReportTargetType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import br.com.saqz.groups.presentation.members.GroupInviteesEffect
import br.com.saqz.groups.presentation.members.GroupInviteesIntent
import br.com.saqz.groups.presentation.members.GroupInviteesViewModel
import br.com.saqz.groups.presentation.members.GroupMembersEffect
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import br.com.saqz.groups.presentation.moderation.ModerationIntent
import br.com.saqz.groups.presentation.moderation.ModerationViewModel
import br.com.saqz.groups.presentation.moderation.ReportTargetUi
import br.com.saqz.groups.presentation.ui.moderation.ModerationOverlay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Quem navega é o `SaqzNavHost` (VUL-72): os três efeitos saem daqui como callback e a
 * tela não conhece rota nenhuma.
 *
 * [groupId] entra como parâmetro de Koin porque a ViewModel o exige no construtor — é o
 * `GroupsRoute.Members.groupId` que o `NavDisplay` já carrega. A definição em si
 * (`viewModel { params -> GroupMembersViewModel(params.get()) }`) é do VUL-72, dono do
 * grafo; daqui sai só o argumento, para que registrar a rota não precise mexer neste
 * arquivo.
 *
 * Denunciar e bloquear abrem os sheets do [moderation], por cima da tela.
 */
@Composable
fun GroupMembersRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMemberEditor: (String) -> Unit,
    onOpenInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMonthlyInvite: (String) -> Unit = {},
    viewModel: GroupMembersViewModel = koinViewModel(
        key = "members/$groupId",
        parameters = { parametersOf(groupId) },
    ),
    moderation: ModerationViewModel = koinViewModel(key = "moderation/members/$groupId"),
    invitees: GroupInviteesViewModel = koinViewModel(key = "invitees/$groupId", parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val inviteesState by invitees.state.collectAsStateWithLifecycle()
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    ObserveAsEvents(invitees.effects) { effect ->
        when (effect) {
            is GroupInviteesEffect.OpenMonthlyInvite -> onOpenMonthlyInvite(effect.groupId)
            is GroupInviteesEffect.Copy -> clipboard.setText(AnnotatedString(effect.text))
        }
    }
    val moderationState by moderation.state.collectAsStateWithLifecycle()
    RefreshWhenVisible {
        viewModel.onIntent(GroupMembersIntent.Refresh)
        invitees.onIntent(GroupInviteesIntent.Refresh)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupMembersEffect.OpenMemberProfile -> onOpenProfile(effect.memberId)
            is GroupMembersEffect.OpenMemberEditor -> onOpenMemberEditor(effect.memberId)
            is GroupMembersEffect.OpenInvite -> onOpenInvite(effect.groupId)
            is GroupMembersEffect.ReportMember -> moderation.onIntent(
                ModerationIntent.StartReport(
                    ReportTargetUi(effect.groupId, ReportTargetType.USER, effect.memberId, effect.name),
                ),
            )
            is GroupMembersEffect.BlockMember -> moderation.onIntent(
                ModerationIntent.StartBlock(effect.groupId, effect.memberId, effect.name),
            )
            is GroupMembersEffect.UnblockMember -> moderation.onIntent(ModerationIntent.Unblock(effect.memberId))
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        GroupMembersScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onBack = onBack,
            invitees = inviteesState,
            onInviteesIntent = invitees::onIntent,
        )
        ModerationOverlay(state = moderationState, onIntent = moderation::onIntent)
    }
}
