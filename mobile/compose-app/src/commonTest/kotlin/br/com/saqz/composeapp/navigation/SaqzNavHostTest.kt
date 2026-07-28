package br.com.saqz.composeapp.navigation

import androidx.navigation3.runtime.NavKey
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
        assertEquals(listOf(SaqzShellDestination), stackFor(SessionAccessState.Ready(session)))
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
        val stale = mutableListOf<NavKey>(SaqzShellDestination, GroupsRoute.Details("ceret"))

        reconcileAccessStack(stale, SessionAccessState.SignedOut, restoring = true)

        assertEquals(listOf<NavKey>(AccessRoute.Login), stale)
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
