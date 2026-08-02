package br.com.saqz.groups.data.di

import br.com.saqz.groups.data.invite.KtorInviteGateway
import br.com.saqz.groups.domain.membership.InviteGateway
import org.koin.core.module.Module
import org.koin.dsl.module

/** Grafo isolado do convite; o fecho instala este módulo quando liga o Fluxo 3. */
fun inviteJourneyDataModule(): Module = module {
    single<InviteGateway> { KtorInviteGateway(get()) }
}
