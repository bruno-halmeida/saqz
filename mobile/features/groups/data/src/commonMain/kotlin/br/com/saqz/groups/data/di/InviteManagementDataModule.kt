package br.com.saqz.groups.data.di

import br.com.saqz.groups.data.membership.KtorEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import org.koin.core.module.Module
import org.koin.dsl.module

/** Módulo do ticket, instalado pelo bootstrap quando liga as telas 3a/3b/3c. */
fun inviteManagementDataModule(): Module = module {
    single<GroupEntryRequestGateway> { KtorEntryRequestGateway(get()) }
}
