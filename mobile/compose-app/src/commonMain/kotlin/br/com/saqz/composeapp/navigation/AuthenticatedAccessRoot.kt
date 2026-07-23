package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.attendance.share.NativeAttendanceShareResult
import br.com.saqz.groups.domain.photo.GroupPhotoGateway
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.access.domain.port.AuthState
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.LocalAccessStatePort
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeSharePort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkIntent
import br.com.saqz.groups.presentation.attendance.share.DeferredAttendanceLinkStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.InviteToolStateMachine
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailEffect
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.games.detail.GameDetailViewModel
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoCoordinator
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupListPhotoLoader
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.groups.presentation.setup.GroupCommandKeyFactory
import br.com.saqz.groups.presentation.setup.GroupSetupEffect
import br.com.saqz.groups.presentation.setup.GroupSetupInput
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.GroupSetupViewModel
import br.com.saqz.groups.ui.setup.GroupSetupScreen
import br.com.saqz.groups.ui.ExpireInviteConfirmationDialog
import br.com.saqz.groups.ui.ExpireInviteConfirmationIntent
import br.com.saqz.groups.ui.GroupOnboardingScreen
import br.com.saqz.groups.ui.GroupOnboardingIntent
import br.com.saqz.groups.ui.GroupSettingsScreen
import br.com.saqz.groups.ui.GroupSettingsIntent
import br.com.saqz.groups.ui.GroupSettingsUiState
import br.com.saqz.groups.ui.GroupsSelectorChrome
import br.com.saqz.groups.ui.InviteManagementScreen
import br.com.saqz.groups.ui.InviteManagementIntent
import br.com.saqz.groups.ui.InviteManagementUiState
import br.com.saqz.groups.ui.games.detail.GameDetailScreen
import br.com.saqz.access.ui.LoginRoot
import br.com.saqz.groups.ui.LogoutConfirmationDialog
import br.com.saqz.groups.ui.LogoutConfirmationIntent
import br.com.saqz.groups.ui.MembershipAdministrationScreen
import br.com.saqz.groups.ui.MembershipAdministrationIntent
import br.com.saqz.access.ui.NameCompletionRoot
import br.com.saqz.access.ui.PasswordResetRoot
import br.com.saqz.access.ui.RegistrationRoot
import br.com.saqz.access.ui.VerificationRoot
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.SaqzPlatformDependencies
import br.com.saqz.composeapp.di.AttendanceDestinationStore
import br.com.saqz.composeapp.di.GameDetailViewModelParameters
import br.com.saqz.composeapp.di.GroupSetupViewModelParameters
import br.com.saqz.composeapp.home.AuthenticatedHomeScreen
import br.com.saqz.composeapp.ui.groups.GroupsRouteHost
import br.com.saqz.access.domain.session.SessionInvalidator
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

internal const val AccessRootTag = "authenticated-access-destination"

internal enum class AccessPage {
    CONTEXT,
    CREATE_GROUP,
    SETTINGS,
    MEMBERSHIPS,
    INVITE,
}

internal enum class AccessDestination {
    STARTING,
    LOGIN,
    REGISTRATION,
    PASSWORD_RESET,
    VERIFICATION,
    NAME_COMPLETION,
    BOOTSTRAP,
    GROUP_ONBOARDING,
    GROUP_CONTEXT,
    CREATE_GROUP,
    SETTINGS,
    MEMBERSHIPS,
    INVITE,
}

internal class AccessDestinationStack(initial: AccessDestination) {
    var entries: List<AccessDestination> = listOf(initial)
        private set

    fun replace(destination: AccessDestination) {
        entries = listOf(destination)
    }
}

@Composable
internal fun AuthenticatedAccessRoute(
    dependencies: SaqzPlatformDependencies,
    accessViewModelOverride: AccessViewModel? = null,
    groupSetupViewModelOverride: GroupSetupViewModel? = null,
    groupPhotos: GroupPhotoRuntimeDependencies = dependencies.groupPhotos,
) {
    val accessViewModel = accessViewModelOverride ?: koinViewModel<AccessViewModel>(key = "authenticated-access")
    val groupsViewModel = koinViewModel<GroupsNavigationViewModel>(key = "groups-navigation")
    val state by accessViewModel.state.collectAsState()
    val groupsNavigation by groupsViewModel.state.collectAsState()
    val sessionMemberships = (state.session as? SessionAccessState.Ready)?.session?.memberships.orEmpty()
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
    val groupSetupViewModel = if (state.page == AccessPage.CREATE_GROUP) {
        groupSetupViewModelOverride ?: koinViewModel<GroupSetupViewModel>(
            key = "group-setup-${state.createFlowKey}",
            parameters = {
                parametersOf(
                    GroupSetupViewModelParameters(
                        input = GroupSetupInput(),
                        commandKeys = GroupCommandKeyFactory { accessViewModel.newCommandKey() },
                    ),
                )
            },
        )
    } else null
    val groupSetupState = groupSetupViewModel?.state?.collectAsState()?.value
    val gameDetailViewModel = groupsNavigation.takeIf {
        it.destination == GroupsDestination.GAME_DETAIL && it.groupId != null && it.gameId != null
    }?.let { navigation ->
        koinViewModel<GameDetailViewModel>(
            key = "game-detail-${navigation.groupId}-${navigation.gameId}",
            parameters = {
                parametersOf(
                    GameDetailViewModelParameters(
                        groupId = navigation.groupId!!,
                        gameId = navigation.gameId!!,
                        role = state.administration.group?.group?.role
                    ?: (state.selection as? GroupSelectionState.Selected)?.group?.group?.role
                            ?: GroupRole.ATHLETE,
                    ),
                )
            },
        )
    }
    val gameDetailState = gameDetailViewModel?.state?.collectAsState()?.value
    var setupPhotoUploadPending by remember(groupSetupViewModel) { mutableStateOf(false) }
    LaunchedEffect(state.page, state.createFlowKey) {
        if (state.page == AccessPage.CREATE_GROUP) {
            setupPhotoUploadPending = false
            photoCoordinator.onIntent(GroupPhotoIntent.Cancel)
        }
    }
    LaunchedEffect(state.session) {
        if (state.session == SessionAccessState.SignedOut) {
            setupPhotoUploadPending = false
            photoCoordinator.onIntent(GroupPhotoIntent.Logout)
        }
    }
    val versionedGroup = state.administration.group
    LaunchedEffect(
        state.page,
        groupsNavigation.destination,
        groupsNavigation.groupId,
        versionedGroup?.group?.id,
        versionedGroup?.versionToken,
    ) {
        val selectedGroup = versionedGroup ?: return@LaunchedEffect
        if (
            state.page == AccessPage.CONTEXT &&
            groupsNavigation.destination == GroupsDestination.HOME &&
            groupsNavigation.groupId == selectedGroup.group.id.value
        ) {
            photoCoordinator.onIntent(
                GroupPhotoIntent.Load(
                    selectedGroup.group.id.value,
                    selectedGroup.versionToken.value,
                ),
            )
        }
    }
    LaunchedEffect(sessionMemberships, groupPhotoState.groupId, state.page, state.selection) {
        if (state.page == AccessPage.CREATE_GROUP) return@LaunchedEffect
        val loadedGroupId = groupPhotoState.groupId ?: return@LaunchedEffect
        val selectedGroupId = (state.selection as? GroupSelectionState.Selected)?.group?.group?.id?.value
        if (
            selectedGroupId != loadedGroupId &&
            sessionMemberships.none { it.groupId.value == loadedGroupId }
        ) {
            photoCoordinator.onIntent(GroupPhotoIntent.MembershipLost)
        }
    }
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
    if (gameDetailViewModel != null) {
        LaunchedEffect(gameDetailViewModel, dependencies.attendanceShare) {
            gameDetailViewModel.effects.collect { effect ->
                when (effect) {
                    is GameDetailEffect.ShareAttendanceLink ->
                        dependencies.attendanceShare.shareLink(effect.url.value) { result ->
                            gameDetailViewModel.onIntent(
                                GameDetailIntent.ReportAttendanceShareResult(
                                    result is NativeAttendanceShareResult.Success,
                                ),
                            )
                        }
                    is GameDetailEffect.ShareAttendanceImage ->
                        dependencies.attendanceShare.shareImage(effect.image) { result ->
                            gameDetailViewModel.onIntent(
                                GameDetailIntent.ReportAttendanceShareResult(
                                    result is NativeAttendanceShareResult.Success,
                                ),
                            )
                        }
                    else -> Unit
                }
            }
        }
    }
    if (groupSetupViewModel != null) {
        LaunchedEffect(groupSetupViewModel, photoCoordinator) {
            groupSetupViewModel.effects.collect { effect ->
                when (effect) {
                    is GroupSetupEffect.SelectGroup -> accessViewModel.onIntent(
                        AccessIntent.Selection(GroupSelectionIntent.Select(effect.groupId)),
                    )
                    is GroupSetupEffect.OpenGroup -> Unit
                    is GroupSetupEffect.UploadPhoto -> {
                        setupPhotoUploadPending = true
                        photoCoordinator.onIntent(GroupPhotoIntent.BindTarget(effect.groupId, effect.groupEtag))
                        photoCoordinator.onIntent(GroupPhotoIntent.Upload)
                    }
                }
            }
        }
        LaunchedEffect(groupSetupViewModel, photoCoordinator) {
            photoCoordinator.state.collect { photo ->
                if (!setupPhotoUploadPending) return@collect
                when {
                    photo.stage == GroupPhotoStage.IDLE && photo.selection == null && photo.existing != null -> {
                        setupPhotoUploadPending = false
                        groupSetupViewModel.onIntent(GroupSetupIntent.PhotoUploadSucceeded)
                    }
                    photo.error in setOf(
                        GroupPhotoError.ENCODING_FAILED,
                        GroupPhotoError.UPLOAD_FAILED,
                        GroupPhotoError.STALE_VERSION,
                        GroupPhotoError.TARGET_UNAVAILABLE,
                    ) -> {
                        setupPhotoUploadPending = false
                        groupSetupViewModel.onIntent(GroupSetupIntent.PhotoUploadFailed)
                    }
                }
            }
        }
    }
    val onAccessIntent: (AccessIntent) -> Unit = { intent ->
        if (intent == AccessIntent.ClosePage && state.page == AccessPage.CREATE_GROUP) {
            setupPhotoUploadPending = false
            photoCoordinator.onIntent(GroupPhotoIntent.Cancel)
        }
        accessViewModel.onIntent(intent)
    }
    AuthenticatedAccessRoot(
        state = state,
        onIntent = onAccessIntent,
        groupsNavigation = groupsNavigation,
        onGroupsIntent = groupsViewModel::onIntent,
        initiallyShowAppHome = true,
        groupSetupState = groupSetupState,
        onGroupSetupIntent = groupSetupViewModel?.let { viewModel -> viewModel::onIntent } ?: {},
        groupPhotoState = groupPhotoState,
        onGroupPhotoIntent = { intent ->
            if (intent == GroupPhotoIntent.Upload || intent == GroupPhotoIntent.RetryUpload) {
                setupPhotoUploadPending = true
            }
            photoCoordinator.onIntent(intent)
        },
        groupPhotoPreview = { handle, modifier ->
            GroupPhotoPreview(handle, groupPhotos.previews, photoImageLoader, modifier) ==
                GroupPhotoRenderState.SUCCESS
        },
        groupPhotoDetailPreview = { handle, modifier ->
            GroupPhotoPreview(handle, photoCache, photoImageLoader, modifier)
        },
        loadListPhoto = listPhotoLoader::load,
        gameDetailState = gameDetailState,
        onGameDetailIntent = gameDetailViewModel?.let { viewModel -> viewModel::onIntent } ?: {},
    )
}

internal fun handleAccessEffect(
    effect: AccessUiEffect,
    share: NativeSharePort,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
    onIntent: (AccessIntent) -> Unit,
) {
    when (effect) {
        is AccessUiEffect.RequestShare -> share.share(effect.text, object : ResultCallback {
            override fun complete(result: OperationResult) {
                onIntent(AccessIntent.ShareFinished(result is OperationResult.Success))
            }
        })
        is AccessUiEffect.OpenAttendanceGame -> onGroupsIntent(GroupsNavigationIntent.OpenGameDetail(effect.gameId))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
internal fun AuthenticatedAccessRoot(
    state: AccessRootSnapshot,
    groupsNavigation: GroupsNavigationState? = null,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit = {},
    initiallyShowAppHome: Boolean = false,
    groupSetupState: GroupSetupState? = null,
    onGroupSetupIntent: (GroupSetupIntent) -> Unit = {},
    groupPhotoState: GroupPhotoState = GroupPhotoState(),
    onGroupPhotoIntent: (GroupPhotoIntent) -> Unit = {},
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)? = null,
    groupPhotoDetailPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    gameDetailState: GameDetailState? = null,
    onGameDetailIntent: (GameDetailIntent) -> Unit = {},
    onIntent: (AccessIntent) -> Unit,
) {
    val desired = state.destination()
    val stack = AccessDestinationStack(desired).also { it.replace(desired) }
    val destination = stack.entries.single()
    var showAppHome by rememberSaveable { mutableStateOf(initiallyShowAppHome) }
    val handleGroupsIntent: (GroupsNavigationIntent) -> Unit = { intent ->
        when {
            showAppHome && intent == GroupsNavigationIntent.OpenHome -> Unit
            !showAppHome &&
                groupsNavigation?.destination == GroupsDestination.SELECTOR &&
                intent == GroupsNavigationIntent.OpenHome -> showAppHome = true
            else -> {
                showAppHome = false
                onGroupsIntent(intent)
            }
        }
    }
    LaunchedEffect(destination) {
        if (destination == AccessDestination.LOGIN) showAppHome = initiallyShowAppHome
    }
    LaunchedEffect(groupsNavigation?.destination, groupsNavigation?.gameId) {
        if (groupsNavigation?.destination == GroupsDestination.GAME_DETAIL) {
            showAppHome = false
        }
    }
    val systemBackIntent = destination.systemBackIntent()
    BackHandler(enabled = systemBackIntent != null) {
        systemBackIntent?.let(onIntent)
    }
    key(destination) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (destination.respectsSafeArea()) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .testTag(AccessRootTag),
        ) {
            DestinationContent(
                destination,
                state,
                onIntent,
                groupsNavigation,
                handleGroupsIntent,
                showAppHome,
                groupSetupState,
                onGroupSetupIntent,
                groupPhotoState,
                onGroupPhotoIntent,
                groupPhotoPreview,
                groupPhotoDetailPreview,
                loadListPhoto,
                gameDetailState,
                onGameDetailIntent,
            )
        }
    }
}

internal fun AccessDestination.respectsSafeArea(): Boolean = when (this) {
    AccessDestination.LOGIN,
    AccessDestination.REGISTRATION,
    AccessDestination.PASSWORD_RESET,
    -> false
    else -> true
}

internal fun AccessDestination.systemBackIntent(): AccessIntent? = when (this) {
    AccessDestination.REGISTRATION,
    AccessDestination.PASSWORD_RESET -> AccessIntent.Authentication(AuthenticationIntent.ShowLogin)
    AccessDestination.CREATE_GROUP -> AccessIntent.ClosePage
    else -> null
}

private fun AccessRootSnapshot.destination(): AccessDestination {
    if (!authObserved) return AccessDestination.STARTING
    return when (session) {
        SessionAccessState.SignedOut -> when (authentication.screen) {
            AuthScreen.LOGIN -> AccessDestination.LOGIN
            AuthScreen.REGISTRATION -> AccessDestination.REGISTRATION
            AuthScreen.PASSWORD_RESET -> AccessDestination.PASSWORD_RESET
        }
        is SessionAccessState.AwaitingVerification -> AccessDestination.VERIFICATION
        is SessionAccessState.CompletingName -> AccessDestination.NAME_COMPLETION
        SessionAccessState.Bootstrapping,
        SessionAccessState.BootstrapError,
        -> AccessDestination.BOOTSTRAP
        is SessionAccessState.Ready -> selectionDestination()
    }
}

private fun AccessRootSnapshot.selectionDestination(): AccessDestination = when (selection) {
    GroupSelectionState.NoGroup,
    is GroupSelectionState.Selector,
    is GroupSelectionState.Loading,
    is GroupSelectionState.LoadError,
    -> if (page == AccessPage.CREATE_GROUP) AccessDestination.CREATE_GROUP else AccessDestination.GROUP_ONBOARDING
    is GroupSelectionState.Selected -> when (page) {
        AccessPage.CONTEXT -> AccessDestination.GROUP_CONTEXT
        AccessPage.CREATE_GROUP -> AccessDestination.CREATE_GROUP
        AccessPage.SETTINGS -> if (administration.actions.canEditSettings) {
            AccessDestination.SETTINGS
        } else {
            AccessDestination.GROUP_CONTEXT
        }
        AccessPage.MEMBERSHIPS -> if (administration.actions.canManageRoles) {
            AccessDestination.MEMBERSHIPS
        } else {
            AccessDestination.GROUP_CONTEXT
        }
        AccessPage.INVITE -> if (administration.actions.canManageInvite) {
            AccessDestination.INVITE
        } else {
            AccessDestination.GROUP_CONTEXT
        }
    }
}

@Composable
private fun DestinationContent(
    destination: AccessDestination,
    state: AccessRootSnapshot,
    onIntent: (AccessIntent) -> Unit,
    groupsNavigation: GroupsNavigationState?,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
    showAppHome: Boolean,
    groupSetupState: GroupSetupState?,
    onGroupSetupIntent: (GroupSetupIntent) -> Unit,
    groupPhotoState: GroupPhotoState,
    onGroupPhotoIntent: (GroupPhotoIntent) -> Unit,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)?,
    groupPhotoDetailPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)?,
    gameDetailState: GameDetailState?,
    onGameDetailIntent: (GameDetailIntent) -> Unit,
) {
    if (
        showAppHome &&
        groupsNavigation != null &&
        destination in setOf(AccessDestination.GROUP_ONBOARDING, AccessDestination.GROUP_CONTEXT)
    ) {
        GroupsSelectorChrome(
            navigation = groupsNavigation.copy(destination = GroupsDestination.HOME),
            onNavigationIntent = onGroupsIntent,
        ) {
            AuthenticatedHomeScreen()
        }
        return
    }
    when (destination) {
        AccessDestination.STARTING -> SaqzLoadingState()
        AccessDestination.LOGIN -> LoginRoot()
        AccessDestination.REGISTRATION -> RegistrationRoot()
        AccessDestination.PASSWORD_RESET -> PasswordResetRoot()
        AccessDestination.VERIFICATION -> VerificationRoot()
        AccessDestination.NAME_COMPLETION -> NameCompletionRoot()
        AccessDestination.BOOTSTRAP -> BootstrapAccessScreen(state.session) { intent ->
            onIntent(AccessIntent.Session(intent))
        }
        AccessDestination.GROUP_ONBOARDING -> {
            val onboardingNavigation = groupsNavigation?.takeIf { navigation ->
                navigation.destination in setOf(
                    GroupsDestination.SELECTOR,
                    GroupsDestination.LOADING,
                    GroupsDestination.LOAD_ERROR,
                )
            }
            if (onboardingNavigation != null) {
                GroupsRouteContent(
                    state,
                    onboardingNavigation,
                    onIntent,
                    onGroupsIntent,
                    groupPhotoState,
                    groupPhotoDetailPreview,
                    loadListPhoto,
                    gameDetailState,
                    onGameDetailIntent,
                )
            } else {
                GroupOnboardingScreen(state.selection) { intent ->
                    when (intent) {
                        is GroupOnboardingIntent.Select -> onIntent(
                            AccessIntent.Selection(GroupSelectionIntent.Select(intent.groupId)),
                        )
                        GroupOnboardingIntent.OpenCreateGroup -> onIntent(AccessIntent.OpenCreateGroup)
                        GroupOnboardingIntent.Retry -> onIntent(
                            AccessIntent.Selection(GroupSelectionIntent.Retry),
                        )
                    }
                }
            }
        }
        AccessDestination.GROUP_CONTEXT -> {
            if (groupsNavigation == null) {
                SaqzLoadingState()
            } else {
                GroupsRouteContent(
                    state,
                    groupsNavigation,
                    onIntent,
                    onGroupsIntent,
                    groupPhotoState,
                    groupPhotoDetailPreview,
                    loadListPhoto,
                    gameDetailState,
                    onGameDetailIntent,
                )
            }
            if (state.showLogoutConfirmation) {
                LogoutConfirmationDialog { intent ->
                    onIntent(
                        when (intent) {
                            LogoutConfirmationIntent.Confirm -> AccessIntent.ConfirmLogout
                            LogoutConfirmationIntent.Cancel -> AccessIntent.CancelLogout
                        },
                    )
                }
            }
        }
        AccessDestination.CREATE_GROUP -> if (groupSetupState == null) {
            SaqzLoadingState()
        } else {
            GroupSetupScreen(
                state = groupSetupState,
                photoState = groupPhotoState,
                onPhotoIntent = onGroupPhotoIntent,
                photoPreview = groupPhotoPreview,
                onIntent = onGroupSetupIntent,
            )
        }
        AccessDestination.SETTINGS -> GroupSettingsScreen(
            GroupSettingsUiState(
                administration = state.administration,
                name = state.settingsName,
                timeZone = state.settingsTimeZone,
            ),
        ) { intent ->
            onIntent(
                when (intent) {
                    is GroupSettingsIntent.UpdateName -> AccessIntent.UpdateSettingsName(intent.value)
                    is GroupSettingsIntent.UpdateTimeZone -> AccessIntent.UpdateSettingsTimeZone(intent.value)
                    GroupSettingsIntent.Save -> AccessIntent.SaveSettings
                    GroupSettingsIntent.Reload -> AccessIntent.ReloadSettings
                    GroupSettingsIntent.Back -> AccessIntent.ClosePage
                },
            )
        }
        AccessDestination.MEMBERSHIPS -> MembershipAdministrationScreen(state.administration) { intent ->
            onIntent(
                when (intent) {
                    is MembershipAdministrationIntent.ChangeRole -> AccessIntent.ChangeRole(
                        intent.userId,
                        intent.role,
                    )
                    MembershipAdministrationIntent.Back -> AccessIntent.ClosePage
                },
            )
        }
        AccessDestination.INVITE -> {
            InviteManagementScreen(
                InviteManagementUiState(state.administration.actions, state.invite),
            ) { intent ->
                onIntent(
                    when (intent) {
                        InviteManagementIntent.Generate -> AccessIntent.GenerateInvite
                        is InviteManagementIntent.Share -> AccessIntent.ShareInvite(intent.url)
                        InviteManagementIntent.RequestExpire -> AccessIntent.RequestExpireInvite
                        InviteManagementIntent.Retry -> AccessIntent.RetryInvite
                        InviteManagementIntent.Back -> AccessIntent.ClosePage
                    },
                )
            }
            if (state.showExpireConfirmation) {
                ExpireInviteConfirmationDialog { intent ->
                    onIntent(
                        when (intent) {
                            ExpireInviteConfirmationIntent.Confirm -> AccessIntent.ConfirmExpireInvite
                            ExpireInviteConfirmationIntent.Cancel -> AccessIntent.CancelExpireInvite
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupsRouteContent(
    state: AccessRootSnapshot,
    navigation: GroupsNavigationState,
    onIntent: (AccessIntent) -> Unit,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit,
    groupPhotoState: GroupPhotoState,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)?,
    gameDetailState: GameDetailState?,
    onGameDetailIntent: (GameDetailIntent) -> Unit,
) {
    GroupsRouteHost(
        navigation = navigation,
        administration = state.administration,
        groupPhotoState = groupPhotoState,
        groupPhotoPreview = groupPhotoPreview,
        loadListPhoto = loadListPhoto,
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
    )
}
