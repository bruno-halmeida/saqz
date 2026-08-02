package br.com.saqz.profile.presentation.edit.di

import br.com.saqz.profile.presentation.edit.EditProfileViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun editProfilePresentationModule(): Module = module {
    viewModel { EditProfileViewModel(get(), get()) }
}
