package br.com.saqz.profile.presentation.exit.di

import br.com.saqz.profile.domain.ProfileGateway
import br.com.saqz.profile.presentation.exit.ProfileExitViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun profileExitPresentationModule(): Module = module {
    viewModel { params ->
        ProfileExitViewModel(
            gateway = get<ProfileGateway>(),
            email = params.get(),
        )
    }
}
