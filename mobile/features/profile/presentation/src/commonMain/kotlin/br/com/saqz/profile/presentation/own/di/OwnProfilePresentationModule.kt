package br.com.saqz.profile.presentation.own.di

import br.com.saqz.profile.presentation.own.OwnProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun ownProfilePresentationModule(): Module = module {
    viewModel { OwnProfileViewModel(gateway = get()) }
}
