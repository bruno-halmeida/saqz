package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.composeapp.di.GameDetailViewModelParameters
import br.com.saqz.composeapp.di.GroupSetupViewModelParameters
import br.com.saqz.composeapp.home.AuthenticatedHomeScreen
import br.com.saqz.designsystem.component.SaqzBottomNav
import br.com.saqz.designsystem.component.SaqzBottomNavItem
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.effects.ObserveAsEvents
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.navigation.FinanceRoute
import br.com.saqz.groups.navigation.GroupsRoute
import br.com.saqz.groups.presentation.GroupFinanceVisibility
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.games.detail.GameDetailEffect
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.GroupPhotoCoordinator
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupListPhotoLoader
import br.com.saqz.groups.presentation.route.FinancePlaceholderMode
import br.com.saqz.groups.presentation.route.FinancePlaceholderRouteViewModel
import br.com.saqz.groups.presentation.route.GroupAdministrationRouteMode
import br.com.saqz.groups.presentation.route.GroupAdministrationRouteViewModel
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderMode
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderRouteViewModel
import br.com.saqz.groups.presentation.route.GroupHomeRouteEffect
import br.com.saqz.groups.presentation.route.GroupHomeRouteViewModel
import br.com.saqz.groups.presentation.route.GroupInviteRouteEffect
import br.com.saqz.groups.presentation.route.GroupInviteRouteIntent
import br.com.saqz.groups.presentation.route.GroupInviteRouteViewModel
import br.com.saqz.groups.presentation.athlete.AthleteRosterViewModel
import br.com.saqz.groups.presentation.athlete.OwnAthleteProfileViewModel
import br.com.saqz.groups.presentation.route.GroupSelectionRouteViewModel
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupEffect
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.ui.athlete.AthleteRosterScreen
import br.com.saqz.groups.ui.athlete.OwnAthleteProfileSection
import br.com.saqz.groups.ui.athlete.PositionOnboardingHost
import br.com.saqz.groups.ui.games.detail.GameDetailScreen
import br.com.saqz.groups.ui.route.FinancePlaceholderRoot
import br.com.saqz.groups.ui.route.GroupHomeRoot
import br.com.saqz.groups.ui.route.GroupInviteRoot
import br.com.saqz.groups.ui.route.GroupLoadErrorRoot
import br.com.saqz.groups.ui.route.GroupLoadingRoot
import br.com.saqz.groups.ui.route.GroupMembershipsRoot
import br.com.saqz.groups.ui.route.GroupPlaceholderRoot
import br.com.saqz.groups.ui.route.GroupSelectorRoot
import br.com.saqz.groups.ui.route.GroupSettingsRoot
import br.com.saqz.groups.ui.setup.GroupSetupScreen
import br.com.saqz.navigation.NavigationMode
import br.com.saqz.navigation.NavigationSession
import br.com.saqz.navigation.ProductNavigationHost
import br.com.saqz.navigation.ProductRoute
import br.com.saqz.navigation.ProductTab
import br.com.saqz.navigation.access.installAccessEntries
import br.com.saqz.navigation.access.isAccessSession
import br.com.saqz.navigation.access.reconcileAccessStack
import br.com.saqz.navigation.effect.handleGroupContentEffect
import br.com.saqz.navigation.effect.handleGroupHomeEffect
import br.com.saqz.navigation.effect.handleGroupSelectionEffect
import br.com.saqz.navigation.effect.handleOpenAttendanceGame
import br.com.saqz.navigation.finance.installFinanceEntries
import br.com.saqz.navigation.groups.installGroupsEntries
import br.com.saqz.navigation.serialization.navigationSavedStateConfiguration
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Composition-root entry (T23/T24): resolves the orchestrator ([AccessViewModel]) and the
 * shared group-photo runtime, then drives [ProductNavigation]. All route content is backed
 * by the per-route adapter ViewModels (T11-T15) through the feature-owned Roots -- the
 * legacy enum projection is no longer consulted.
 */
@Composable
internal fun ProductNavigationRoute(
    dependencies: SaqzPlatformDependencies,
    accessViewModelOverride: AccessViewModel? = null,
    groupPhotos: GroupPhotoRuntimeDependencies = dependencies.groupPhotos,
) {
    val accessViewModel = accessViewModelOverride ?: koinViewModel<AccessViewModel>(key = "authenticated-access")
    val state by accessViewModel.state.collectAsState()

    // Shared photo runtime (moved from the legacy AuthenticatedAccessRoute): one cache,
    // one image loader, one coordinator shared by GroupHome, Selector, and CreateGroup.
    val photoScope = rememberCoroutineScope()
    val platformContext = LocalPlatformContext.current
    val photoCache = remember(accessViewModel) { CoilGroupPhotoCache() }
    val photoImageLoader = remember(accessViewModel, platformContext) {
        ImageLoader.Builder(platformContext).build()
    }
    DisposableEffect(photoCache, photoImageLoader) {
        onDispose {
            photoImageLoader.shutdown()
            photoCache.shutdown()
        }
    }
    val photoCoordinator = remember(accessViewModel, groupPhotos, photoCache) {
        GroupPhotoCoordinator(
            gateway = accessViewModel.groupPhotoGateway,
            selections = groupPhotos.selection,
            encoder = groupPhotos.encoder,
            cache = photoCache,
            scope = photoScope,
        )
    }
    val listPhotoLoader = remember(accessViewModel, photoCache) {
        GroupListPhotoLoader(
            gateway = accessViewModel.groupPhotoGateway,
            cache = photoCache,
        )
    }
    val groupPhotoState by photoCoordinator.state.collectAsState()
    LaunchedEffect(groupPhotoState.groupId, groupPhotoState.existing?.preview) {
        if (groupPhotoState.existing == null) {
            photoImageLoader.memoryCache?.clear()
        }
    }
    // Load the selected group's photo; release it when membership to the loaded group is gone.
    val selectedVersioned = (state.selection as? GroupSelectionState.Selected)?.group
    LaunchedEffect(selectedVersioned?.group?.id?.value, selectedVersioned?.versionToken?.value) {
        val selected = selectedVersioned ?: return@LaunchedEffect
        photoCoordinator.onIntent(
            GroupPhotoIntent.Load(selected.group.id.value, selected.versionToken.value),
        )
    }
    val sessionMemberships = (state.session as? SessionAccessState.Ready)?.session?.memberships.orEmpty()
    LaunchedEffect(sessionMemberships, groupPhotoState.groupId, state.selection) {
        val loadedGroupId = groupPhotoState.groupId ?: return@LaunchedEffect
        val selectedGroupId = (state.selection as? GroupSelectionState.Selected)?.group?.group?.id?.value
        if (selectedGroupId != loadedGroupId && sessionMemberships.none { it.groupId.value == loadedGroupId }) {
            photoCoordinator.onIntent(GroupPhotoIntent.MembershipLost)
        }
    }
    LaunchedEffect(state.session) {
        if (state.session == SessionAccessState.SignedOut) {
            photoCoordinator.onIntent(GroupPhotoIntent.Logout)
        }
    }

    ProductNavigation(
        state = state,
        onIntent = accessViewModel::onIntent,
        accessEffects = accessViewModel.effects,
        share = dependencies.share,
        attendanceShare = dependencies.attendanceShare,
        photoCoordinator = photoCoordinator,
        loadListPhoto = listPhotoLoader::load,
        groupPhotoDetailPreview = { handle, modifier ->
            GroupPhotoPreview(handle, photoCache, photoImageLoader, modifier)
        },
        groupSetupPhotoPreview = { handle, modifier ->
            GroupPhotoPreview(handle, groupPhotos.previews, photoImageLoader, modifier) ==
                br.com.saqz.groups.presentation.photo.GroupPhotoRenderState.SUCCESS
        },
    )
}

/**
 * The one product `NavDisplay` (MODNAV-01, T23/T24): five app-owned back stacks, one
 * [NavigationSession] writer, and per-route entry content backed by the T11-T15 route
 * adapters through the feature-owned Roots. Route history lives only in the session and
 * stacks (GROUPNAV-05); the orchestrator remains authoritative for auth/session/selection.
 */
@Composable
internal fun ProductNavigation(
    state: AccessRootSnapshot,
    onIntent: (AccessIntent) -> Unit,
    accessEffects: kotlinx.coroutines.flow.Flow<AccessUiEffect>? = null,
    share: NativeSharePort? = null,
    attendanceShare: br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort? = null,
    photoCoordinator: GroupPhotoCoordinator? = null,
    loadListPhoto: (suspend (String) -> br.com.saqz.groups.presentation.photo.ExistingGroupPhoto?)? = null,
    groupPhotoDetailPreview: (
        @Composable (
            br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle,
            Modifier,
        ) -> br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
    )? = null,
    groupSetupPhotoPreview: (
        @Composable (
            br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle,
            Modifier,
        ) -> Boolean
    )? = null,
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
    // GROUPNAV-06/STATE-01..02 + RESTORE-03: on switch, clear the group scope BEFORE
    // reconciling the Groups root. As two separate effects the reconciled GroupHome
    // root was immediately reset back to the initial Selector root by clearGroupScope,
    // leaving a freshly selected group unreachable until the next selection emission.
    LaunchedEffect(state.selection) {
        (state.selection as? GroupSelectionState.Selected)?.group?.group?.id?.value?.let {
            session.clearGroupScope(it)
        }
        session.reconcileGroupSelection(state.selection)
    }
    // RESTORE-02: clear authenticated stacks on logout.
    LaunchedEffect(state.session) {
        if (state.session == SessionAccessState.SignedOut) session.clearAuthenticated()
    }
    // LIFE-04: orchestrator one-off effects -> session mutations.
    if (accessEffects != null) {
        ObserveAsEvents(accessEffects) { effect ->
            when (effect) {
                is AccessUiEffect.OpenAttendanceGame -> handleOpenAttendanceGame(session, effect.gameId)
            }
        }
    }

    val mode = if (isAccessSession(state.session)) NavigationMode.ACCESS else NavigationMode.AUTHENTICATED

    val titleFor: @Composable (NavKey) -> String = { key -> routeTitle(key) }
    val bottomBar: @Composable () -> Unit = { ProductBottomBar(session) }
    val groupsContent: @Composable (NavKey) -> Unit = { key ->
        GroupsEntryContent(
            key = key,
            session = session,
            state = state,
            onIntent = onIntent,
            share = share,
            attendanceShare = attendanceShare,
            photoCoordinator = photoCoordinator,
            loadListPhoto = loadListPhoto,
            groupPhotoDetailPreview = groupPhotoDetailPreview,
            groupSetupPhotoPreview = groupSetupPhotoPreview,
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
        // Legacy observable contract: exactly one active product destination host
        // (rotation/recreation tests count this tag and assert catalog never leaks).
        modifier = modifier.testTag("authenticated-access-destination"),
    )
    if (mode == NavigationMode.AUTHENTICATED) {
        PositionOnboardingHost(koinViewModel())
    }
}

/**
 * Per-route entry content (T24/T25): each route acquires its adapter ViewModel from the
 * entry-scoped store (koinViewModel resolves against the NavEntry's ViewModelStoreOwner)
 * and renders its feature-owned Root; typed effects translate through the T20 handlers.
 */
@Composable
private fun GroupsEntryContent(
    key: NavKey,
    session: NavigationSession,
    state: AccessRootSnapshot,
    onIntent: (AccessIntent) -> Unit,
    share: NativeSharePort?,
    attendanceShare: br.com.saqz.groups.domain.attendance.share.NativeAttendanceSharePort?,
    photoCoordinator: GroupPhotoCoordinator?,
    loadListPhoto: (suspend (String) -> br.com.saqz.groups.presentation.photo.ExistingGroupPhoto?)?,
    groupPhotoDetailPreview: (
        @Composable (
            br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle,
            Modifier,
        ) -> br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
    )?,
    groupSetupPhotoPreview: (@Composable (br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle, Modifier) -> Boolean)?,
) {
    when (key) {
        ProductRoute.AppHome -> AuthenticatedHomeScreen()

        GroupsRoute.Setup -> Unit

        GroupsRoute.Selector -> {
            val viewModel = koinViewModel<GroupSelectionRouteViewModel>()
            ObserveAsEvents(viewModel.effects) { handleGroupSelectionEffect(session, it) }
            GroupSelectorRoot(viewModel, loadListPhoto, groupPhotoDetailPreview)
        }

        GroupsRoute.Loading -> GroupLoadingRoot()

        GroupsRoute.LoadError -> {
            val viewModel = koinViewModel<GroupSelectionRouteViewModel>()
            ObserveAsEvents(viewModel.effects) { handleGroupSelectionEffect(session, it) }
            GroupLoadErrorRoot(viewModel)
        }

        GroupsRoute.GroupHome -> {
            val coordinator = photoCoordinator ?: return
            val viewModel = koinViewModel<GroupHomeRouteViewModel>(parameters = { parametersOf(coordinator) })
            ObserveAsEvents(viewModel.effects) { effect ->
                if (!handleGroupHomeEffect(session, effect)) {
                    when (effect) {
                        GroupHomeRouteEffect.SwitchGroup -> onIntent(AccessIntent.SwitchGroup)
                        GroupHomeRouteEffect.ConfirmLogout -> onIntent(AccessIntent.ConfirmLogout)
                        else -> Unit
                    }
                }
            }
            GroupHomeRoot(
                viewModel = viewModel,
                groupPhotoPreview = groupPhotoDetailPreview,
                onNavigate = { intent -> session.navigate(intent) },
            )
        }

        GroupsRoute.ProfileCompletion -> PlaceholderEntry(GroupContentPlaceholderMode.PROFILE_COMPLETION, session)
        GroupsRoute.People -> {
            val rosterViewModel = koinViewModel<AthleteRosterViewModel>()
            val rosterState by rosterViewModel.state.collectAsState()
            val canManage = state.administration.actions.canManageRoles
            AthleteRosterScreen(
                state = rosterState,
                canManage = canManage,
                onIntent = rosterViewModel::onIntent,
            )
        }
        GroupsRoute.Games -> PlaceholderEntry(GroupContentPlaceholderMode.GAMES, session)
        GroupsRoute.Notices -> PlaceholderEntry(GroupContentPlaceholderMode.NOTICES, session)
        GroupsRoute.More -> PlaceholderEntry(
            GroupContentPlaceholderMode.MORE,
            session,
            athleteProfile = {
                val profileViewModel = koinViewModel<OwnAthleteProfileViewModel>()
                val profileState by profileViewModel.state.collectAsState()
                OwnAthleteProfileSection(state = profileState, onIntent = profileViewModel::onIntent)
            },
        )

        is GroupsRoute.GameDetail -> {
            val selected = state.administration.group?.group
            if (selected == null) {
                SaqzLoadingState(Modifier.fillMaxSize().testTag(GroupsNavigationTags.GameDetail))
            } else {
                val viewModel = koinViewModel<GameDetailViewModel>(
                    key = "game-detail-${selected.id.value}-${key.gameId}",
                    parameters = {
                        parametersOf(
                            GameDetailViewModelParameters(
                                groupId = selected.id.value,
                                gameId = key.gameId,
                                role = selected.role,
                            ),
                        )
                    },
                )
                val detailState by viewModel.state.collectAsState()
                ObserveAsEvents(viewModel.effects) { effect ->
                    when (effect) {
                        is GameDetailEffect.ShareAttendanceLink -> attendanceShare?.shareLink(effect.url.value) { result ->
                            viewModel.onIntent(
                                GameDetailIntent.ReportAttendanceShareResult(result is NativeAttendanceShareResult.Success),
                            )
                        }
                        is GameDetailEffect.ShareAttendanceImage -> attendanceShare?.shareImage(effect.image) { result ->
                            viewModel.onIntent(
                                GameDetailIntent.ReportAttendanceShareResult(result is NativeAttendanceShareResult.Success),
                            )
                        }
                        else -> Unit
                    }
                }
                Box(Modifier.fillMaxSize().testTag(GroupsNavigationTags.GameDetail)) {
                    GameDetailScreen(detailState, viewModel::onIntent)
                }
            }
        }

        GroupsRoute.Settings -> {
            val viewModel = koinViewModel<GroupAdministrationRouteViewModel>(
                parameters = { parametersOf(GroupAdministrationRouteMode.SETTINGS) },
            )
            GroupSettingsRoot(viewModel, onBack = { session.goBack() })
        }

        GroupsRoute.Memberships -> {
            val viewModel = koinViewModel<GroupAdministrationRouteViewModel>(
                parameters = { parametersOf(GroupAdministrationRouteMode.MEMBERSHIPS) },
            )
            GroupMembershipsRoot(viewModel, onBack = { session.goBack() })
        }

        GroupsRoute.Invite -> {
            val inviteViewModel = koinViewModel<GroupInviteRouteViewModel>()
            val administrationViewModel = koinViewModel<GroupAdministrationRouteViewModel>(
                parameters = { parametersOf(GroupAdministrationRouteMode.SETTINGS) },
            )
            ObserveAsEvents(inviteViewModel.effects) { effect ->
                when (effect) {
                    is GroupInviteRouteEffect.RequestShare -> share?.share(
                        effect.url,
                        object : ResultCallback {
                            override fun complete(result: OperationResult) {
                                inviteViewModel.onIntent(
                                    GroupInviteRouteIntent.ShareFinished(result is OperationResult.Success),
                                )
                            }
                        },
                    )
                }
            }
            GroupInviteRoot(inviteViewModel, administrationViewModel, onBack = { session.goBack() })
        }

        GroupsRoute.CreateGroup -> CreateGroupEntry(
            session = session,
            photoCoordinator = photoCoordinator,
            groupSetupPhotoPreview = groupSetupPhotoPreview,
        )

        FinanceRoute.Finance -> {
            val viewModel = koinViewModel<FinancePlaceholderRouteViewModel>(
                parameters = { parametersOf(FinancePlaceholderMode.FINANCE) },
            )
            FinancePlaceholderRoot(viewModel, onBack = { session.goBack() })
        }

        FinanceRoute.OwnCharges -> {
            val viewModel = koinViewModel<FinancePlaceholderRouteViewModel>(
                parameters = { parametersOf(FinancePlaceholderMode.OWN_CHARGES) },
            )
            FinancePlaceholderRoot(viewModel, onBack = { session.goBack() })
        }

        else -> Unit
    }
}

@Composable
private fun PlaceholderEntry(
    mode: GroupContentPlaceholderMode,
    session: NavigationSession,
    athleteProfile: (@Composable () -> Unit)? = null,
) {
    val viewModel = koinViewModel<GroupContentPlaceholderRouteViewModel>(
        key = "group-placeholder-$mode",
        parameters = { parametersOf(mode) },
    )
    ObserveAsEvents(viewModel.effects) { effect ->
        handleGroupContentEffect(
            session = session,
            effect = effect,
            canManageFinance = viewModel.state.value.access.financeVisibility == GroupFinanceVisibility.ORGANIZER,
        )
    }
    GroupPlaceholderRoot(viewModel, mode, onBack = { session.goBack() }, athleteProfile = athleteProfile)
}

/** CreateGroup entry: existing GroupSetupViewModel + the photo-upload bridge, entry-scoped. */
@Composable
private fun CreateGroupEntry(
    session: NavigationSession,
    photoCoordinator: GroupPhotoCoordinator?,
    groupSetupPhotoPreview: (@Composable (br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle, Modifier) -> Boolean)?,
) {
    val requestIds = koinInject<RequestIdGenerator>()
    val selectionMachine = koinInject<GroupSelectionStateMachine>()
    val viewModel = koinViewModel<GroupSetupViewModel>(
        parameters = {
            parametersOf(
                GroupSetupViewModelParameters(
                    input = GroupSetupInput(),
                    commandKeys = GroupCommandKeyFactory { requestIds.next() },
                ),
            )
        },
    )
    val setupState by viewModel.state.collectAsState()
    var photoUploadPending by remember(viewModel) { mutableStateOf(false) }
    LaunchedEffect(viewModel, photoCoordinator) {
        photoUploadPending = false
        photoCoordinator?.onIntent(GroupPhotoIntent.Cancel)
    }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupSetupEffect.SelectGroup -> {
                session.goBack()
                selectionMachine.onIntent(GroupSelectionIntent.SelectJoined(effect.groupId))
            }
            is GroupSetupEffect.OpenGroup -> Unit
            is GroupSetupEffect.UploadPhoto -> {
                photoUploadPending = true
                photoCoordinator?.onIntent(GroupPhotoIntent.BindTarget(effect.groupId, effect.groupEtag))
                photoCoordinator?.onIntent(GroupPhotoIntent.Upload)
            }
        }
    }
    if (photoCoordinator != null) {
        LaunchedEffect(viewModel, photoCoordinator) {
            photoCoordinator.state.collect { photo ->
                if (!photoUploadPending) return@collect
                when {
                    photo.stage == GroupPhotoStage.IDLE && photo.selection == null && photo.existing != null -> {
                        photoUploadPending = false
                        viewModel.onIntent(GroupSetupIntent.PhotoUploadSucceeded)
                    }
                    photo.error in setOf(
                        GroupPhotoError.ENCODING_FAILED,
                        GroupPhotoError.UPLOAD_FAILED,
                        GroupPhotoError.STALE_VERSION,
                        GroupPhotoError.TARGET_UNAVAILABLE,
                    ) -> {
                        photoUploadPending = false
                        viewModel.onIntent(GroupSetupIntent.PhotoUploadFailed)
                    }
                }
            }
        }
    }
    val photoState by (
        photoCoordinator?.state
            ?: kotlinx.coroutines.flow.MutableStateFlow(br.com.saqz.groups.presentation.photo.GroupPhotoState())
        ).collectAsState()
    GroupSetupScreen(
        state = setupState,
        photoState = photoState,
        onPhotoIntent = { intent ->
            if (intent == GroupPhotoIntent.Upload || intent == GroupPhotoIntent.RetryUpload) {
                photoUploadPending = true
            }
            photoCoordinator?.onIntent(intent)
        },
        photoPreview = groupSetupPhotoPreview,
        onBack = { session.goBack() },
        onIntent = viewModel::onIntent,
    )
}

/** Access stack root: `Starting` until auth is observed, else the reconciler replaces it. */
private val AccessRouteRoot: NavKey = br.com.saqz.access.navigation.AccessRoute.Starting

/** Typed feature navigation commands -> session mutations (LIFE-04, T25). */
private fun NavigationSession.navigate(intent: GroupsNavigationIntent) {
    when (intent) {
        is GroupsNavigationIntent.OpenGroup -> Unit // selection reconciliation owns the stack root
        GroupsNavigationIntent.OpenGroups -> selectTab(ProductTab.GROUPS)
        GroupsNavigationIntent.OpenHome -> goBack()
        GroupsNavigationIntent.OpenProfileCompletion -> push(GroupsRoute.ProfileCompletion)
        GroupsNavigationIntent.OpenPeople -> push(GroupsRoute.People)
        GroupsNavigationIntent.OpenGames -> push(GroupsRoute.Games)
        is GroupsNavigationIntent.OpenGameDetail -> {
            push(GroupsRoute.Games)
            push(GroupsRoute.GameDetail(intent.gameId))
        }
        GroupsNavigationIntent.OpenFinance -> push(GroupsRoute.Games) // unreachable from GroupHome; placeholder
        GroupsNavigationIntent.OpenNotices -> selectTab(ProductTab.NOTICES)
        GroupsNavigationIntent.OpenMore -> selectTab(ProductTab.MORE)
        else -> Unit // Reconcile is a legacy-VM concern; selection reconciliation owns the stack root.
    }
}

@Composable
private fun rememberNavigationSession(
    homeStack: NavBackStack<NavKey>,
    groupsStack: NavBackStack<NavKey>,
    noticesStack: NavBackStack<NavKey>,
    moreStack: NavBackStack<NavKey>,
): NavigationSession = remember(homeStack, groupsStack, noticesStack, moreStack) {
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
private fun ProductBottomBar(session: NavigationSession) {
    val selected = session.selectedTab
    SaqzBottomNav(
        items = listOf(
            tabItem("Início", ProductTab.HOME, selected) { session.selectTab(ProductTab.HOME) },
            tabItem("Grupos", ProductTab.GROUPS, selected) { session.selectTab(ProductTab.GROUPS) },
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
// screen body still renders its own localized heading.
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
