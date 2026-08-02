package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.presentation.invite.InviteLandingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Módulo instalável pelo fecho do Fluxo 3; não entra no bootstrap comum. */
fun inviteLandingPresentationModule(): Module = module {
    viewModel { params -> InviteLandingViewModel(params.get(), get(), get<GroupSystemTimeZonePort>()) }
}
