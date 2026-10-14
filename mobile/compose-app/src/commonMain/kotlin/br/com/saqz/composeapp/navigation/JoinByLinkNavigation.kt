package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.presentation.navigation.GroupsRoute

/**
 * Entrou no grupo por link: o cadastro de atleta tem de ser a tela de cima, numa única mudança da
 * pilha. O redeem termina depois de a Início já ter recarregado e a pessoa pode ter tocado em
 * "Ver o jogo" nesse intervalo; empilhar o cadastro sobre uma transição em curso deixava a pilha
 * presa no meio dela. Tudo o que estiver acima do shell sai junto, no mesmo passo.
 */
internal fun MutableList<NavKey>.openAthleteRegistrationAfterJoin(groupId: String) {
    val shellIndex = indexOfFirst { it is SaqzShellDestination }
    val target = GroupsRoute.AthleteRegistration(groupId)
    if (lastOrNull() == target) return
    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
        if (shellIndex >= 0 && shellIndex < lastIndex) subList(shellIndex + 1, size).clear()
        add(target)
    }
}


/**
 * Enquanto há convite para tratar depois do login, a Início não é o destino intermediário: da sessão
 * pronta até o coordenador entregar a tela do convite, a pessoa vê só o carregando.
 */
internal fun isInviteHandoffPending(
    sessionReady: Boolean,
    hasPendingInvite: Boolean,
    coordinatorAuthenticated: Boolean,
    redeeming: Boolean,
): Boolean = sessionReady && hasPendingInvite && (!coordinatorAuthenticated || redeeming)

/** Tempo máximo que o shell fica só no carregando por causa de um convite: nunca prende a pessoa. */
internal const val INVITE_HANDOFF_TIMEOUT_MILLIS = 15_000L

/** Telas do convite (cadastro de atleta depois de entrar, pedido enviado ou erro). */
internal fun isInviteScreen(route: NavKey?): Boolean = when (route) {
    is GroupsRoute.InviteLanding -> true
    is GroupsRoute.AthleteRegistration -> !route.fromProfile
    else -> false
}

/**
 * A retenção do shell em "carregando" começa quando o convite começa a ser tratado ([active]) e só
 * termina quando a pessoa sai da tela do convite. Depois de o efeito navegar o convite já não está
 * pendente, mas a Início ainda é composta por baixo durante a transição; segurar até a tela do
 * convite sair do topo evita Início, esqueleto e toque nesse intervalo. Sessão que oscila no meio
 * não solta a retenção; só sair da conta ([signedOut]) solta.
 */
internal fun nextInviteHold(
    held: Boolean,
    active: Boolean,
    sessionReady: Boolean,
    signedOut: Boolean,
    topIsInviteScreen: Boolean,
): Boolean = when {
    signedOut -> false
    active -> true
    held && sessionReady && !topIsInviteScreen -> false
    else -> held
}
