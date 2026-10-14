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
}
