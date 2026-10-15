package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.presentation.SessionAccessState
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

    private fun hold(
        held: Boolean,
        active: Boolean = false,
        sessionReady: Boolean = true,
        signedOut: Boolean = false,
        top: NavKey? = SaqzShellDestination.Home,
    ) = nextInviteHold(held, active, sessionReady, signedOut, isInviteScreen(top))

    @Test fun shellStaysLoadingWhileTheInviteScreenIsOnTop() {
        // Convite tratado e entrada concluída: o pendente já saiu, mas a tela do convite está no topo.
        assertEquals(true, hold(held = true, top = GroupsRoute.AthleteRegistration("g1")))
        assertEquals(true, hold(held = true, top = GroupsRoute.InviteLanding("code", requestSent = true)))
        assertEquals(true, hold(held = true, top = GroupsRoute.InviteLanding("code")))
    }

    @Test fun leavingTheInviteScreenReleasesTheHome() {
        assertEquals(false, hold(held = true, top = SaqzShellDestination.Home))
        assertEquals(false, hold(held = true, top = GroupsRoute.Details("g1")))
    }

    @Test fun registrationOpenedFromTheProfileIsNotAnInviteScreen() {
        assertEquals(false, isInviteScreen(GroupsRoute.AthleteRegistration("g1", fromProfile = true)))
        assertEquals(true, isInviteScreen(GroupsRoute.AthleteRegistration("g1")))
    }

    @Test fun startsHoldingWhenTheInviteStartsBeingHandled() {
        assertEquals(true, hold(held = false, active = true))
    }

    @Test fun flickeringSessionInTheMiddleKeepsTheHold() {
        // Sessão passa por um estado não pronto com o shell por baixo: a retenção não solta.
        assertEquals(true, hold(held = true, sessionReady = false, top = SaqzShellDestination.Home))
        // E ao voltar a ficar pronta, com o convite ainda pendente, continua retendo.
        assertEquals(true, hold(held = true, active = true))
    }

    @Test fun signingOutReleasesTheHold() {
        assertEquals(false, hold(held = true, sessionReady = false, signedOut = true, top = GroupsRoute.AthleteRegistration("g1")))
    }

    @Test fun handledWithoutAnyInviteScreenReleasesTheHome() {
        // Nada para tratar (sem convite guardado): a Início volta assim que o coordenador termina.
        assertEquals(false, hold(held = true, active = false, top = SaqzShellDestination.Home))
    }

    @Test fun holdSequenceFromSignupToLeavingTheRegistration() {
        var held = false
        // sessão pronta, coordenador ainda não autenticou: ativo
        held = hold(held, active = true)
        assertEquals(true, held)
        // redeem em curso: ativo, shell no topo
        held = hold(held, active = true)
        assertEquals(true, held)
        // efeito navegou: pendente zerou, cadastro no topo, shell por baixo em transição
        held = hold(held, active = false, top = GroupsRoute.AthleteRegistration("g1"))
        assertEquals(true, held)
        // pessoa sai do cadastro
        held = hold(held, active = false, top = SaqzShellDestination.Home)
        assertEquals(false, held)
    }

    private val accessSession = AccessSession(
        user = AccessUser(id = "user-1", email = "atleta@example.test", displayName = "Atleta"),
        memberships = emptyList(),
    )

    @Test fun signedOutTransitionIsDetectedFromEveryOtherState() {
        listOf(
            SessionAccessState.Ready(accessSession),
            SessionAccessState.Bootstrapping,
            SessionAccessState.BootstrapError,
            SessionAccessState.CompletingIdentity(accessSession),
        ).forEach { assertEquals(true, isLogoutTransition(it, SessionAccessState.SignedOut), "$it") }
    }

    @Test fun signedOutToSignedOutAndTheInitialStateDoNotCount() {
        assertEquals(false, isLogoutTransition(SessionAccessState.SignedOut, SessionAccessState.SignedOut))
        assertEquals(false, isLogoutTransition(null, SessionAccessState.SignedOut))
    }

    @Test fun readyToBootstrappingIsARetryNotALogout() {
        assertEquals(false, isLogoutTransition(SessionAccessState.Ready(accessSession), SessionAccessState.Bootstrapping))
        assertEquals(false, isLogoutTransition(SessionAccessState.SignedOut, SessionAccessState.Bootstrapping))
    }
}
