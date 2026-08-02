package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.invite.GroupInviteEffect
import br.com.saqz.groups.presentation.invite.GroupInviteIntent
import br.com.saqz.groups.presentation.invite.GroupInviteViewModel
import br.com.saqz.groups.presentation.invite.GroupInviteToast
import br.com.saqz.groups.presentation.invite.InvitePreviewEffect
import br.com.saqz.groups.presentation.invite.InvitePreviewIntent
import br.com.saqz.groups.presentation.invite.InvitePreviewMessageViewModel
import br.com.saqz.groups.presentation.invite.InviteQrEffect
import br.com.saqz.groups.presentation.invite.InviteQrIntent
import br.com.saqz.groups.presentation.invite.InviteQrViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GroupInviteRoot(
    groupId: String,
    onBack: () -> Unit,
    onOpenMessagePreview: (groupName: String, inviteUrl: String) -> Unit,
    onOpenQr: (groupName: String, inviteUrl: String) -> Unit,
    onToast: (GroupInviteToast) -> Unit = {},
    viewModel: GroupInviteViewModel = koinViewModel(parameters = { parametersOf(groupId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupInviteEffect.OpenMessagePreview -> onOpenMessagePreview(effect.groupName, effect.inviteUrl)
            is GroupInviteEffect.OpenQr -> onOpenQr(effect.groupName, effect.inviteUrl)
            GroupInviteEffect.LinkCopied -> onToast(GroupInviteToast.LinkCopied)
        }
    }
    GroupInviteScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}

@Composable
fun InvitePreviewMessageRoot(
    groupName: String,
    inviteUrl: String,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
    viewModel: InvitePreviewMessageViewModel = koinViewModel(parameters = { parametersOf(groupName, inviteUrl) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            InvitePreviewEffect.Back -> onBack()
            InvitePreviewEffect.Shared -> onShare()
        }
    }
    InvitePreviewMessageScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun InviteQrRoot(
    groupName: String,
    inviteUrl: String,
    onBack: () -> Unit,
    onShare: () -> Unit = {},
    onSave: () -> Unit = {},
    viewModel: InviteQrViewModel = koinViewModel(parameters = { parametersOf(groupName, inviteUrl) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            InviteQrEffect.Back -> onBack()
            InviteQrEffect.Shared -> onShare()
            InviteQrEffect.Saved -> onSave()
        }
    }
    InviteQrScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}
