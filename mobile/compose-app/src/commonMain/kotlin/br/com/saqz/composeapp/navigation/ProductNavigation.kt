package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.designsystem.component.SaqzBottomNav
import br.com.saqz.designsystem.component.SaqzBottomNavItem
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.ui.GroupsDestinationContent
import br.com.saqz.composeapp.home.AuthenticatedHomeScreen
import br.com.saqz.navigation.NavigationMode
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductNavigationHost
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab
import br.com.saqz.navigation.access.installAccessEntries
import br.com.saqz.navigation.access.isAccessSession
import br.com.saqz.navigation.access.reconcileAccessStack
import br.com.saqz.navigation.finance.installFinanceEntries
import br.com.saqz.navigation.groups.installGroupsEntries
import br.com.saqz.navigation.serialization.navigationSavedStateConfiguration
import br.com.saqz.composeapp.SaqzPlatformDependencies
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * T23 composition-root entry: resolves the post-Wave-2 orchestrator ([AccessViewModel]) and
 * the Groups destination projection through Koin (one DI graph, no `:navigation` type
 * exported), reconciles the Groups projection with the session, forwards native share /
 * deep-link effects outside `:navigation`, and drives [ProductNavigation].
 */
@Composable
internal fun ProductNavigationRoute(
    dependencies: SaqzPlatformDependencies,
    accessViewModelOverride: AccessViewModel? = null,
) {
    val accessViewModel = accessViewModelOverride ?: koinViewModel<AccessViewModel>(key = "authenticated-access")
    val groupsViewModel = koinViewModel<GroupsNavigationViewModel>(key = "groups-navigation")
    val state by accessViewModel.state.collectAsState()
    val groupsNavigation by groupsViewModel.state.collectAsState()
    val sessionMemberships = (state.session as? SessionAccessState.Ready)?.session?.memberships.orEmpty()

    LaunchedEffect(groupsViewModel, state.selection, sessionMemberships) {
        groupsViewModel.onIntent(
            GroupsNavigationIntent.Reconcile(
                selection = state.selection,
                memberships = sessionMemberships.toSafeGroupSelectionMemberships(),
            ),
        )
    }
    LaunchedEffect(accessViewModel, dependencies.share) {
        accessViewModel.effects.collect { effect ->
            handleAccessEffect(effect, dependencies.share, groupsViewModel::onIntent, accessViewModel::onIntent)
        }
    }

    ProductNavigation(
        state = state,
        onIntent = accessViewModel::onIntent,
        groupsNavigation = groupsNavigation,
        onGroupsIntent = groupsViewModel::onIntent,
    )
}

/**
 * T23 (MODNAV-01/03, ACCESSNAV-04, BACK-04, TAB-01): integrates the `:navigation`
 * [ProductNavigationHost] at the composition root. `:compose-app` stays the sole
 * framework exporter -- only the app-owned back stacks, the shared session, and the
 * feature-screen content bindings are supplied here; no `:navigation` type leaves the
 * exported `SaqzMobile` surface.
 *
 * The four tab back stacks (Início/Grupos/Avisos/Mais) and the access stack are
 * app-owned [rememberNavBackStack]s. [NavigationSession] is the single writer that both
 * the bottom nav and `NavDisplay`/TopBar back drive. Route history lives only in the
 * session and the stacks; the orchestrator ([AccessRootSnapshot]) stays authoritative
 * for domain state and is projected into stack reconciliation.
 *
 * Content bindings reuse the existing feature screens ([GroupsDestinationContent],
 * [AuthenticatedHomeScreen]) so UI/behavior is preserved (REG-01). The Groups
 * destination projection ([groupsNavigation]) supplies each route's ancillary data
 * while the session owns which key is on top.
 */
@Composable
internal fun ProductNavigation(
    state: AccessRootSnapshot,
    onIntent: (AccessIntent) -> Unit,
    groupsNavigation: GroupsNavigationState,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
    groupPhotoState: GroupPhotoState = GroupPhotoState(),
    groupPhotoDetailPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    gameDetailState: GameDetailState? = null,
    onGameDetailIntent: (GameDetailIntent) -> Unit = {},
    sessionStateMachine: SessionAccessStateMachine = koinInject(),
    modifier: Modifier = Modifier,
) {
    val accessStack: NavBackStack<NavKey> =
        rememberNavBackStack(navigationSavedStateConfiguration, AccessRouteRoot)
    val homeStack: NavBackStack<NavKey> =
        rememberNavBackStack(navigationSavedStateConfiguration, ProductRoute.AppHome)
    val groupsStack: NavBackStack<NavKey> =
        rememberNavBackStack(navigationSavedStateConfiguration, GroupsRoute.Selector)
    val noticesStack: NavBackStack<NavKey> =
        rememberNavBackStack(navigationSavedStateConfiguration, GroupsRoute.Notices)
    val moreStack: NavBackStack<NavKey> =
        rememberNavBackStack(navigationSavedStateConfiguration, GroupsRoute.More)

    val session = rememberNavigationSession(homeStack, groupsStack, noticesStack, moreStack)

    // ACCESSNAV-03: reconcile the access stack with the authoritative session/auth state.
    LaunchedEffect(state.session, state.authentication.screen) {
        reconcileAccessStack(accessStack, state.session, state.authentication.screen)
    }
    // GROUPNAV-06/STATE-01..02: reconcile the Groups root with GroupSelectionState.
    LaunchedEffect(state.selection) {
        session.reconcileGroupSelection(state.selection)
    }
    // RESTORE-02/03: clear authenticated stacks on logout; clear group scope on switch.
    LaunchedEffect(state.session) {
        if (state.session == SessionAccessState.SignedOut) session.clearAuthenticated()
    }
    val selectedGroupId = (state.selection as? GroupSelectionState.Selected)?.group?.group?.id?.value
    LaunchedEffect(selectedGroupId) {
        if (selectedGroupId != null) session.clearGroupScope(selectedGroupId)
    }

    val mode = if (isAccessSession(state.session)) NavigationMode.ACCESS else NavigationMode.AUTHENTICATED

    val titleFor: @Composable (NavKey) -> String = { key -> routeTitle(key) }
    val bottomBar: @Composable () -> Unit = { ProductBottomBar(session, onGroupsIntent) }
    val groupsContent: @Composable (NavKey) -> Unit = { key ->
        GroupsRouteContentBinding(
            key = key,
            state = state,
            groupsNavigation = groupsNavigation,
            onIntent = onIntent,
            onGroupsIntent = onGroupsIntent,
            groupPhotoState = groupPhotoState,
            groupPhotoDetailPreview = groupPhotoDetailPreview,
            loadListPhoto = loadListPhoto,
            gameDetailState = gameDetailState,
            onGameDetailIntent = onGameDetailIntent,
        )
    }

    val provider = entryProvider<NavKey> {
        installAccessEntries(sessionStateMachine)
        installGroupsEntries(session, titleFor, bottomBar, groupsContent)
        installFinanceEntries(session, titleFor, groupsContent)
    }

    ProductNavigationHost(
        mode = mode,
        selectedTab = session.selectedTab,
        accessBackStack = accessStack,
        homeBackStack = homeStack,
        groupsBackStack = groupsStack,
        noticesBackStack = noticesStack,
        moreBackStack = moreStack,
        entryProvider = provider,
        onBack = { session.goBack() },
        modifier = modifier,
    )
}

/** Access stack root: `Starting` until auth is observed, else the reconciler replaces it. */
private val AccessRouteRoot: NavKey = br.com.saqz.access.navigation.AccessRoute.Starting

@Composable
private fun rememberNavigationSession(
    homeStack: NavBackStack<NavKey>,
    groupsStack: NavBackStack<NavKey>,
    noticesStack: NavBackStack<NavKey>,
    moreStack: NavBackStack<NavKey>,
): NavigationSession = androidx.compose.runtime.remember(homeStack, groupsStack, noticesStack, moreStack) {
    NavigationSession(
        stacks = mapOf(
            ProductTab.HOME to homeStack,
            ProductTab.GROUPS to groupsStack,
            ProductTab.NOTICES to noticesStack,
            ProductTab.MORE to moreStack,
        ),
    )
}

/** MENU-13: the four validated tabs, wired to [NavigationSession.selectTab]. */
@Composable
private fun ProductBottomBar(
    session: NavigationSession,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
) {
    val selected = session.selectedTab
    SaqzBottomNav(
        items = listOf(
            tabItem("Início", ProductTab.HOME, selected) { session.selectTab(ProductTab.HOME) },
            tabItem("Grupos", ProductTab.GROUPS, selected) {
                session.selectTab(ProductTab.GROUPS)
                onGroupsIntent(GroupsNavigationIntent.OpenGroups)
            },
            tabItem("Avisos", ProductTab.NOTICES, selected) { session.selectTab(ProductTab.NOTICES) },
            tabItem("Mais", ProductTab.MORE, selected) { session.selectTab(ProductTab.MORE) },
        ),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
    )
}

private fun tabItem(label: String, tab: ProductTab, selected: ProductTab, onClick: () -> Unit) =
    SaqzBottomNavItem(label = label, selected = tab == selected, onClick = onClick, icon = { Text("•") })

// SCOPED-chrome TopBar title per route. Groups strings are module-internal to
// :features:groups (no publicResClass), so the SCOPED chrome title is supplied here; the
// screen body (via GroupsDestinationContent) still renders its own localized heading.
@Composable
private fun routeTitle(key: NavKey): String = when (key) {
    GroupsRoute.People -> "Pessoas"
    GroupsRoute.Games -> "Jogos"
    GroupsRoute.Notices -> "Avisos"
    is GroupsRoute.GameDetail -> "Detalhes do jogo"
    FinanceRoute.Finance -> "Finanças"
    FinanceRoute.OwnCharges -> "Minhas cobranças"
    else -> ""
}

/**
 * Renders the feature screen for a Groups/AppHome/Finance route by reusing the existing
 * [GroupsDestinationContent] (REG-01). [AppHome] renders the compose-app home screen; every
 * other key projects the current [groupsNavigation] ancillary data under the route's key.
 */
@Composable
private fun GroupsRouteContentBinding(
    key: NavKey,
    state: AccessRootSnapshot,
    groupsNavigation: GroupsNavigationState,
    onIntent: (AccessIntent) -> Unit,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
    groupPhotoState: GroupPhotoState,
    groupPhotoDetailPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)?,
    gameDetailState: GameDetailState?,
    onGameDetailIntent: (GameDetailIntent) -> Unit,
) {
    if (key == ProductRoute.AppHome) {
        AuthenticatedHomeScreen()
        return
    }
    val destination = key.toGroupsDestination() ?: return
    GroupsDestinationContent(
        navigation = groupsNavigation.copy(destination = destination),
        administration = state.administration,
        groupPhotoState = groupPhotoState,
        groupPhotoPreview = groupPhotoDetailPreview,
        gameDetailState = gameDetailState,
        onGameDetailIntent = onGameDetailIntent,
        onNavigationIntent = onGroupsIntent,
        onOpenSettings = { onIntent(AccessIntent.OpenSettings) },
        onSelectGroup = { groupId ->
            onGroupsIntent(GroupsNavigationIntent.OpenGroup(groupId))
            onIntent(AccessIntent.Selection(GroupSelectionIntent.Select(groupId)))
        },
        onOpenCreateGroup = { onIntent(AccessIntent.OpenCreateGroup) },
        onRetryGroup = { onIntent(AccessIntent.Selection(GroupSelectionIntent.Retry)) },
        onOpenInvite = { onIntent(AccessIntent.OpenInvite) },
        onRequestLogout = { onIntent(AccessIntent.RequestLogout) },
        loadListPhoto = loadListPhoto,
    )
}

private fun NavKey.toGroupsDestination(): GroupsDestination? = when (this) {
    GroupsRoute.Setup -> GroupsDestination.SETUP
    GroupsRoute.Selector -> GroupsDestination.SELECTOR
    GroupsRoute.Loading -> GroupsDestination.LOADING
    GroupsRoute.LoadError -> GroupsDestination.LOAD_ERROR
    GroupsRoute.GroupHome -> GroupsDestination.HOME
    GroupsRoute.ProfileCompletion -> GroupsDestination.PROFILE_COMPLETION
    GroupsRoute.People -> GroupsDestination.PEOPLE
    GroupsRoute.Games -> GroupsDestination.GAMES
    is GroupsRoute.GameDetail -> GroupsDestination.GAME_DETAIL
    GroupsRoute.Notices -> GroupsDestination.NOTICES
    GroupsRoute.More -> GroupsDestination.MORE
    FinanceRoute.Finance -> GroupsDestination.FINANCE
    FinanceRoute.OwnCharges -> GroupsDestination.OWN_CHARGES
    else -> null
}
