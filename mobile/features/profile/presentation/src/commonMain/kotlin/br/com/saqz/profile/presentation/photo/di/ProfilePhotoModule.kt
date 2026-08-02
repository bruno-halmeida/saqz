package br.com.saqz.profile.presentation.photo.di

import br.com.saqz.profile.domain.ProfilePhotoSelectionPort
import br.com.saqz.profile.presentation.photo.ProfilePhotoViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun profilePhotoPresentationModule(selection: ProfilePhotoSelectionPort): Module = module {
    single<ProfilePhotoSelectionPort> { selection }
    viewModel { ProfilePhotoViewModel(gateway = get(), selection = get()) }
}
