package br.com.saqz.groups.data.di

import br.com.saqz.groups.data.invite.KtorInviteGateway
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.NetworkClient
import org.koin.core.module.Module
import org.koin.dsl.module

/** Grafo isolado do convite; o bootstrap do app instala este módulo ao ligar o Fluxo 3. */
fun inviteJourneyDataModule(): Module = module {
    single<InviteGateway> {
        KtorInviteGateway(
            network = get<NetworkClient>(),
            authenticatedNetwork = get<AuthenticatedNetworkClient>(),
        )
    }
}
