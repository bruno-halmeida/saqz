package br.com.saqz.composeapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import br.com.saqz.access.navigation.AccessRoute
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.NameCompletionRoot
import br.com.saqz.access.ui.PhoneCompletionRoot
import br.com.saqz.access.ui.VerificationRoot
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.composeapp.shell.SaqzAppShell
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
 * A UI ainda não navega **abaixo** do shell: [reconcileAccessStack] deriva a base do stack
 * do estado de sessão autoritativo. Acima dela, quem empilha são as lambdas que cada `Root`
 * recebe — navegação entre features é callback (AGENTS.md §6), e o `Root` não conhece
 * `NavDisplay`.
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
    val pop: () -> Unit = { backStack.removeLastOrNull() }
    NavDisplay(
        backStack = backStack,
        // O `NavDisplay` só habilita o back quando há entrada anterior, então isto nunca
        // esvazia a base que o gate garante.
        onBack = pop,
        entryProvider = entryProvider {
            entry<AccessRoute.Starting> { SaqzSpinner() }
            entry<AccessRoute.Login> { LoginRoot() }
            entry<AccessRoute.Verification> { VerificationRoot() }
            entry<AccessRoute.NameCompletion> { NameCompletionRoot() }
            entry<AccessRoute.PhoneCompletion> { PhoneCompletionRoot() }
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
 * O gate de acesso. Fora de `Ready` ele **canonicaliza**: cada `SessionAccessState`
 * colapsa o stack para o seu destino único, e é isso que impede tela autenticada de
 * aparecer sem sessão — não pode afrouxar.
 *
 * Com `Ready` ele garante só a **base**. Antes do VUL-72 o stack tinha exatamente uma
 * entrada e `Ready` reescrevia tudo; com as rotas de grupo empilháveis, reescrever
 * zeraria a navegação a cada emissão de estado de sessão — e a sessão emite mais de uma
 * vez. Sair de `Ready` volta a colapsar, então logout continua limpando o stack inteiro.
 */
internal fun reconcileAccessStack(stack: MutableList<NavKey>, session: SessionAccessState) {
    val destination = session.toDestination()
    if (session is SessionAccessState.Ready) {
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
    is SessionAccessState.AwaitingVerification -> AccessRoute.Verification
    is SessionAccessState.CompletingName -> AccessRoute.NameCompletion
    is SessionAccessState.CompletingPhone -> AccessRoute.PhoneCompletion
    SessionAccessState.Bootstrapping, SessionAccessState.BootstrapError -> AccessRoute.Bootstrap
    is SessionAccessState.Ready -> SaqzShellDestination
}
