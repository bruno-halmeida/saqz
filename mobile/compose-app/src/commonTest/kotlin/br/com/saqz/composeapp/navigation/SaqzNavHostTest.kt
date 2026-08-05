package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.composeapp.shell.SaqzShellGroupsTab
import br.com.saqz.composeapp.shell.SaqzShellHomeTab
import br.com.saqz.groups.invite.GroupInviteEffect
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.groups.presentation.navigation.InviteLandingRouteError
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * O gate de sessão: cada [SessionAccessState] resolve para um destino, e `Ready` é o único
 * que alcança o shell. Exaustivo por construção — um estado novo reprova o `when` de
 * `toDestination` em tempo de compilação e aparece faltando aqui.
 *
 * O stack tem profundidade nos dois lados da sessão, e o gate garante só a **base** nos dois
 * casos em que ela é legítima: acima do shell, com as rotas de grupo (VUL-72); e no lado
 * deslogado, na primeira passagem depois de uma recriação do host (VUL-84). Fora deles todo
 * estado continua colapsando. Os casos de cada um estão no fim do arquivo.
 */
class SaqzNavHostTest {

    private fun stackFor(session: SessionAccessState): List<NavKey> =
        mutableListOf<NavKey>(AccessRoute.Starting)
            .also { reconcileAccessStack(it, session) }

    @Test
    fun signedOutRoutesToLogin() {
        assertEquals(listOf(AccessRoute.Login), stackFor(SessionAccessState.SignedOut))
    }

    @Test
    fun completingIdentityRoutesToTheMergedScreen() {
        assertEquals(
            listOf(AccessRoute.IdentityCompletion),
            stackFor(SessionAccessState.CompletingIdentity(session)),
        )
    }

    @Test
    fun bootstrapStatesRouteToBootstrap() {
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.Bootstrapping))
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.BootstrapError))
    }

    @Test
    fun readyRoutesToTheEmptyShell() {
        assertEquals(listOf(SaqzShellDestination.Home), stackFor(SessionAccessState.Ready(session)))
    }

    @Test
    fun authenticatedInviteExploreReturnsToTheShell() {
        val stack = NavBackStack<NavKey>(
            SaqzShellDestination.Home,
            GroupsRoute.InviteLanding("invite-explore"),
        )

        stack.openInviteExplore()

        assertEquals(listOf<NavKey>(SaqzShellDestination.Home), stack.toList())
    }

    // VUL-193: os callbacks do invite landing (BrowseOtherGroups e OpenAnotherGroup,
    // mais o back da top-bar que emite BrowseOtherGroups) prometem a lista de grupos.
    // Fazem `openInviteOtherGroups()` → `resetTo(SaqzShellDestination.Groups)`, e o destino
    // carrega a aba Grupos no `initialTab` — sem isto o reset cairia na aba Início (default)
    // e quebraria a promessa.
    @Test
    fun inviteBrowseOtherGroupsLandsOnTheGroupsTab() {
        val stack = NavBackStack<NavKey>(
            SaqzShellDestination.Home,
            GroupsRoute.InviteLanding("invite-other"),
        )

        stack.openInviteOtherGroups()

        assertEquals(listOf<NavKey>(SaqzShellDestination.Groups), stack.toList())
        assertEquals(SaqzShellGroupsTab, SaqzShellDestination.Groups.resolvedTab())
    }

    @Test
    fun loginLandsOnTheHomeTabByDefault() {
        // O destino que o login alcança (default) carrega a aba Início.
        assertEquals(SaqzShellHomeTab, SaqzShellDestination.Home.resolvedTab())
        assertNull(SaqzShellDestination.Home.initialTab)
    }

    @Test
    fun pendingInviteStorageFailureRoutesToVisibleLandingError() {
        assertEquals(
            GroupsRoute.InviteLanding(
                code = "invite-storage",
                redeemError = InviteLandingRouteError.Network,
            ),
            pendingInviteStorageFailureRoute(
                code = GroupInviteEffect.PendingInviteStorageFailed("invite-storage").code,
                fallbackCode = null,
            ),
        )
    }

    // VUL-72: fora de `Ready` o gate continua colapsando -- é ele que impede tela
    // autenticada de aparecer sem sessão, e afrouxar isso quebraria o login. A profundidade
    // que o VUL-84 devolve ao fluxo 1 é navegação da pessoa, não estado de sessão, e não
    // afrouxa isto: só a primeira passagem depois da recriação do host é tolerante.
    @Test
    fun everyStateBeforeReadyCollapsesToASingleEntry() {
        listOf(
            SessionAccessState.SignedOut,
            SessionAccessState.CompletingIdentity(session),
            SessionAccessState.Bootstrapping,
            SessionAccessState.BootstrapError,
        ).forEach { state ->
            val stack = mutableListOf<NavKey>(SaqzShellDestination.Home, GroupsRoute.Details("ceret"))
            reconcileAccessStack(stack, state)
            assertEquals(1, stack.size, "$state deveria colapsar o stack")
        }
    }

    @Test
    fun reconcilingAnAlreadyMatchingStackIsANoOp() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    // VUL-72 (a): com `Ready` o gate garante só a base. A sessão emite mais de uma vez, e
    // antes disto cada emissão zerava a navegação de volta para o shell.
    @Test
    fun repeatedReadyKeepsTheStackedGroupRoutes() {
        val ready = SessionAccessState.Ready(session)
        val stack = mutableListOf<NavKey>(SaqzShellDestination.Home)
        reconcileAccessStack(stack, ready)
        stack += GroupsRoute.Details("ceret")
        stack += GroupsRoute.Members("ceret")
        stack += ProfileRoute.Edit
        stack += ProfileRoute.Exit("atleta@example.test")

        reconcileAccessStack(stack, ready)
        reconcileAccessStack(stack, ready)

        assertEquals(
            listOf<NavKey>(
                SaqzShellDestination.Home,
                GroupsRoute.Details("ceret"),
                GroupsRoute.Members("ceret"),
                ProfileRoute.Edit,
                ProfileRoute.Exit("atleta@example.test"),
            ),
            stack,
        )
    }

    // VUL-72: `Ready` ainda corrige uma base errada -- chegar autenticado sobre a pilha de
    // acesso continua levando ao shell.
    @Test
    fun readyReplacesAnAccessStackWithTheShell() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(stack, SessionAccessState.Ready(session))
        assertEquals(listOf<NavKey>(SaqzShellDestination.Home), stack)
    }

    // VUL-84, o mesmo de cima com o fluxo 1 empilhado: a base errada cai inteira, não só a
    // entrada de baixo. Autenticar no meio de uma recuperação de senha vai para o shell.
    @Test
    fun readyReplacesADeepAccessStackWithTheShell() {
        val stack = mutableListOf<NavKey>(
            AccessRoute.Login,
            AccessRoute.ForgotPassword,
            AccessRoute.ResetCode("ana@exemplo.com"),
        )

        reconcileAccessStack(stack, SessionAccessState.Ready(session))

        assertEquals(listOf<NavKey>(SaqzShellDestination.Home), stack)
    }

    // VUL-72 (b): sair da sessão limpa tudo, inclusive o que estava empilhado.
    @Test
    fun signingOutFromADeepStackClearsItBackToLogin() {
        val stack = mutableListOf<NavKey>(
            SaqzShellDestination.Home,
            GroupsRoute.Details("ceret"),
            GroupsRoute.Schedule("ceret"),
            ProfileRoute.Exit("atleta@example.test"),
        )
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    // Recriar o host roda o efeito de novo com o mesmo `SignedOut`, e isso não é transição:
    // girar o aparelho no meio do 1b, ou voltar do app de e-mail durante o 1e, não pode
    // devolver a pessoa ao login.
    @Test
    fun aRestoredStackSurvivesTheFirstPass() {
        val restored = mutableListOf<NavKey>(
            AccessRoute.Login,
            AccessRoute.ForgotPassword,
            AccessRoute.ResetCode("ana@exemplo.com"),
        )

        reconcileAccessStack(restored, SessionAccessState.SignedOut, restoring = true)

        assertEquals(
            listOf<NavKey>(
                AccessRoute.Login,
                AccessRoute.ForgotPassword,
                AccessRoute.ResetCode("ana@exemplo.com"),
            ),
            restored,
        )
    }

    // A tolerância acima é só para o stack que já nasce sob o destino da sessão. Um início
    // a frio começa em `Starting`, e ficar ali seria abrir o app num spinner eterno.
    @Test
    fun aColdStartIsStillCanonicalizedOnTheFirstPass() {
        val cold = mutableListOf<NavKey>(AccessRoute.Starting)

        reconcileAccessStack(cold, SessionAccessState.SignedOut, restoring = true)

        assertEquals(listOf<NavKey>(AccessRoute.Login), cold)
    }

    // E o stack restaurado de uma sessão que não existe mais também cai: a raiz não bate
    // com o destino, então a canonicalização acontece mesmo na primeira passagem.
    @Test
    fun aRestoredStackFromADeadSessionIsCanonicalized() {
        val stale = mutableListOf<NavKey>(SaqzShellDestination.Home, GroupsRoute.Details("ceret"))

        reconcileAccessStack(stale, SessionAccessState.SignedOut, restoring = true)

        assertEquals(listOf<NavKey>(AccessRoute.Login), stale)
    }

    /**
     * VUL-90: a troca de senha consome o ticket, então concluir o 1g **tira o formulário
     * do stack**. Empilhar o 1h sobre ele deixava o voltar — o visível e o do sistema —
     * reabrir o 1g com um token já usado, e o 1e com um código já gasto: a pessoa digita
     * tudo de novo para levar um erro que não tem como entender.
     */
    @Test
    fun completingTheSignedOutResetDropsTheConsumedFormToLogin() {
        val stack = mutableListOf<NavKey>(
            AccessRoute.Login,
            AccessRoute.ForgotPassword,
            AccessRoute.ResetCode("ana@exemplo.com"),
            AccessRoute.NewPassword("ana@exemplo.com", "ticket-do-reset"),
        )

        stack.completePasswordReset(SessionAccessState.SignedOut)

        assertEquals(listOf<NavKey>(AccessRoute.Login, AccessRoute.PasswordChanged), stack)
    }

    @Test
    fun completingTheAuthenticatedResetDropsTheConsumedFormToTheShell() {
        val stack = mutableListOf<NavKey>(
            SaqzShellDestination.Home,
            AccessRoute.ForgotPassword,
            AccessRoute.ResetCode("ana@exemplo.com"),
            AccessRoute.NewPassword("ana@exemplo.com", "ticket-do-reset"),
        )

        stack.completePasswordReset(SessionAccessState.Ready(session))

        assertEquals(listOf<NavKey>(SaqzShellDestination.Home, AccessRoute.PasswordChanged), stack)
    }

    @Test
    fun passwordChangedSignInReturnsToTheLiveSessionDestination() {
        val authenticated = mutableListOf<NavKey>(AccessRoute.PasswordChanged)
        authenticated.finishPasswordChanged(SessionAccessState.Ready(session))
        assertEquals(listOf<NavKey>(SaqzShellDestination.Home), authenticated)

        val signedOut = mutableListOf<NavKey>(AccessRoute.PasswordChanged)
        signedOut.finishPasswordChanged(SessionAccessState.SignedOut)
        assertEquals(listOf<NavKey>(AccessRoute.Login), signedOut)
    }

    /**
     * VUL-113: "Criar meu grupo" na 8d empilha sobre um pagamento já concluído — o segmento
     * do fluxo de planos cai do stack antes do formulário de criação entrar, senão o voltar
     * do 2a reabriria 8d/8c/8b/8a já resolvidos.
     */
    @Test
    fun creatingAGroupFromPlanActiveDropsThePlansSegment() {
        val stack = mutableListOf<NavKey>(
            SaqzShellDestination.Home,
            SubscriptionsRoute.PlanSelection,
            SubscriptionsRoute.Payment(planId = "Organizador", cycle = "Monthly"),
            SubscriptionsRoute.PlanActive,
        )

        stack.dropPlansSegment()

        assertEquals(listOf<NavKey>(SaqzShellDestination.Home), stack)
    }

    // A base de grupos empilhada por baixo (VUL-72) não é parte do segmento de planos, e
    // fica de pé.
    @Test
    fun dropPlansSegmentLeavesUnrelatedRoutesUntouched() {
        val stack = mutableListOf<NavKey>(
            SaqzShellDestination.Home,
            GroupsRoute.Details("ceret"),
            SubscriptionsRoute.PlanSelection,
        )

        stack.dropPlansSegment()

        assertEquals(listOf<NavKey>(SaqzShellDestination.Home, GroupsRoute.Details("ceret")), stack)
    }

    private companion object {
        val session = AccessSession(
            user = AccessUser(
                id = "user-1",
                email = "atleta@example.test",
                displayName = "Atleta",
            ),
            memberships = emptyList(),
        )
    }
}
