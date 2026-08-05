package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
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
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.emailVerified
import br.com.saqz.access.presentation.register.RegisterInviteContext
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.ForgotPasswordRoot
import br.com.saqz.access.ui.IdentityCompletionRoot
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.access.ui.NewPasswordRoot
import br.com.saqz.access.ui.PasswordChangedScreen
import br.com.saqz.access.ui.RegisterRoot
import br.com.saqz.access.ui.ResetCodeRoot
import br.com.saqz.composeapp.shell.EmailVerificationBanner
import br.com.saqz.composeapp.shell.SaqzAppShell
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.invite.GroupInviteCoordinator
import br.com.saqz.groups.invite.GroupInviteEffect
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.navigation.InviteLandingRouteError
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.groups.presentation.membereditor.MemberEditorRoot
import br.com.saqz.groups.presentation.newentry.NewEntryPrefill
import br.com.saqz.groups.presentation.newentry.NewEntryEffect
import br.com.saqz.groups.presentation.newentry.NewEntryRoot
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.statement.StatementEffect
import br.com.saqz.groups.presentation.statement.StatementRoot
import br.com.saqz.groups.presentation.ui.athleteregistration.AthleteRegistrationRoot
import br.com.saqz.groups.presentation.ui.details.GroupDetailsRoot
import br.com.saqz.groups.presentation.ui.finance.overview.FinanceOverviewRoot
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxRoot
import br.com.saqz.groups.presentation.ui.finance.settlement.GameSettlementRoot
import br.com.saqz.groups.presentation.ui.gameeditor.GameEditorRoot
import br.com.saqz.groups.presentation.ui.gamedetail.GameDetailRoot
import br.com.saqz.groups.presentation.ui.home.HomeOwnChargesBannerRoot
import br.com.saqz.groups.presentation.ui.home.HomeRoot
import br.com.saqz.groups.presentation.ui.invite.GroupInviteRoot
import br.com.saqz.groups.presentation.ui.invite.InviteLandingRoot
import br.com.saqz.groups.presentation.ui.invite.InvitePreviewMessageRoot
import br.com.saqz.groups.presentation.ui.invite.InviteQrRoot
import br.com.saqz.groups.presentation.ui.list.GroupListRoot
import br.com.saqz.groups.presentation.ui.members.GroupMembersRoot
import br.com.saqz.groups.presentation.ui.schedule.GroupScheduleRoot
import br.com.saqz.groups.presentation.ui.setup.GroupSetupRoot
import br.com.saqz.profile.presentation.edit.ui.EditProfileRoot
import br.com.saqz.profile.presentation.exit.ProfileExitRoot
import br.com.saqz.profile.presentation.navigation.ProfileRoute
import br.com.saqz.profile.presentation.own.ui.OwnProfileRoot
import br.com.saqz.subscriptions.presentation.navigation.SubscriptionsRoute
import br.com.saqz.subscriptions.presentation.payment.PaymentEffect
import br.com.saqz.subscriptions.presentation.payment.ui.PaymentRoot
import br.com.saqz.subscriptions.presentation.planselection.ui.PlanSelectionRoot
import br.com.saqz.subscriptions.presentation.ui.myplan.MyPlanRoot
import br.com.saqz.subscriptions.presentation.ui.planactive.PlanActiveRoot
import org.koin.compose.koinInject

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
 * - **acima da base**, as rotas de grupo e de perfil empilham sobre o shell. Quem empilha são
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
    var profileRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var scheduleRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var groupDetailsRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var groupCashboxRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var statementRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var settlementRefreshVersion by rememberSaveable { mutableIntStateOf(0) }
    var pendingInviteCode by rememberSaveable { mutableStateOf<String?>(null) }
    var inviteContext by remember { mutableStateOf<RegisterInviteContext?>(null) }
    var coordinatorAuthenticated by remember { mutableStateOf(false) }
    val inviteCoordinator = koinInject<GroupInviteCoordinator>()
    LaunchedEffect(inviteCoordinator) {
        if (pendingInviteCode == null) {
            // This must run before the session gate as well: a signed-out relaunch needs the
            // preview to reach RegisterRoot when the user chooses the registration path.
            pendingInviteCode = inviteCoordinator.readPendingInviteCode()
        }
    }
    LaunchedEffect(state.session) {
        reconcileAccessStack(backStack, state.session, restoring = restoring[0])
        restoring[0] = false
        if (state.session is SessionAccessState.Ready && !coordinatorAuthenticated) {
            coordinatorAuthenticated = true
            inviteCoordinator.onAuthenticated()
        } else if (state.session !is SessionAccessState.Ready && coordinatorAuthenticated) {
            coordinatorAuthenticated = false
            inviteCoordinator.onSignedOut()
        }
    }
    LaunchedEffect(inviteCoordinator, state.session) {
        inviteCoordinator.effects.collect { effect ->
            when (effect) {
                is GroupInviteEffect.OpenInviteLanding -> {
                    pendingInviteCode = effect.code
                    if (state.session is SessionAccessState.Ready) {
                        backStack.add(GroupsRoute.InviteLanding(effect.code))
                    } else {
                        // The access roots already expose the login-to-register path. The
                        // coordinator keeps the invite in secure storage while it is used.
                        backStack.resetTo(AccessRoute.Login)
                    }
                }
                is GroupInviteEffect.NavigateToGroup -> when (effect.status) {
                    InviteRedeemStatus.JOINED -> {
                        pendingInviteCode = null
                        inviteContext = null
                        backStack.add(GroupsRoute.AthleteRegistration(effect.groupId))
                    }
                    InviteRedeemStatus.PENDING -> {
                        backStack.add(GroupsRoute.InviteLanding(effect.inviteCode, requestSent = true))
                    }
                }
                is GroupInviteEffect.RedeemFailed -> {
                    backStack.add(
                        GroupsRoute.InviteLanding(
                            code = effect.code,
                            redeemError = effect.error.toRouteError(),
                        ),
                    )
                }
                is GroupInviteEffect.PendingInviteStorageFailed -> {
                    backStack.add(pendingInviteStorageFailureRoute(effect.code, pendingInviteCode))
                }
            }
        }
    }
    LaunchedEffect(pendingInviteCode) {
        if (pendingInviteCode == null) {
            inviteContext = null
            return@LaunchedEffect
        }
        inviteContext = when (val preview = inviteCoordinator.previewPending()) {
            is SaqzResult.Success -> RegisterInviteContext.preview(
                groupName = preview.value.groupName,
                inviterName = preview.value.inviterName,
                entryRequiresApproval = preview.value.entryRequiresApproval,
            )
            else -> RegisterInviteContext.Generic
        }
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
                // As duas saídas para a 1a são o mesmo desempilhar — a 1b só é alcançável
                // por cima do login. O que muda entre elas ("Entrar?" do e-mail duplicado
                // leva o e-mail junto) já aconteceu na ViewModel, no formulário que a 1a
                // projeta. Cadastrar não empilha a 1c: vira sessão, e quem reage a sessão é
                // o `reconcileAccessStack`.
                RegisterRoot(
                    onBack = pop,
                    onOpenLogin = pop,
                    inviteContext = inviteContext,
                )
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
            entry<AccessRoute.NewPassword> { route ->
                NewPasswordRoot(
                    token = route.token,
                    onBack = pop,
                    onFinish = { backStack.completePasswordReset(state.session) },
                    // Ticket morto pede o código de novo, e o 1e é exatamente a entrada
                    // anterior — é para ela reaparecer intacta que a rota do 1g carrega o
                    // e-mail. Empilhar um `ResetCode` novo deixaria dois na pilha.
                    onRestartCode = pop,
                )
            }
            entry<AccessRoute.PasswordChanged> {
                // Quem chega aqui já teve o stack colapsado por [completePasswordReset]:
                // a recuperação consumida saiu, e a base depende de a sessão estar viva.
                PasswordChangedScreen(
                    onSignIn = { backStack.finishPasswordChanged(state.session) },
                    onBack = pop,
                )
            }
            entry<AccessRoute.Bootstrap> {
                BootstrapAccessScreen(
                    state = state.session,
                    onIntent = { onIntent(AccessIntent.Session(it)) },
                )
            }
            entry<SaqzShellDestination> { route ->
                SaqzAppShell(
                    catalogEnabled = catalogEnabled,
                    initialTab = route.resolvedTab(),
                    financeTabVisible = state.session.administersAnyGroup(),
                    financeTab = {
                        FinanceOverviewRoot(onOpenGroup = { backStack.add(FinanceRoute.GroupCashbox(it)) })
                    },
                    homeTab = { onOpenGroups ->
                        HomeRoot(
                            onOpenGroup = { backStack.add(GroupsRoute.Details(it)) },
                            onOpenGroups = onOpenGroups,
                            onOpenGame = { groupId, gameId ->
                                backStack.add(GroupsRoute.GameDetail(groupId, gameId))
                            },
                            onOpenMembers = { backStack.add(GroupsRoute.Members(it)) },
                            onOpenCashbox = { backStack.add(FinanceRoute.GroupCashbox(it)) },
                            onOpenGameSettlement = { groupId, gameId ->
                                backStack.add(FinanceRoute.GameSettlement(groupId, gameId))
                            },
                            onOpenGameEditor = { backStack.add(GroupsRoute.GameEditor(it)) },
                            onOpenInvite = { backStack.add(GroupsRoute.Invite(it)) },
                        )
                    },
                    profileTab = {
                        OwnProfileRoot(
                            onOpenEditor = { backStack.add(ProfileRoute.Edit) },
                            onOpenPasswordRecovery = { backStack.add(AccessRoute.ForgotPassword) },
                            onSignOut = {
                                backStack.add(
                                    ProfileRoute.Exit(
                                        email = (state.session as? SessionAccessState.Ready)
                                            ?.session?.user?.email.orEmpty(),
                                    ),
                                )
                            },
                            refreshVersion = profileRefreshVersion,
                        )
                    },
                    banner = { onOpenHome ->
                        // Uma faixa por vez — duas seriam dois blocos escuros sobre o
                        // conteúdo —, e o desempate é por **estado da dívida**, não por
                        // ordem fixa (VUL-202):
                        //
                        // - **cobrança vencida ganha de tudo.** A ordem fixa "e-mail
                        //   primeiro" partia de que a faixa de e-mail acaba, e ela não
                        //   acaba: o VUL-76 tirou a trava do backend de propósito, então
                        //   quem nunca confirma entra e fica. Quem entrou por convite, não
                        //   abriu o e-mail e vive na aba Grupos passaria meses devendo sem
                        //   um sinal fora da Início — exatamente a aba que não visita;
                        // - **no prazo, o e-mail passa na frente.** Aí o argumento de
                        //   duração continua valendo, e a cobrança não se perde: a seção da
                        //   Home fica lá o tempo todo, e a faixa aparece assim que a
                        //   pendência vence ou o e-mail sai da frente.
                        //
                        // Quem decide é o `HomeOwnChargesBannerRoot`, porque `overdue` só
                        // existe no estado da Home; a faixa de e-mail entra lá como slot. O
                        // Root é montado sempre — inclusive com o e-mail na frente —, e é
                        // dele o recarregamento na volta ao app.
                        val ready = state.session as? SessionAccessState.Ready
                        HomeOwnChargesBannerRoot(
                            onOpenHome = onOpenHome,
                            emailBanner = if (ready != null && !ready.emailVerified) {
                                {
                                    EmailVerificationBanner(
                                        onRefresh = {
                                            onIntent(AccessIntent.Session(SessionIntent.RefreshEmailVerification))
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    },
                    groupsTab = {
                        // A aba Grupos é o 2n. `GroupsRoute.List` não vira `entry` porque
                        // seria um segundo host da mesma tela — e a barra do 10q fica sob
                        // a lista só enquanto o shell é quem a desenha.
                        GroupListRoot(
                            onOpenGroup = { backStack.add(GroupsRoute.Details(it)) },
                            // Com plano ativo com vaga de grupo o "+" de 2n atalha direto
                            // para o 2a (`GroupListEffect.OpenCreateGroup`); sem plano
                            // entitulador — ou limite atingido — ele abre o Fluxo 8 ·
                            // Planos, e o `PlanActive` traz a pessoa de volta ao 2a.
                            onCreateGroup = { backStack.add(GroupsRoute.Create) },
                            onOpenPlans = { backStack.add(SubscriptionsRoute.PlanSelection) },
                        )
                    },
                )
            }
            entry<ProfileRoute.Edit> {
                EditProfileRoot(
                    onSave = {
                        pop()
                        profileRefreshVersion++
                    },
                    onBack = pop,
                )
            }
            entry<ProfileRoute.Exit> { route ->
                ProfileExitRoot(
                    email = route.email,
                    onClose = pop,
                    onLogout = { onIntent(AccessIntent.ConfirmLogout) },
                )
            }
            entry<FinanceRoute.GroupCashbox> { route ->
                GroupCashboxRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    onMutationSuccess = { groupDetailsRefreshVersion++ },
                    refreshVersion = groupCashboxRefreshVersion,
                    onOpenNewEntry = { groupId ->
                        backStack.add(FinanceRoute.NewEntry(groupId))
                    },
                    onOpenStatement = { groupId ->
                        backStack.add(FinanceRoute.Statement(groupId))
                    },
                )
            }
            entry<FinanceRoute.Statement> { route ->
                StatementRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    onEffect = { effect ->
                        when (effect) {
                            is StatementEffect.OpenNewEntry -> backStack.add(
                                FinanceRoute.NewEntry(effect.groupId),
                            )
                        }
                    },
                    refreshVersion = statementRefreshVersion,
                )
            }
            entry<FinanceRoute.NewEntry> { route ->
                NewEntryRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    prefill = route.prefill,
                    onEffect = { effect ->
                        when (effect) {
                            NewEntryEffect.Saved -> {
                                pop()
                                statementRefreshVersion++
                                groupCashboxRefreshVersion++
                                settlementRefreshVersion++
                                groupDetailsRefreshVersion++
                            }
                        }
                    },
                )
            }
            entry<FinanceRoute.GameSettlement> { route ->
                GameSettlementRoot(
                    groupId = route.groupId,
                    gameId = route.gameId,
                    onBack = pop,
                    onMutationSuccess = { groupDetailsRefreshVersion++ },
                    refreshVersion = settlementRefreshVersion,
                    onOpenNewEntry = { groupId, localDate ->
                        backStack.add(FinanceRoute.NewEntry(groupId, NewEntryPrefill.GameCourt(localDate)))
                    },
                    onOpenCashbox = { groupId -> backStack.add(FinanceRoute.GroupCashbox(groupId)) },
                )
            }
            entry<SubscriptionsRoute.PlanSelection> {
                PlanSelectionRoot(
                    onBack = pop,
                    onContinue = { planId, cycle, couponCode ->
                        backStack.add(SubscriptionsRoute.Payment(planId.name, cycle.name, couponCode))
                    },
                )
            }
            entry<SubscriptionsRoute.Payment> { route ->
                PaymentRoot(
                    route = route,
                    onBack = pop,
                    onEffect = { effect ->
                        when (effect) {
                            // O recibo confirmou o pagamento: 8a/8c saem do stack junto — o
                            // voltar do sistema na 8d não pode reabrir um checkout já pago.
                            PaymentEffect.NavigateToPlanActive -> {
                                backStack.dropPlansSegment()
                                backStack.add(SubscriptionsRoute.PlanActive)
                            }
                        }
                    },
                )
            }
            entry<SubscriptionsRoute.PlanActive> {
                PlanActiveRoot(
                    onCreateGroup = {
                        backStack.dropPlansSegment()
                        backStack.add(GroupsRoute.Create)
                    },
                    onViewMyPlan = { backStack.add(SubscriptionsRoute.MyPlan) },
                )
            }
            entry<SubscriptionsRoute.MyPlan> { MyPlanRoot(onBack = pop) }
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
                    refreshVersion = groupDetailsRefreshVersion,
                )
            }
            entry<GroupsRoute.Invite> { route ->
                GroupInviteRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    onOpenMessagePreview = { groupName, inviteUrl ->
                        backStack.add(GroupsRoute.InviteMessagePreview(groupName, inviteUrl))
                    },
                    onOpenQr = { groupName, inviteUrl ->
                        backStack.add(GroupsRoute.InviteQr(groupName, inviteUrl))
                    },
                )
            }
            entry<GroupsRoute.InviteMessagePreview> { route ->
                InvitePreviewMessageRoot(
                    groupName = route.groupName,
                    inviteUrl = route.inviteUrl,
                    onBack = pop,
                )
            }
            entry<GroupsRoute.InviteQr> { route ->
                InviteQrRoot(
                    groupName = route.groupName,
                    inviteUrl = route.inviteUrl,
                    onBack = pop,
                )
            }
            entry<GroupsRoute.InviteLanding> { route ->
                InviteLandingRoot(
                    code = route.code,
                    onJoin = { groupId ->
                        inviteCoordinator.clearPendingInvite(route.code) {
                            pendingInviteCode = null
                            inviteContext = null
                            pop()
                            backStack.add(GroupsRoute.AthleteRegistration(groupId))
                        }
                    },
                    onRequest = {},
                    onBrowseOtherGroups = {
                        backStack.openInviteOtherGroups()
                    },
                    onExploreApp = {
                        backStack.openInviteExplore()
                    },
                    onOpenAnotherGroup = {
                        backStack.openInviteOtherGroups()
                    },
                    onRequestNewInvite = pop,
                    initialRequestSent = route.requestSent,
                    initialRedeemError = route.redeemError,
                )
            }
            entry<GroupsRoute.AthleteRegistration> { route ->
                AthleteRegistrationRoot(
                    groupId = route.groupId,
                    onSave = {
                        pop()
                        backStack.add(GroupsRoute.Details(route.groupId))
                    },
                    onBack = pop,
                )
            }
            entry<GroupsRoute.MemberEditor> { route ->
                MemberEditorRoot(
                    groupId = route.groupId,
                    userId = route.userId,
                    onBack = pop,
                    onRemove = pop,
                )
            }
            entry<GroupsRoute.Members> { route ->
                GroupMembersRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    // TODO(Fluxo 7 · Perfil): ver perfil do membro.
                    onOpenProfile = {},
                    onOpenMemberEditor = { userId ->
                        backStack.add(GroupsRoute.MemberEditor(route.groupId, userId))
                    },
                    onOpenInvite = { groupId ->
                        backStack.add(GroupsRoute.Invite(groupId))
                    },
                )
            }
            entry<GroupsRoute.Schedule> { route ->
                GroupScheduleRoot(
                    groupId = route.groupId,
                    onBack = pop,
                    // 4 · Detalhe do jogo da agenda.
                    onOpenGame = { gameId -> backStack.add(GroupsRoute.GameDetail(route.groupId, gameId)) },
                    refreshVersion = scheduleRefreshVersion,
                )
            }
            entry<GroupsRoute.GameEditor> { route ->
                GameEditorRoot(
                    groupId = route.groupId,
                    gameId = route.gameId,
                    onBack = pop,
                    onOpenGameDetail = { gameId -> backStack.add(GroupsRoute.GameDetail(route.groupId, gameId)) },
                    onSave = { groupDetailsRefreshVersion++ },
                )
            }
            entry<GroupsRoute.GameDetail> { route ->
                GameDetailRoot(
                    groupId = route.groupId,
                    gameId = route.gameId,
                    onBack = pop,
                    onOpenEditor = { backStack.add(GroupsRoute.GameEditor(route.groupId, route.gameId)) },
                    onOpenSettlement = {
                        backStack.add(FinanceRoute.GameSettlement(route.groupId, route.gameId))
                    },
                    onCancel = {
                        scheduleRefreshVersion++
                        pop()
                    },
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

private fun InviteError.toRouteError(): InviteLandingRouteError = when (this) {
    InviteError.InvalidOrExpired,
    InviteError.GroupDeleted,
    -> InviteLandingRouteError.Invalid
    is InviteError.Expired -> InviteLandingRouteError.Expired(expiredAt)
    is InviteError.RateLimited -> InviteLandingRouteError.RateLimited(retryAfterSeconds)
    InviteError.PlanLimit -> InviteLandingRouteError.PlanLimit
    is InviteError.DataFailure -> InviteLandingRouteError.Network
}

internal fun NavBackStack<NavKey>.openInviteExplore() {
    // "Explorar o app" é a entrada geral — o default do shell (aba Início, VUL-193).
    resetTo(SaqzShellDestination.Home)
}

/**
 * VUL-193: os callbacks do invite landing (BrowseOtherGroups e OpenAnotherGroup, mais o
 * back da top-bar) prometem a lista de grupos. O destino carrega `initialTab = Grupos`
 * para o shell abrir na aba certa — sem isto o reset cairia na aba Início (default).
 */
internal fun NavBackStack<NavKey>.openInviteOtherGroups() {
    resetTo(SaqzShellDestination.Groups)
}

internal fun pendingInviteStorageFailureRoute(
    code: String?,
    fallbackCode: String?,
): GroupsRoute.InviteLanding = GroupsRoute.InviteLanding(
    code = code ?: fallbackCode.orEmpty(),
    redeemError = InviteLandingRouteError.Network,
)

/**
 * O 1g→1h. A troca **consome** o ticket, então o que sobrou da recuperação sai do stack em
 * vez de ficar embaixo do 1h: o 1g reaberto pediria a senha de novo com um token morto na
 * mão, e o 1e, com um código já gasto. Nos dois casos a pessoa digitaria tudo outra vez
 * para levar um erro que não tem como entender.
 *
 * Sobra a base correta para a sessão atual, que é para onde o "Entrar agora" leva de qualquer
 * forma — o voltar do 1h, visível ou do sistema, passa a dar no mesmo lugar. Empilhar sobre o
 * formulário era o defeito; substituir só ele deixaria o mesmo defeito uma entrada acima.
 */
internal fun MutableList<NavKey>.completePasswordReset(session: SessionAccessState) {
    clear()
    add(session.passwordChangedDestination())
    add(AccessRoute.PasswordChanged)
}

internal fun MutableList<NavKey>.finishPasswordChanged(session: SessionAccessState) {
    clear()
    add(session.passwordChangedDestination())
}

/** Os papéis que o backend devolve para quem administra o grupo. */
private val AdministratorRoles = setOf("OWNER", "ADMIN")

/**
 * VUL-200: a aba Caixa só existe para quem administra algum grupo — o
 * `GET /api/me/finance/overview` já filtra por `owner_user_id = :actorId OR
 * memberships.role = 'ADMIN'` (CTE `administered_groups`), então para os outros ela abria
 * uma tela zerada. **Os dois lados do `OR` importam**: o dono entra pela coluna do grupo, e
 * a membership dele é `OWNER` — por isso [AdministratorRoles] tem os dois papéis, e reduzir
 * a lista a `ADMIN` esconderia a aba de quem é dono do próprio grupo.
 *
 * A fonte do papel é a **própria sessão**, não o `ownProfile()`: o `AccessSession` já chega
 * com `memberships` e `role` do `PUT /api/session`, e o shell só existe em
 * [SessionAccessState.Ready] (o gate garante). Ou seja, o papel é conhecido **antes do
 * primeiro quadro** do shell — nenhuma chamada de rede a mais, nenhuma guarda de geração, e
 * principalmente nenhuma piscada da aba aparecendo depois que o perfil carrega.
 *
 * `role` é `String` crua de propósito no domínio de acesso ("membership role preserves
 * access owned raw value"), então a comparação é nossa; `uppercase` porque quem escolhe a
 * caixa do valor é o backend.
 *
 * ponytail: as memberships são as do bootstrap da sessão. Criar o primeiro grupo no meio da
 * sessão só acende a aba no próximo bootstrap — o caixa daquele grupo continua a um toque
 * pelo detalhe. Se isso incomodar, o conserto é um `SessionIntent` de recarregar a sessão
 * depois do 2a, não um segundo carregador de papel aqui.
 */
internal fun SessionAccessState.administersAnyGroup(): Boolean =
    this is SessionAccessState.Ready &&
        session.memberships.any { it.role.value.uppercase() in AdministratorRoles }

private fun SessionAccessState.passwordChangedDestination(): NavKey = when (this) {
    is SessionAccessState.Ready -> SaqzShellDestination.Home
    SessionAccessState.SignedOut,
    is SessionAccessState.CompletingIdentity,
    SessionAccessState.Bootstrapping,
    SessionAccessState.BootstrapError,
    -> AccessRoute.Login
}

/**
 * O 8d→2a: o pagamento já aconteceu (8a→8b→8c→8d), então o segmento do fluxo de planos sai
 * do stack antes do formulário de criação entrar — sem isso, o voltar do 2a devolveria a
 * pessoa a uma assinatura que já foi paga, sem como "desfazer" navegando.
 */
internal fun MutableList<NavKey>.dropPlansSegment() {
    while (lastOrNull() is SubscriptionsRoute) removeLastOrNull()
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
        // 4 · Criar jogo: abre o editor com gameId null.
        is GroupDetailsEffect.OpenCreateGame -> add(GroupsRoute.GameEditor(effect.groupId))
        // 4c · O card do próximo jogo e a agenda abrem o mesmo detalhe.
        is GroupDetailsEffect.OpenGame -> add(GroupsRoute.GameDetail(effect.groupId, effect.gameId))
        is GroupDetailsEffect.OpenCashbox -> add(FinanceRoute.GroupCashbox(effect.groupId))
        is GroupDetailsEffect.OpenInviteLink -> add(GroupsRoute.Invite(effect.groupId))
        // TODO(Fluxo 9 · Quadra): abrir a quadra no mapa é port nativo, não rota.
        GroupDetailsEffect.OpenMap -> Unit
        // VUL-203: copiar o Pix é área de transferência e o `GroupDetailsRoot` já consome
        // o efeito antes daqui. O ramo existe porque o `when` é sobre o tipo inteiro —
        // não é `else`, e nenhum efeito de navegação cai nele.
        is GroupDetailsEffect.CopyPix -> Unit
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
    is SessionAccessState.Ready -> SaqzShellDestination.Home
}
