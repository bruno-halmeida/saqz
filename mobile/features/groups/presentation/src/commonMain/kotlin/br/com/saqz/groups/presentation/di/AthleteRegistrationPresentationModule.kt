package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Grafo isolado da tela 3j/3k; a ligação ao bootstrap pertence ao fecho do fluxo. */
fun athleteRegistrationPresentationModule(): Module = module {
    viewModel { params ->
        AthleteRegistrationViewModel(
            groupId = params.get(),
            savedState = params.get(),
            groupGateway = get(),
            athleteGateway = get(),
        )
    }
}
