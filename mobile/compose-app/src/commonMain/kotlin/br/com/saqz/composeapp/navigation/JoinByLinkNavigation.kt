package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.register.RegisterInviteContext
import br.com.saqz.groups.invite.GroupInviteCoordinator
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

/**
 * Só `logout()` leva a sessão a [SessionAccessState.SignedOut] vinda de outro estado; Ready →
 * Bootstrapping (nova tentativa) não é sair e não pode perder o convite. [previous] nulo é o
 * estado inicial da composição, que não conta.
 */
internal fun isLogoutTransition(previous: SessionAccessState?, current: SessionAccessState): Boolean =
    previous != null && previous !is SessionAccessState.SignedOut && current is SessionAccessState.SignedOut

/**
 * A cópia que a navegação guarda do convite: o código (sobrevive à recriação) e o nome do grupo
 * usado em "Entrando no <grupo>". As decisões de quando ela cai moram aqui, sem Compose, para o
 * `SaqzNavHost` e os testes usarem o mesmo código. O armazenamento do aparelho continua sendo do
 * [GroupInviteCoordinator].
 */
internal class InviteCopy(code: String? = null) {
    var code by mutableStateOf(code)
    var context by mutableStateOf<RegisterInviteContext?>(null)

    private var previousSession: SessionAccessState? = null

    fun clear() {
        code = null
        context = null
    }

    /** Entrar no grupo e pedir entrada contam igual: o convite já foi usado. */
    fun onRedeemed() = clear()

    /**
     * Sessão mudou: só a transição para SignedOut é sair da conta. Aí a cópia cai e o coordinator
     * apaga o guardado; Ready → Bootstrapping (nova tentativa) não toca em nada.
     */
    fun onSessionChanged(session: SessionAccessState, coordinator: GroupInviteCoordinator) {
        if (isLogoutTransition(previousSession, session)) {
            clear()
            coordinator.onLoggedOut()
        }
        previousSession = session
    }

    /** Ação da tela do convite (entrar ou pedir entrada): só depois do guardado apagado a cópia cai. */
    fun clearAfterAction(coordinator: GroupInviteCoordinator, inviteCode: String, then: () -> Unit = {}) {
        coordinator.clearPendingInvite(inviteCode) {
            clear()
            then()
        }
    }

    companion object {
        val Saver: Saver<InviteCopy, String> = Saver(
            save = { it.code },
            restore = { InviteCopy(it) },
        )
    }
}
