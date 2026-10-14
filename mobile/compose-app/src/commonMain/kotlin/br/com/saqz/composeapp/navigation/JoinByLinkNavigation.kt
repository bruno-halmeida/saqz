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

