package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.SaqzSpinner

// Legacy observable contract carried over from the product host: exactly one active
// destination host in the tree (rotation/recreation tests count this tag).
internal const val SaqzDestinationHostTag = "authenticated-access-destination"

/**
 * C1 entry point: the single Navigation3 [NavDisplay] over one acesso→shell back stack.
 * The access screens are feature-owned Roots from `:features:access` (each resolves its
 * own ViewModel through Koin); the only app-owned destination is the empty shell.
 *
 * VUL-84 dá profundidade ao lado deslogado: as seis telas do fluxo 1 que a pessoa alcança
 * clicando (1b a 1h) empilham sobre o login. [reconcileAccessStack] continua derivando o
 * stack do estado de sessão autoritativo, mas só corre quando esse estado **muda** — entre
 * mudanças, a navegação do fluxo 1 é da pessoa, e o voltar a desfaz.
 *
 * Cada rota nova ainda é um andaime: um `Text` com o nome da rota e os links para as
 * próximas. Os sete tickets de tela da onda 5 trocam cada `AccessSkeleton` pelo seu Root,
 * uma linha por ticket.
 */
@Composable
internal fun SaqzNavHost(
    state: AccessUiState,
    onIntent: (AccessIntent) -> Unit,
    backStack: NavBackStack<NavKey> = rememberSerializable(
        serializer = saqzAccessBackStackSerializer,
        configuration = saqzLocalNavConfiguration,
    ) { defaultAccessBackStack() },
    modifier: Modifier = Modifier,
    catalogEnabled: Boolean = false,
) {
    LaunchedEffect(state.session) {
        reconcileAccessStack(backStack, state.session)
    }
    NavDisplay(
        backStack = backStack,
        // O voltar desfaz o que a pessoa empilhou dentro do fluxo 1, e nunca esvazia o
        // stack: a última entrada é a que o portão de sessão escolheu.
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryProvider = entryProvider {
            entry<AccessRoute.Starting> { SaqzSpinner() }
            entry<AccessRoute.Login> {
                LoginRoot(
                    onCreateAccount = { backStack.add(AccessRoute.Register) },
                    onForgotPassword = { backStack.add(AccessRoute.ForgotPassword) },
                )
            }
            entry<AccessRoute.Register> {
                AccessSkeleton("Register", "IdentityCompletion" to { backStack.add(AccessRoute.IdentityCompletion) })
            }
            entry<AccessRoute.IdentityCompletion> { AccessSkeleton("IdentityCompletion") }
            entry<AccessRoute.ForgotPassword> {
                AccessSkeleton(
                    "ForgotPassword",
                    "ResetCode" to { backStack.add(AccessRoute.ResetCode(SkeletonEmail)) },
                )
            }
            entry<AccessRoute.ResetCode> { route ->
                AccessSkeleton(
                    "ResetCode",
                    "NewPassword" to { backStack.add(AccessRoute.NewPassword(route.email, SkeletonToken)) },
                )
            }
            entry<AccessRoute.NewPassword> {
                AccessSkeleton("NewPassword", "PasswordChanged" to { backStack.add(AccessRoute.PasswordChanged) })
            }
            entry<AccessRoute.PasswordChanged> {
                // Senha trocada não tem volta para o formulário que a trocou: o "Entrar
                // agora" recomeça o stack no login.
                AccessSkeleton("PasswordChanged", "Login" to { backStack.resetTo(AccessRoute.Login) })
            }
            entry<AccessRoute.Bootstrap> {
                BootstrapAccessScreen(
                    state = state.session,
                    onIntent = { onIntent(AccessIntent.Session(it)) },
                )
            }
            entry<SaqzShellDestination> {
                SaqzAppShell(
                    onLogout = { onIntent(AccessIntent.ConfirmLogout) },
                    catalogEnabled = catalogEnabled,
                )
            }
        },
        modifier = modifier.testTag(SaqzDestinationHostTag),
    )
}

// Valores de andaime: a 1d ainda não coleta e-mail e a 1e ainda não troca código por
// ticket, mas as rotas já carregam os dois, então o percurso precisa de algo para levar.
private const val SkeletonEmail = "ana@exemplo.com"
private const val SkeletonToken = "token-de-andaime"

/** Uma tela feia: o nome da rota e um link por saída. */
@Composable
private fun AccessSkeleton(name: String, vararg next: Pair<String, () -> Unit>) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("skeleton-$name"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name)
        next.forEach { (label, go) -> Text("→ $label", modifier = Modifier.clickable(onClick = go)) }
    }
}

private fun NavBackStack<NavKey>.resetTo(route: NavKey) {
    clear()
    add(route)
}

/**
 * Migrated from `:navigation`'s `reconcileAccessStack` (C3 kills that module) and extended
 * with `Ready` → the empty shell. Cada estado de sessão canonicaliza o stack para o seu
 * destino único; roda a cada **mudança** de estado, e é no-op quando o stack já bate.
 *
 * Entre mudanças o stack é da pessoa: é assim que o percurso 1a → 1d → 1e → 1g → 1h
 * sobrevive, sem que o portão de sessão precise conhecer a navegação de dentro do fluxo.
 */
internal fun reconcileAccessStack(stack: MutableList<NavKey>, session: SessionAccessState) {
    val target: List<NavKey> = listOf(session.toDestination())
    if (stack != target) {
        stack.clear()
        stack.addAll(target)
    }
}

private fun SessionAccessState.toDestination(): NavKey = when (this) {
    SessionAccessState.SignedOut -> AccessRoute.Login
    is SessionAccessState.CompletingIdentity -> AccessRoute.IdentityCompletion
    SessionAccessState.Bootstrapping, SessionAccessState.BootstrapError -> AccessRoute.Bootstrap
    is SessionAccessState.Ready -> SaqzShellDestination
}
