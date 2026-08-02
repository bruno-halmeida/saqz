package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.invite.GroupInviteViewModel
import br.com.saqz.groups.presentation.invite.InvitePreviewMessageViewModel
import br.com.saqz.groups.presentation.invite.InviteQrViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Grafo próprio das telas 3a/3b/3c; o registro no app pertence ao fecho da navegação. */
fun inviteManagementPresentationModule(): Module = module {
    viewModel { params ->
        GroupInviteViewModel(
            groupId = params.get(),
            groupGateway = get(),
            membershipGateway = get(),
            entryRequestGateway = get(),
            athleteGateway = get(),
            urlStore = get(),
            sharePort = get(),
            clipboardPort = get(),
        )
    }
    viewModel { params ->
        InvitePreviewMessageViewModel(
            groupName = params.get(),
            inviteUrl = params.get(),
            sharePort = get(),
        )
    }
    viewModel { params ->
        InviteQrViewModel(
            groupName = params.get(),
            inviteUrl = params.get(),
            sharePort = get(),
        )
    }
}
