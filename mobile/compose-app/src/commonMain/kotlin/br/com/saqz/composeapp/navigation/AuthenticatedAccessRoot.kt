package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.saqz.groups.data.GroupApi
import br.com.saqz.groups.data.GroupProfileGateway
import br.com.saqz.groups.data.GroupPhotoApi
import br.com.saqz.groups.data.GroupPhotoGateway
import br.com.saqz.groups.data.RolesInvitesApi
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.NativeSharePort
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult as NativeTokenResult
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.groups.presentation.DeferredInviteIntent
import br.com.saqz.groups.presentation.DeferredInviteStateMachine
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.GroupAdministrationStateMachine
import br.com.saqz.groups.presentation.GroupSelectionIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import br.com.saqz.groups.presentation.InviteUiError
import br.com.saqz.groups.presentation.photo.GroupPhotoCoordinator
import br.com.saqz.groups.presentation.photo.GroupPhotoError
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoStage
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupInviteCodeListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.LocalGroupStatePort
import br.com.saqz.groups.port.NativeGroupLinkPort
import br.com.saqz.groups.port.DefaultGroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
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
import br.com.saqz.groups.ui.GroupContextScreen
import br.com.saqz.groups.ui.GroupContextIntent
import br.com.saqz.groups.ui.GroupOnboardingScreen
import br.com.saqz.groups.ui.GroupOnboardingIntent
import br.com.saqz.groups.ui.GroupSettingsScreen
import br.com.saqz.groups.ui.GroupSettingsIntent
import br.com.saqz.groups.ui.GroupSettingsUiState
import br.com.saqz.groups.ui.InviteManagementScreen
import br.com.saqz.groups.ui.InviteManagementIntent
import br.com.saqz.groups.ui.InviteManagementUiState
import br.com.saqz.groups.ui.InviteToolState
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.groups.ui.LogoutConfirmationDialog
import br.com.saqz.groups.ui.LogoutConfirmationIntent
import br.com.saqz.groups.ui.MembershipAdministrationScreen
import br.com.saqz.groups.ui.MembershipAdministrationIntent
import br.com.saqz.access.ui.NameCompletionScreen
import br.com.saqz.access.ui.PasswordResetScreen
import br.com.saqz.access.ui.RegistrationScreen
import br.com.saqz.access.ui.VerificationScreen
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.composeapp.GroupPhotoRuntimeDependencies
import br.com.saqz.composeapp.SaqzAppDependencies
import br.com.saqz.network.AuthenticatedNetworkClient
import br.com.saqz.network.IdTokenProvider
import br.com.saqz.network.NetworkConfig
import br.com.saqz.network.NetworkResult
import br.com.saqz.network.SessionApi
import br.com.saqz.network.SessionInvalidator
import br.com.saqz.network.TokenResult
import br.com.saqz.network.createPlatformNetworkClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

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
    dependencies: SaqzAppDependencies,
    accessViewModelOverride: AccessViewModel? = null,
    groupSetupViewModelOverride: GroupSetupViewModel? = null,
    groupPhotos: GroupPhotoRuntimeDependencies = dependencies.groupPhotos,
) {
    val accessViewModel = accessViewModelOverride ?: viewModel<AccessViewModel>(key = "authenticated-access") {
        AccessViewModel(dependencies)
    }
    val groupsViewModel = viewModel<GroupsNavigationViewModel>(key = "groups-navigation") {
        GroupsNavigationViewModel()
    }
    val state by accessViewModel.state.collectAsState()
    val groupsNavigation by groupsViewModel.state.collectAsState()
    val photoScope = rememberCoroutineScope()
    val photoCoordinator = remember(accessViewModel, groupPhotos) {
        GroupPhotoCoordinator(
            gateway = accessViewModel.groupPhotoGateway,
            selections = groupPhotos.selection,
            encoder = groupPhotos.encoder,
            cache = groupPhotos.cache,
            scope = photoScope,
        )
    }
    val groupPhotoState by photoCoordinator.state.collectAsState()
    val groupSetupViewModel = if (state.page == AccessPage.CREATE_GROUP) {
        groupSetupViewModelOverride ?: viewModel<GroupSetupViewModel>(key = "group-setup-${state.createFlowKey}") {
            GroupSetupViewModel(
                input = GroupSetupInput(),
                gateway = accessViewModel.groupProfileGateway,
                timeZones = DefaultGroupSystemTimeZonePort(),
                drafts = dependencies.groupDrafts,
                commandKeys = GroupCommandKeyFactory { accessViewModel.newCommandKey() },
            )
        }
    } else null
    val groupSetupState = groupSetupViewModel?.state?.collectAsState()?.value
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
    LaunchedEffect(groupsViewModel, state.selection) {
        groupsViewModel.onIntent(GroupsNavigationIntent.Reconcile(state.selection))
    }
    LaunchedEffect(accessViewModel, dependencies.share) {
        accessViewModel.effects.collect { effect ->
            handleAccessEffect(effect, dependencies.share, accessViewModel::onIntent)
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
            GroupPhotoPreview(handle, groupPhotos.previews, modifier)
        },
    )
}

internal fun handleAccessEffect(
    effect: AccessUiEffect,
    share: NativeSharePort,
    onIntent: (AccessIntent) -> Unit,
) {
    when (effect) {
        is AccessUiEffect.RequestShare -> share.share(effect.text, object : ResultCallback {
            override fun complete(result: OperationResult) {
                onIntent(AccessIntent.ShareFinished(result is OperationResult.Success))
            }
        })
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION")
@Composable
internal fun AuthenticatedAccessRoot(
    state: AccessRootSnapshot,
    groupsNavigation: GroupsNavigationState? = null,
    onGroupsIntent: (GroupsNavigationIntent) -> Unit = {},
    groupSetupState: GroupSetupState? = null,
    onGroupSetupIntent: (GroupSetupIntent) -> Unit = {},
    groupPhotoState: GroupPhotoState = GroupPhotoState(),
    onGroupPhotoIntent: (GroupPhotoIntent) -> Unit = {},
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)? = null,
    onIntent: (AccessIntent) -> Unit,
) {
    val desired = state.destination()
    val stack = AccessDestinationStack(desired).also { it.replace(desired) }
    val destination = stack.entries.single()
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
                onGroupsIntent,
                groupSetupState,
                onGroupSetupIntent,
                groupPhotoState,
                onGroupPhotoIntent,
                groupPhotoPreview,
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

internal class AccessRuntime(
    private val dependencies: SaqzAppDependencies,
    private val scope: CoroutineScope,
) : AccessRuntimeContract {
    private val network = createPlatformNetworkClient(
        NetworkConfig(environment = dependencies.environment, baseUrl = dependencies.apiBaseUrl),
    )
    private val invalidator = DelegatingSessionInvalidator()
    private val authenticatedNetwork = AuthenticatedNetworkClient(
        network,
        NativeTokenProvider(dependencies),
        invalidator,
    )
    private val groups = GroupApi(authenticatedNetwork)
    override val groupProfileGateway: GroupProfileGateway = groups
    override val groupPhotoGateway: GroupPhotoGateway = GroupPhotoApi(authenticatedNetwork)
    private val roles = RolesInvitesApi(authenticatedNetwork)
    private val session = SessionAccessStateMachine(dependencies.auth, dependencies.localState, SessionApi(authenticatedNetwork), scope)
    private val authentication = AuthenticationStateMachine(dependencies.auth) {
        session.onIntent(SessionIntent.Accept(it))
    }
    private val groupState = dependencies.groupState
    private val groupLinks = dependencies.groupLinks
    private val selection = GroupSelectionStateMachine(groupState, groups, scope)
    private val administration = GroupAdministrationStateMachine(groups, roles, scope) {
        selection.onIntent(GroupSelectionIntent.Select(it))
    }
    private val invites = DeferredInviteStateMachine(groupLinks, groupState, roles, scope) {
        selection.onIntent(GroupSelectionIntent.Select(it))
    }
    private val mutableInviteToolState = MutableStateFlow(InviteToolState())
    override val inviteToolState = mutableInviteToolState.asStateFlow()
    private val mutableAuthObservedState = MutableStateFlow(false)
    override val authObservedState = mutableAuthObservedState.asStateFlow()
    override val authenticationState = authentication.state
    override val sessionState = session.state
    override val selectionState = selection.state
    override val administrationState = administration.state
    private var authSubscription: Cancelable? = null

    init {
        invalidator.delegate = session
    }

    override fun onIntent(intent: AccessRuntimeIntent) {
        when (intent) {
            AccessRuntimeIntent.Start -> start()
            AccessRuntimeIntent.Close -> close()
            is AccessRuntimeIntent.Authentication -> authentication.onIntent(intent.intent)
            is AccessRuntimeIntent.Session -> session.onIntent(intent.intent)
            is AccessRuntimeIntent.Selection -> selection.onIntent(intent.intent)
            is AccessRuntimeIntent.Administration -> administration.onIntent(intent.intent)
            is AccessRuntimeIntent.DeferredInvite -> invites.onIntent(intent.intent)
            is AccessRuntimeIntent.ShowGroupSelector -> showGroupSelector(intent.session)
            AccessRuntimeIntent.RotateInvite -> rotateInvite()
            AccessRuntimeIntent.ExpireInvite -> expireInvite()
            is AccessRuntimeIntent.ShareFinished -> shareFinished(intent.successful)
        }
    }

    private fun start() {
        if (authSubscription != null) return
        authSubscription = dependencies.auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                mutableAuthObservedState.value = true
                when (state) {
                    AuthState.SignedOut -> authentication.onIntent(AuthenticationIntent.ShowLogin)
                    is AuthState.SignedIn -> session.onIntent(
                        SessionIntent.Accept(AuthTransition.Authenticated(state.user)),
                    )
                }
            }
        })
        invites.onIntent(DeferredInviteIntent.Start)
        invites.onIntent(DeferredInviteIntent.Restore)
    }

    private fun close() {
        authSubscription?.cancel()
        authSubscription = null
        invites.onIntent(DeferredInviteIntent.Stop)
        network.close()
    }

    private fun showGroupSelector(session: br.com.saqz.network.SessionDto) {
        dependencies.localState.writeSelectedGroupId(null, resultCallback {
            selection.onIntent(GroupSelectionIntent.Reconcile(session))
        })
    }

    private fun rotateInvite() {
        val groupId = administration.state.value.group?.group?.id ?: return
        if (mutableInviteToolState.value.isLoading) return
        mutableInviteToolState.value = mutableInviteToolState.value.copy(isLoading = true, error = null)
        scope.launch {
            mutableInviteToolState.value = when (val result = roles.rotateInvite(groupId)) {
                is NetworkResult.Success -> InviteToolState(inviteUrl = result.value.inviteUrl)
                is NetworkResult.Failure -> mutableInviteToolState.value.copy(
                    isLoading = false,
                    error = InviteUiError.UNAVAILABLE,
                )
            }
        }
    }

    private fun expireInvite() {
        val groupId = administration.state.value.group?.group?.id ?: return
        if (mutableInviteToolState.value.isLoading) return
        mutableInviteToolState.value = mutableInviteToolState.value.copy(isLoading = true, error = null)
        scope.launch {
            mutableInviteToolState.value = when (roles.expireInvite(groupId)) {
                is NetworkResult.Success -> InviteToolState()
                is NetworkResult.Failure -> mutableInviteToolState.value.copy(
                    isLoading = false,
                    error = InviteUiError.UNAVAILABLE,
                )
            }
        }
    }

    private fun shareFinished(successful: Boolean) {
        if (!successful) {
            mutableInviteToolState.value = mutableInviteToolState.value.copy(error = InviteUiError.UNAVAILABLE)
        }
    }

    override fun newRequestId(): String {
        val bytes = Random.nextBytes(16)
        bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
        bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
        val hex = bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        return "${hex.take(8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.drop(20)}"
    }

    private fun resultCallback(block: (OperationResult) -> Unit) = object : ResultCallback {
        override fun complete(result: OperationResult) = block(result)
    }
}

private class NativeTokenProvider(private val dependencies: SaqzAppDependencies) : IdTokenProvider {
    override fun token(forceRefresh: Boolean, completion: (TokenResult) -> Unit) {
        dependencies.auth.idToken(forceRefresh, object : TokenCallback {
            override fun complete(result: NativeTokenResult) {
                completion(
                    when (result) {
                        is NativeTokenResult.Success -> TokenResult.Available(result.token)
                        is NativeTokenResult.Failure -> TokenResult.Unavailable
                    },
                )
            }
        })
    }
}

private class DelegatingSessionInvalidator : SessionInvalidator {
    lateinit var delegate: SessionInvalidator

    override fun invalidate() = delegate.invalidate()
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
    groupSetupState: GroupSetupState?,
    onGroupSetupIntent: (GroupSetupIntent) -> Unit,
    groupPhotoState: GroupPhotoState,
    onGroupPhotoIntent: (GroupPhotoIntent) -> Unit,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)?,
) {
    when (destination) {
        AccessDestination.STARTING -> SaqzLoadingState()
        AccessDestination.LOGIN -> LoginScreen(state.authentication) { intent ->
            onIntent(AccessIntent.Authentication(intent))
        }
        AccessDestination.REGISTRATION -> RegistrationScreen(state.authentication) { intent ->
            onIntent(AccessIntent.Authentication(intent))
        }
        AccessDestination.PASSWORD_RESET -> PasswordResetScreen(state.authentication) { intent ->
            onIntent(AccessIntent.Authentication(intent))
        }
        AccessDestination.VERIFICATION -> VerificationScreen(
            state.session as SessionAccessState.AwaitingVerification,
        ) { intent ->
            onIntent(AccessIntent.Session(intent))
        }
        AccessDestination.NAME_COMPLETION -> NameCompletionScreen(
            state.session as SessionAccessState.CompletingName,
        ) { intent ->
            onIntent(AccessIntent.Session(intent))
        }
        AccessDestination.BOOTSTRAP -> BootstrapAccessScreen(state.session) { intent ->
            onIntent(AccessIntent.Session(intent))
        }
        AccessDestination.GROUP_ONBOARDING -> GroupOnboardingScreen(state.selection) { intent ->
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
        AccessDestination.GROUP_CONTEXT -> {
            if (groupsNavigation == null) {
                GroupContextScreen(state.administration) { intent ->
                    onIntent(
                        when (intent) {
                            GroupContextIntent.SwitchGroup -> AccessIntent.SwitchGroup
                            GroupContextIntent.OpenSettings -> AccessIntent.OpenSettings
                            GroupContextIntent.OpenRoles -> AccessIntent.OpenMemberships
                            GroupContextIntent.OpenInvite -> AccessIntent.OpenInvite
                            GroupContextIntent.RequestLogout -> AccessIntent.RequestLogout
                        },
                    )
                }
            } else {
                GroupsNavigationHost(
                    navigation = groupsNavigation,
                    administration = state.administration,
                    onNavigationIntent = onGroupsIntent,
                    onOpenSettings = { onIntent(AccessIntent.OpenSettings) },
                    onSwitchGroup = { onIntent(AccessIntent.SwitchGroup) },
                    onRequestLogout = { onIntent(AccessIntent.RequestLogout) },
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
