package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class JoinByLinkNavigationTest {
    @Test fun registrationOpensOnTopOfTheShell() {
        val stack = NavBackStack<NavKey>(SaqzShellDestination.Home)
        stack.openAthleteRegistrationAfterJoin("g1")
        assertEquals(listOf(SaqzShellDestination.Home, GroupsRoute.AthleteRegistration("g1")), stack.toList())
    }

    @Test fun routesOpenedMeanwhileAreReplacedInTheSameStep() {
        val stack = NavBackStack<NavKey>(SaqzShellDestination.Home, GroupsRoute.GameDetail("g1", "game"))
        stack.openAthleteRegistrationAfterJoin("g1")
        assertEquals(listOf(SaqzShellDestination.Home, GroupsRoute.AthleteRegistration("g1")), stack.toList())
    }

    @Test fun repeatedJoinDoesNotStackTwoRegistrations() {
        val stack = NavBackStack<NavKey>(SaqzShellDestination.Home)
        stack.openAthleteRegistrationAfterJoin("g1")
        stack.openAthleteRegistrationAfterJoin("g1")
        assertEquals(2, stack.size)
    }

    @Test fun pendingInviteNeverLetsTheHomeBeTheIntermediateDestination() {
        // Sessão pronta, convite guardado, coordenador ainda não começou: carregando, não Início.
        assertEquals(true, isInviteHandoffPending(true, true, coordinatorAuthenticated = false, redeeming = false))
        // Coordenador trabalhando no convite.
        assertEquals(true, isInviteHandoffPending(true, true, coordinatorAuthenticated = true, redeeming = true))
        // Tela do convite já aberta e tratada: a Início volta (Explorar o app).
        assertEquals(false, isInviteHandoffPending(true, true, coordinatorAuthenticated = true, redeeming = false))
        // Sem convite ou sem sessão: nada muda.
        assertEquals(false, isInviteHandoffPending(true, false, coordinatorAuthenticated = false, redeeming = false))
        assertEquals(false, isInviteHandoffPending(false, true, coordinatorAuthenticated = false, redeeming = false))
    }
}
