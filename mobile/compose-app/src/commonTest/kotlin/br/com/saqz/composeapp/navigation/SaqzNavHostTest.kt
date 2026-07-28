package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
import br.com.saqz.access.domain.session.AccessSession
import br.com.saqz.access.domain.session.AccessUser
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O portão de sessão: cada [SessionAccessState] resolve para exatamente um destino, e só o
 * `Ready` alcança o shell. Exaustivo por construção — um estado novo reprova o `when` de
 * `toDestination` em tempo de compilação e aparece faltando aqui.
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
        assertEquals(listOf(SaqzShellDestination), stackFor(SessionAccessState.Ready(session)))
    }

    // O portão canonicaliza para uma entrada só: a profundidade que o VUL-84 devolve ao
    // fluxo 1 é da navegação da pessoa, não de um estado de sessão.
    @Test
    fun everySessionStateResolvesToASingleEntry() {
        listOf(
            SessionAccessState.SignedOut,
            SessionAccessState.CompletingIdentity(session),
            SessionAccessState.Bootstrapping,
            SessionAccessState.BootstrapError,
            SessionAccessState.Ready(session),
        ).forEach { state -> assertEquals(1, stackFor(state).size) }
    }

    @Test
    fun reconcilingAnAlreadyMatchingStackIsANoOp() {
        val stack = mutableListOf<NavKey>(AccessRoute.Login)
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        reconcileAccessStack(stack, SessionAccessState.SignedOut)
        assertEquals(listOf<NavKey>(AccessRoute.Login), stack)
    }

    // VUL-84: enquanto a sessão não muda, a reconciliação não corre — é o que deixa o
    // percurso 1a → 1d → 1e → 1g → 1h de pé. Quando ela corre, o portão vence: o estado
    // novo desfaz o que a pessoa empilhou, porque o destino dele é outro.
    @Test
    fun aSessionChangeCollapsesTheStackTheUserBuilt() {
        val stack = mutableListOf<NavKey>(
            AccessRoute.Login,
            AccessRoute.ForgotPassword,
            AccessRoute.ResetCode("ana@exemplo.com"),
        )

        reconcileAccessStack(stack, SessionAccessState.Ready(session))

        assertEquals(listOf<NavKey>(SaqzShellDestination), stack)
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
