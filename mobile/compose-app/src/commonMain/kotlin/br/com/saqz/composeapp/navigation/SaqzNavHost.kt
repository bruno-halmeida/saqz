package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import br.com.saqz.access.ui.ForgotPasswordRoot
import br.com.saqz.access.ui.IdentityCompletionRoot
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.ResetCodeRoot
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.ui.details.GroupDetailsRoot
import br.com.saqz.groups.presentation.ui.list.GroupListRoot
import br.com.saqz.groups.presentation.ui.members.GroupMembersRoot
import br.com.saqz.groups.presentation.ui.schedule.GroupScheduleRoot
import br.com.saqz.groups.presentation.ui.setup.GroupSetupRoot

// Legacy observable contract carried over from the product host: exactly one active
// destination host in the tree (rotation/recreation tests count this tag).
internal const val SaqzDestinationHostTag = "authenticated-access-destination"

/**
 * The single Navigation3 [NavDisplay] over the acesso→shell→grupos back stack. The access
 * screens and the group screens are feature-owned Roots (each resolves its own ViewModel
 * through Koin); the only app-owned destination is the shell.
 *
 * O stack tem profundidade dos dois lados da sessão, e por motivos diferentes:
 *
 * - **acima da base**, as rotas de grupo empilham sobre o shell (VUL-72). Quem empilha são
 *   as lambdas que cada `Root` recebe — navegação entre features é callback (AGENTS.md §6),
 *   e o `Root` não conhece `NavDisplay`;
 * - **no lado deslogado**, as seis telas do fluxo 1 que a pessoa alcança clicando (1b a 1h)
 *   empilham sobre o login (VUL-84).
 *
 * [reconcileAccessStack] deriva a **base** do stack do estado de sessão autoritativo, e é
 * onde as duas profundidades se conciliam: ele nunca reescreve o que a pessoa empilhou por
 * cima de uma base correta. O voltar desfaz.
 *
 * As rotas do fluxo 1 ainda são andaimes: um `Text` com o nome da rota e os links para as
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
    // A primeira passagem depois de uma recriação do host não é transição de sessão: o
    // `LaunchedEffect` roda de novo com o mesmo `SignedOut` de sempre, e canonicalizar ali
    // jogaria fora o stack que acabou de ser restaurado — girar o aparelho no meio do 1b, ou
    // voltar do app de e-mail durante o 1e, devolveria a pessoa ao login.
    val restoring = remember { booleanArrayOf(true) }
    LaunchedEffect(state.session) {
        reconcileAccessStack(backStack, state.session, restoring = restoring[0])
        restoring[0] = false
    }
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        // O `NavDisplay` só habilita o back quando há entrada anterior
        // (`isBackEnabled = scene.previousEntries.isNotEmpty()`, NavDisplay.kt:557 do
        // navigation3-ui 1.1.1), então isto nunca esvazia a base que o gate garante — é o
        // mesmo corpo do `onBack` padrão da própria biblioteca.
        onBack = pop,
        entryProvider = entryProvider {
            entry<AccessRoute.Starting> { SaqzSpinner() }
            entry<AccessRoute.Login> {
                LoginRoot(
                    onCreateAccount = { backStack.add(AccessRoute.Register) },
                    onForgotPassword = { backStack.add(AccessRoute.ForgotPassword) },
                )
            }
            entry<AccessRoute.Register> {
                // Cadastrar não empilha a 1c: a 1c só existe quando a sessão está em
                // `CompletingIdentity`, e quem reage a isso é o `reconcileAccessStack`.
                // Empilhar `IdentityCompletion` daqui, com a máquina ainda em `SignedOut`,
                // abria um formulário vazio cujos intentos a máquina recusa todos.
                AccessSkeleton("Register")
            }
            entry<AccessRoute.IdentityCompletion> { IdentityCompletionRoot() }
            entry<AccessRoute.ForgotPassword> {
                ForgotPasswordRoot(
                    onBack = pop,
                    onOpenResetCode = { email -> backStack.add(AccessRoute.ResetCode(email)) },
                )
            }
            entry<AccessRoute.ResetCode> { route ->
                ResetCodeRoot(
                    email = route.email,
                    onBack = pop,
                    // "Lembrou a senha? Entrar ›" desiste da troca: o stack volta à base.
                    onSignIn = { backStack.resetTo(AccessRoute.Login) },
                    onOpenNewPassword = { email, token ->
                        backStack.add(AccessRoute.NewPassword(email, token))
                    },
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
                    groupsTab = {
                        // A aba Grupos é o 2n. `GroupsRoute.List` não vira `entry` porque
                        // seria um segundo host da mesma tela — e a barra do 10q fica sob
                        // a lista só enquanto o shell é quem a desenha.
                        GroupListRoot(
                            onOpenGroup = { backStack.add(GroupsRoute.Details(it)) },
                            // TODO(Fluxo 8 · Planos): "Criar grupo" passa pela escolha de
                            // plano antes de chegar ao 2a. Sem ele, `GroupsRoute.Create`
                            // não tem quem a empilhe.
                            onOpenPlans = {},
                            // TODO(Fluxo 3 · Convite): entrar por código.
                            onJoinWithCode = {},
                        )
                    },
                )
            }
            entry<GroupsRoute.Create> {
                GroupSetupDestination(GroupSetupMode.Create, backStack)
            }
            entry<GroupsRoute.Edit> { route ->
                GroupSetupDestination(GroupSetupMode.Edit(route.groupId), backStack)
            }
            entry<GroupsRoute.Details> { route ->
                GroupDetailsRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    onEffect = { effect -> backStack.onDetailsEffect(effect, pop) },
                )
            }
            entry<GroupsRoute.Members> { route ->
                GroupMembersRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    // TODO(Fluxo 7 · Perfil): ver perfil do membro.
                    onOpenProfile = {},
                    // TODO(Fluxo 3 · Convite, 3g): editor de membro.
                    onOpenMemberEditor = {},
                    // TODO(Fluxo 3 · Convite, 3a): link de convite.
                    onOpenInvite = {},
                )
            }
            entry<GroupsRoute.Schedule> { route ->
                GroupScheduleRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    // TODO(Fluxo 4 · Jogos): abrir o jogo da agenda.
                    onOpenGame = {},
                )
            }
        },
        modifier = modifier.testTag(SaqzDestinationHostTag),
    )
}

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
 * O `2a`/`2i`: as duas rotas do formulário compartilham a mesma ligação, porque o modo já
 * carrega a diferença. Os cinco efeitos saem por callback (AGENTS.md §6) e cada um é
 * tratado — o formulário sempre fecha quando termina, só o destino muda.
 */
@Composable
private fun GroupSetupDestination(mode: GroupSetupMode, backStack: NavBackStack<NavKey>) {
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    GroupSetupRoot(
        mode = mode,
        // Criou: o formulário sai do stack e o grupo novo entra no lugar dele.
        onGroupCreate = { groupId ->
            pop()
            backStack.add(GroupsRoute.Details(groupId))
        },
        onGroupSave = pop,
        // Apagou: `Details` e `Edit` do grupo morto ficam para trás; volta para a lista.
        onGroupDelete = { while (backStack.size > 1) backStack.removeLastOrNull() },
        onDraftSave = pop,
        // TODO(Fluxo 2 · Grupos, 2h): a foto passa pelo `GroupPhotoSelectionPort`, que é
        // port nativo e não rota — a ligação é do ticket que trouxer o gateway.
        onPickPhoto = {},
        onBack = pop,
    )
}

/**
 * O 2e/2f manda **um** efeito para fora, e cada um dos oito é tratado aqui — nada de
 * `else -> {}`: a tela antiga de detalhe usava isso e escondeu bug (VUL-72).
 */
private fun MutableList<NavKey>.onDetailsEffect(effect: GroupDetailsEffect, pop: () -> Unit) {
    when (effect) {
        is GroupDetailsEffect.OpenEdit -> add(GroupsRoute.Edit(effect.groupId))
        is GroupDetailsEffect.OpenMembers -> add(GroupsRoute.Members(effect.groupId))
        is GroupDetailsEffect.OpenSchedule -> add(GroupsRoute.Schedule(effect.groupId))
        // Sair do grupo devolve à lista; o efeito é a confirmação, não a pergunta.
        GroupDetailsEffect.Left -> pop()
        // TODO(Fluxo 4 · Criar jogo)
        is GroupDetailsEffect.OpenCreateGame -> Unit
        // TODO(Fluxo 5 · Financeiro, 5b)
        is GroupDetailsEffect.OpenCashbox -> Unit
        // TODO(Fluxo 3 · Convite, 3a)
        is GroupDetailsEffect.OpenInviteLink -> Unit
        // TODO(Fluxo 9 · Quadra): abrir a quadra no mapa é port nativo, não rota.
        GroupDetailsEffect.OpenMap -> Unit
    }
}

/**
 * O gate de acesso, garantindo a **base** do stack em dois casos e **canonicalizando** no
 * resto.
 *
 * Garante só a base quando a profundidade acima dela é navegação legítima da pessoa, e são
 * dois caminhos independentes que chegam à mesma regra:
 *
 * - `Ready` (VUL-72), porque as rotas de grupo empilham sobre o shell. Antes o stack tinha
 *   exatamente uma entrada e `Ready` reescrevia tudo; com elas empilháveis, reescrever
 *   zeraria a navegação a cada emissão de estado de sessão — e a sessão emite mais de uma vez;
 * - [restoring] (VUL-84), a primeira passagem depois de uma recriação do host, que **não** é
 *   transição — é o mesmo estado de sempre chegando de novo. Sem isso, girar o aparelho no
 *   meio do 1b, ou voltar do app de e-mail durante o 1e, jogaria a pessoa de volta ao login.
 *
 * Nos dois, "base correta" é a raiz do stack bater com o destino. O que **não** nasce sob
 * ele cai de qualquer forma: o `[Starting]` de um início a frio, ou um stack restaurado de
 * uma sessão que já morreu.
 *
 * Fora deles, canonicaliza: cada `SessionAccessState` colapsa o stack para o seu destino
 * único. É isso que impede tela autenticada de aparecer sem sessão e o que faz o logout
 * limpar o stack inteiro — não pode afrouxar.
 */
internal fun reconcileAccessStack(
    stack: MutableList<NavKey>,
    session: SessionAccessState,
    restoring: Boolean = false,
) {
    val destination = session.toDestination()
    if (session is SessionAccessState.Ready || restoring) {
        if (stack.firstOrNull() != destination) {
            stack.clear()
            stack.add(destination)
        }
        return
    }
    val target: List<NavKey> = listOf(destination)
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
