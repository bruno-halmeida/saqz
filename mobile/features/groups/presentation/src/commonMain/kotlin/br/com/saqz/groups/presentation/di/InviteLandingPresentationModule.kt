package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.presentation.invite.InviteLandingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo do landing instalado pelo bootstrap no fecho do Fluxo 3. */
fun inviteLandingPresentationModule(): Module = module {
    viewModel { params ->
        InviteLandingViewModel(
            code = params.get(),
            inviteGateway = get(),
            timeZonePort = get<GroupSystemTimeZonePort>(),
            initialRequestSent = params.getOrNull<Boolean>() ?: false,
            initialRedeemError = params.getOrNull<InviteError>(),
        )
    }
}
