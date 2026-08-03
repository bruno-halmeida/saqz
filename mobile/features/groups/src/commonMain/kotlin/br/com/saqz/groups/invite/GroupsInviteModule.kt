package br.com.saqz.groups.invite

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Módulo isolado do fluxo de convite. O scope compartilhado vem do grafo de acesso, que também
 * controla o lifecycle do app. O host consome [GroupInviteCoordinator.effects] diretamente; o
 * sink opcional fica disponível para hosts que preferem receber os efeitos por callback.
 */
fun groupsInviteModule(
    effectSink: (GroupInviteEffect) -> Unit = {},
): Module = module {
    single {
        GroupInviteCoordinator(
            linkPort = get(),
            localState = get(),
            inviteGateway = get(),
            scope = get(),
            effectSink = effectSink,
        )
    }
}
