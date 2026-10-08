package br.com.saqz.groups.presentation.di

import br.com.saqz.groups.presentation.whatsappbinding.WhatsAppAvailability
import br.com.saqz.groups.presentation.whatsappbinding.WhatsAppBindingViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Grafo das configurações avançadas do vínculo com o grupo do WhatsApp. */
fun whatsAppBindingPresentationModule(): Module = module {
    // Uma resposta do servidor para o app inteiro: detalhe do grupo, edição e preferências.
    single { WhatsAppAvailability(get()) }
    viewModel { params -> WhatsAppBindingViewModel(params.get(), get(), get()) }
}
