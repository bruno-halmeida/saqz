package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.access.data.PersistedRoleDto
import br.com.saqz.access.data.GroupApi
import br.com.saqz.access.data.RolesInvitesApi
import br.com.saqz.access.port.AuthState
import br.com.saqz.access.port.AuthStateListener
import br.com.saqz.access.port.Cancelable
import br.com.saqz.access.port.NativeFailureCode
import br.com.saqz.access.port.OperationResult
import br.com.saqz.access.port.ResultCallback
import br.com.saqz.access.port.TokenCallback
import br.com.saqz.access.port.TokenResult as NativeTokenResult
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.DeferredInviteCoordinator
import br.com.saqz.access.presentation.GroupAdministrationState
import br.com.saqz.access.presentation.GroupAdministrationCoordinator
import br.com.saqz.access.presentation.GroupSelectionState
import br.com.saqz.access.presentation.GroupSelectionCoordinator
import br.com.saqz.access.presentation.InviteUiError
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.SessionAccessState
import br.com.saqz.access.presentation.SessionAccessStateMachine
import br.com.saqz.access.ui.BootstrapAccessScreen
import br.com.saqz.access.ui.CreateGroupScreen
import br.com.saqz.access.ui.ExpireInviteConfirmationDialog
import br.com.saqz.access.ui.GroupContextScreen
import br.com.saqz.access.ui.GroupOnboardingScreen
import br.com.saqz.access.ui.GroupSettingsScreen
import br.com.saqz.access.ui.InviteManagementScreen
import br.com.saqz.access.ui.InviteToolState
import br.com.saqz.access.ui.LoginScreen
import br.com.saqz.access.ui.LogoutConfirmationDialog
import br.com.saqz.access.ui.MembershipAdministrationScreen
import br.com.saqz.access.ui.NameCompletionScreen
import br.com.saqz.access.ui.PasswordResetScreen
import br.com.saqz.access.ui.RegistrationScreen
import br.com.saqz.access.ui.VerificationScreen
import br.com.saqz.designsystem.component.SaqzLoadingState
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

@Immutable
internal data class AccessRootSnapshot(
    val authObserved: Boolean,
    val authentication: AuthenticationState,
    val session: SessionAccessState,
    val selection: GroupSelectionState,
    val administration: GroupAdministrationState,
    val page: AccessPage = AccessPage.CONTEXT,
    val createName: String = "",
    val createTimeZone: String = "",
    val settingsName: String = "",
    val settingsTimeZone: String = "",
    val invite: InviteToolState = InviteToolState(),
    val showLogoutConfirmation: Boolean = false,
    val showExpireConfirmation: Boolean = false,
)

internal data class AccessRootActions(
    val updateName: (String) -> Unit = {},
    val updateEmail: (String) -> Unit = {},
    val updatePassword: (String) -> Unit = {},
    val submitLogin: () -> Unit = {},
    val submitGoogle: () -> Unit = {},
    val submitRegistration: () -> Unit = {},
    val submitReset: () -> Unit = {},
    val showLogin: () -> Unit = {},
    val showRegistration: () -> Unit = {},
    val showReset: () -> Unit = {},
    val confirmVerification: () -> Unit = {},
    val resendVerification: () -> Unit = {},
    val completeName: () -> Unit = {},
    val retryBootstrap: () -> Unit = {},
    val selectGroup: (String) -> Unit = {},
    val openCreateGroup: () -> Unit = {},
    val retryGroup: () -> Unit = {},
    val createNameChanged: (String) -> Unit = {},
    val createTimeZoneChanged: (String) -> Unit = {},
    val createGroup: () -> Unit = {},
    val closePage: () -> Unit = {},
    val switchGroup: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val openMemberships: () -> Unit = {},
    val openInvite: () -> Unit = {},
    val requestLogout: () -> Unit = {},
    val confirmLogout: () -> Unit = {},
    val cancelLogout: () -> Unit = {},
    val settingsNameChanged: (String) -> Unit = {},
    val settingsTimeZoneChanged: (String) -> Unit = {},
    val saveSettings: () -> Unit = {},
    val reloadSettings: () -> Unit = {},
    val changeRole: (String, PersistedRoleDto) -> Unit = { _, _ -> },
    val generateInvite: () -> Unit = {},
    val shareInvite: (String) -> Unit = {},
    val requestExpireInvite: () -> Unit = {},
    val confirmExpireInvite: () -> Unit = {},
    val cancelExpireInvite: () -> Unit = {},
    val retryInvite: () -> Unit = {},
)

@Composable
internal fun AuthenticatedAccessRoot(state: AccessRootSnapshot, actions: AccessRootActions) {
    val desired = state.destination()
    val stack = AccessDestinationStack(desired).also { it.replace(desired) }
    val destination = stack.entries.single()
    key(destination) {
        Box(Modifier.fillMaxSize().testTag(AccessRootTag)) {
            DestinationContent(destination, state, actions)
        }
    }
}

@Composable
internal fun AuthenticatedAccessRuntime(runtime: AccessRuntime) {
    DisposableEffect(runtime) {
        runtime.start()
        onDispose { }
    }

    val authentication by runtime.authentication.state.collectAsState()
    val session by runtime.session.state.collectAsState()
    val selection by runtime.selection.state.collectAsState()
    val administration by runtime.administration.state.collectAsState()
    val invite by runtime.inviteToolState.collectAsState()
    var page by remember { mutableStateOf(AccessPage.CONTEXT) }
    var createName by remember { mutableStateOf("") }
    var createTimeZone by remember { mutableStateOf("") }
    var createRequestId by remember { mutableStateOf(runtime.newRequestId()) }
    var settingsName by remember { mutableStateOf("") }
    var settingsTimeZone by remember { mutableStateOf("") }
    var logoutConfirmation by remember { mutableStateOf(false) }
    var expireConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        val ready = session as? SessionAccessState.Ready
        runtime.invites.setSessionReady(ready != null)
        if (ready != null) runtime.selection.reconcile(ready.session)
        if (session is SessionAccessState.SignedOut) {
            page = AccessPage.CONTEXT
            logoutConfirmation = false
        }
    }
    LaunchedEffect(selection) {
        val selected = selection as? GroupSelectionState.Selected
        if (selected != null) {
            runtime.administration.setGroup(selected.group)
            page = AccessPage.CONTEXT
        }
    }

    val snapshot = AccessRootSnapshot(
        authObserved = runtime.authObserved,
        authentication = authentication,
        session = session,
        selection = selection,
        administration = administration,
        page = page,
        createName = createName,
        createTimeZone = createTimeZone,
        settingsName = settingsName,
        settingsTimeZone = settingsTimeZone,
        invite = invite,
        showLogoutConfirmation = logoutConfirmation,
        showExpireConfirmation = expireConfirmation,
    )
    val actions = AccessRootActions(
        updateName = { value ->
            if (session is SessionAccessState.CompletingName) {
                runtime.session.onIntent(SessionIntent.UpdateName(value))
            } else {
                runtime.authentication.onIntent(AuthenticationIntent.UpdateName(value))
            }
        },
        updateEmail = { runtime.authentication.onIntent(AuthenticationIntent.UpdateEmail(it)) },
        updatePassword = { runtime.authentication.onIntent(AuthenticationIntent.UpdatePassword(it)) },
        submitLogin = { runtime.authentication.onIntent(AuthenticationIntent.SubmitPasswordLogin) },
        submitGoogle = { runtime.authentication.onIntent(AuthenticationIntent.SubmitGoogleLogin) },
        submitRegistration = { runtime.authentication.onIntent(AuthenticationIntent.SubmitRegistration) },
        submitReset = { runtime.authentication.onIntent(AuthenticationIntent.SubmitPasswordReset) },
        showLogin = { runtime.authentication.onIntent(AuthenticationIntent.ShowLogin) },
        showRegistration = { runtime.authentication.onIntent(AuthenticationIntent.ShowRegistration) },
        showReset = { runtime.authentication.onIntent(AuthenticationIntent.ShowPasswordReset) },
        confirmVerification = { runtime.session.onIntent(SessionIntent.ConfirmVerification) },
        resendVerification = { runtime.session.onIntent(SessionIntent.ResendVerification) },
        completeName = { runtime.session.onIntent(SessionIntent.CompleteName) },
        retryBootstrap = { runtime.session.onIntent(SessionIntent.RetryBootstrap) },
        selectGroup = runtime.selection::select,
        openCreateGroup = {
            createName = ""
            createTimeZone = ""
            createRequestId = runtime.newRequestId()
            page = AccessPage.CREATE_GROUP
        },
        retryGroup = runtime.selection::retry,
        createNameChanged = { createName = it },
        createTimeZoneChanged = { createTimeZone = it },
        createGroup = { runtime.administration.createGroup(createRequestId, createName, createTimeZone) },
        closePage = { page = AccessPage.CONTEXT },
        switchGroup = {
            page = AccessPage.CONTEXT
            (session as? SessionAccessState.Ready)?.let { runtime.showGroupSelector(it) }
        },
        openSettings = {
            administration.group?.group?.let {
                settingsName = it.name
                settingsTimeZone = it.timeZone
                page = AccessPage.SETTINGS
            }
        },
        openMemberships = {
            runtime.administration.loadMemberships()
            page = AccessPage.MEMBERSHIPS
        },
        openInvite = { page = AccessPage.INVITE },
        requestLogout = { logoutConfirmation = true },
        confirmLogout = {
            logoutConfirmation = false
            runtime.invites.onLogout()
            runtime.session.onIntent(SessionIntent.Logout)
        },
        cancelLogout = { logoutConfirmation = false },
        settingsNameChanged = { settingsName = it },
        settingsTimeZoneChanged = { settingsTimeZone = it },
        saveSettings = { runtime.administration.updateSettings(settingsName, settingsTimeZone) },
        reloadSettings = {
            administration.group?.group?.let {
                settingsName = it.name
                settingsTimeZone = it.timeZone
            }
        },
        changeRole = runtime.administration::changeRole,
        generateInvite = runtime::rotateInvite,
        shareInvite = runtime::shareInvite,
        requestExpireInvite = { expireConfirmation = true },
        confirmExpireInvite = {
            expireConfirmation = false
            runtime.expireInvite()
        },
        cancelExpireInvite = { expireConfirmation = false },
        retryInvite = runtime::rotateInvite,
    )
    AuthenticatedAccessRoot(snapshot, actions)
}

internal class AccessRuntime(
    private val dependencies: SaqzAppDependencies,
    private val scope: CoroutineScope,
) {
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
    private val roles = RolesInvitesApi(authenticatedNetwork)
    val session = SessionAccessStateMachine(dependencies.auth, dependencies.localState, SessionApi(authenticatedNetwork), scope)
    val authentication = AuthenticationStateMachine(dependencies.auth) {
        session.onIntent(SessionIntent.Accept(it))
    }
    val selection = GroupSelectionCoordinator(dependencies.localState, groups, scope)
    val administration = GroupAdministrationCoordinator(groups, roles, scope, selection::select)
    val invites = DeferredInviteCoordinator(dependencies.links, dependencies.localState, roles, scope, selection::select)
    private val mutableInviteToolState = MutableStateFlow(InviteToolState())
    val inviteToolState = mutableInviteToolState.asStateFlow()
    var authObserved by mutableStateOf(false)
        private set
    private var authSubscription: Cancelable? = null

    init {
        invalidator.delegate = session
    }

    fun start() {
        if (authSubscription != null) return
        authSubscription = dependencies.auth.observe(object : AuthStateListener {
            override fun onStateChanged(state: AuthState) {
                authObserved = true
                when (state) {
                    AuthState.SignedOut -> authentication.onIntent(AuthenticationIntent.ShowLogin)
                    is AuthState.SignedIn -> session.onIntent(
                        SessionIntent.Accept(AuthTransition.Authenticated(state.user)),
                    )
                }
            }
        })
        invites.start()
        invites.restore()
    }

    fun close() {
        authSubscription?.cancel()
        authSubscription = null
        invites.stop()
        network.close()
    }

    fun showGroupSelector(ready: SessionAccessState.Ready) {
        dependencies.localState.writeSelectedGroupId(null, resultCallback {
            selection.reconcile(ready.session)
        })
    }

    fun rotateInvite() {
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

    fun expireInvite() {
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

    fun shareInvite(url: String) {
        dependencies.share.share(url, resultCallback { result ->
            if (result is OperationResult.Failure) {
                mutableInviteToolState.value = mutableInviteToolState.value.copy(error = InviteUiError.UNAVAILABLE)
            }
        })
    }

    fun newRequestId(): String {
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
private fun DestinationContent(destination: AccessDestination, state: AccessRootSnapshot, actions: AccessRootActions) {
    when (destination) {
        AccessDestination.STARTING -> SaqzLoadingState()
        AccessDestination.LOGIN -> LoginScreen(
            state.authentication,
            actions.updateEmail,
            actions.updatePassword,
            actions.submitLogin,
            actions.submitGoogle,
            actions.showRegistration,
            actions.showReset,
        )
        AccessDestination.REGISTRATION -> RegistrationScreen(
            state.authentication,
            actions.updateName,
            actions.updateEmail,
            actions.updatePassword,
            actions.submitRegistration,
            actions.showLogin,
        )
        AccessDestination.PASSWORD_RESET -> PasswordResetScreen(
            state.authentication,
            actions.updateEmail,
            actions.submitReset,
            actions.showLogin,
        )
        AccessDestination.VERIFICATION -> VerificationScreen(
            state.session as SessionAccessState.AwaitingVerification,
            actions.confirmVerification,
            actions.resendVerification,
        )
        AccessDestination.NAME_COMPLETION -> NameCompletionScreen(
            state.session as SessionAccessState.CompletingName,
            actions.updateName,
            actions.completeName,
        )
        AccessDestination.BOOTSTRAP -> BootstrapAccessScreen(state.session, actions.retryBootstrap)
        AccessDestination.GROUP_ONBOARDING -> GroupOnboardingScreen(
            state.selection,
            actions.selectGroup,
            actions.openCreateGroup,
            actions.retryGroup,
        )
        AccessDestination.GROUP_CONTEXT -> {
            GroupContextScreen(
                state.administration,
                actions.switchGroup,
                actions.openSettings,
                actions.openMemberships,
                actions.openInvite,
                actions.requestLogout,
            )
            if (state.showLogoutConfirmation) {
                LogoutConfirmationDialog(actions.confirmLogout, actions.cancelLogout)
            }
        }
        AccessDestination.CREATE_GROUP -> CreateGroupScreen(
            state.administration,
            state.createName,
            state.createTimeZone,
            actions.createNameChanged,
            actions.createTimeZoneChanged,
            actions.createGroup,
            actions.closePage,
        )
        AccessDestination.SETTINGS -> GroupSettingsScreen(
            state.administration,
            state.settingsName,
            state.settingsTimeZone,
            actions.settingsNameChanged,
            actions.settingsTimeZoneChanged,
            actions.saveSettings,
            actions.reloadSettings,
            actions.closePage,
        )
        AccessDestination.MEMBERSHIPS -> MembershipAdministrationScreen(
            state.administration,
            actions.changeRole,
            actions.closePage,
        )
        AccessDestination.INVITE -> {
            InviteManagementScreen(
                state.administration.actions,
                state.invite,
                actions.generateInvite,
                actions.shareInvite,
                actions.requestExpireInvite,
                actions.retryInvite,
                actions.closePage,
            )
            if (state.showExpireConfirmation) {
                ExpireInviteConfirmationDialog(actions.confirmExpireInvite, actions.cancelExpireInvite)
            }
        }
    }
}
