package br.com.saqz.profile.data.di

import br.com.saqz.profile.data.KtorProfileGateway
import br.com.saqz.profile.domain.ProfileGateway
import org.koin.core.module.Module
import org.koin.dsl.module

/** Binding do gateway do perfil, mantido no próprio feature para a onda C apenas carregar. */
fun profileDataModule(): Module = module {
    single<ProfileGateway> { KtorProfileGateway(get()) }
}

fun profileModule(): Module = profileDataModule()
