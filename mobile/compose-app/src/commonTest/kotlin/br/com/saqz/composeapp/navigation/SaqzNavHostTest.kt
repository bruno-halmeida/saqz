package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O gate de sessão: cada [SessionAccessState] resolve para um destino, e `Ready` é o único
 * que alcança o shell. Exaustivo por construção — um estado novo reprova o `when` de
 * `toDestination` em tempo de compilação e aparece faltando aqui.
 *
 * A partir do VUL-72 o stack tem profundidade: `Ready` garante só a **base**, e todo estado
 * não-`Ready` continua colapsando. Os três casos que o ticket exige estão no fim do arquivo.
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
    fun awaitingVerificationRoutesToVerification() {
        assertEquals(
            listOf(AccessRoute.Verification),
            stackFor(SessionAccessState.AwaitingVerification(user)),
        )
    }

    @Test
    fun completingNameRoutesToNameCompletion() {
        assertEquals(
            listOf(AccessRoute.NameCompletion),
            stackFor(SessionAccessState.CompletingName(user)),
        )
    }

    @Test
    fun completingPhoneRoutesToPhoneCompletion() {
        assertEquals(
            listOf(AccessRoute.PhoneCompletion),
            stackFor(SessionAccessState.CompletingPhone(session)),
        )
    }

    @Test
    fun bootstrapStatesRouteToBootstrap() {
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.Bootstrapping))
        assertEquals(listOf(AccessRoute.Bootstrap), stackFor(SessionAccessState.BootstrapError))
    }

    @Test
    fun readyRoutesToTheEmptyShell() {
        assertEquals(listOf(SaqzShellDestination), stackFor(SessionAccessState.Ready(session)))
    }

    // VUL-72: fora de `Ready` o gate continua colapsando -- é ele que impede tela
    // autenticada de aparecer sem sessão, e afrouxar isso quebraria o login.
    @Test
    fun everyStateBeforeReadyCollapsesToASingleEntry() {
        listOf(
            SessionAccessState.SignedOut,
            SessionAccessState.AwaitingVerification(user),
            SessionAccessState.CompletingName(user),
            SessionAccessState.CompletingPhone(session),
            SessionAccessState.Bootstrapping,
            SessionAccessState.BootstrapError,
        ).forEach { state ->
            val stack = mutableListOf<NavKey>(SaqzShellDestination, GroupsRoute.Details("ceret"))
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
        val stack = mutableListOf<NavKey>(SaqzShellDestination)
        reconcileAccessStack(stack, ready)
        stack += GroupsRoute.Details("ceret")
        stack += GroupsRoute.Members("ceret")

        reconcileAccessStack(stack, ready)
        reconcileAccessStack(stack, ready)

        assertEquals(
            listOf<NavKey>(
                SaqzShellDestination,
                GroupsRoute.Details("ceret"),
                GroupsRoute.Members("ceret"),
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
        assertEquals(listOf<NavKey>(SaqzShellDestination), stack)
    }

    // VUL-72 (b): sair da sessão limpa tudo, inclusive o que estava empilhado.
    @Test
    fun signingOutFromADeepStackClearsItBackToLogin() {
        val stack = mutableListOf<NavKey>(
            SaqzShellDestination,
            GroupsRoute.Details("ceret"),
            GroupsRoute.Schedule("ceret"),
        )
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    private companion object {
        val user = NativeUser(
            subject = "user-1",
            email = "atleta@example.test",
            emailVerified = true,
            displayName = "Atleta",
        )
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
