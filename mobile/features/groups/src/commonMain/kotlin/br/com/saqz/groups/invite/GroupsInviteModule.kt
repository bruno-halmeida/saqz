package br.com.saqz.groups.invite

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Módulo isolado do fluxo de convite. O fecho fornece o scope e o sink de effects quando ligar
 * o coordinator ao host; por isso este módulo não entra no bootstrap compartilhado.
 */
fun groupsInviteModule(
    scope: CoroutineScope,
    effectSink: (GroupInviteEffect) -> Unit,
): Module = module {
    single {
        GroupInviteCoordinator(
            linkPort = get(),
            localState = get(),
            inviteGateway = get(),
            scope = scope,
            effectSink = effectSink,
        )
    }
}
