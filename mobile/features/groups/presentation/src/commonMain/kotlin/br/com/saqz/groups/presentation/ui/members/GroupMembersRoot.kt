package br.com.saqz.groups.presentation.ui.members

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.members.GroupMembersEffect
import br.com.saqz.groups.presentation.members.GroupMembersViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Quem navega é o `SaqzNavHost` (VUL-72): os três efeitos saem daqui como callback e a
 * tela não conhece rota nenhuma.
 */
@Composable
fun GroupMembersRoot(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenMemberEditor: (String) -> Unit,
    onOpenInvite: (String) -> Unit,
    viewModel: GroupMembersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupMembersEffect.OpenMemberProfile -> onOpenProfile(effect.memberId)
            is GroupMembersEffect.OpenMemberEditor -> onOpenMemberEditor(effect.memberId)
            is GroupMembersEffect.OpenInvite -> onOpenInvite(effect.groupId)
        }
    }
    GroupMembersScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
