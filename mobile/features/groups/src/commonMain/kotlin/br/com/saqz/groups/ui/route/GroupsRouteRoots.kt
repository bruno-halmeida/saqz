package br.com.saqz.groups.ui.route

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.GroupAdministrationIntent
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationAccess
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.route.FinancePlaceholderMode
import br.com.saqz.groups.presentation.route.FinancePlaceholderRouteViewModel
import br.com.saqz.groups.presentation.route.GroupAdministrationRouteViewModel
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderIntent
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderMode
import br.com.saqz.groups.presentation.route.GroupContentPlaceholderRouteViewModel
import br.com.saqz.groups.presentation.route.GroupHomeRouteIntent
import br.com.saqz.groups.presentation.route.GroupHomeRouteViewModel
import br.com.saqz.groups.presentation.route.GroupInviteRouteIntent
import br.com.saqz.groups.presentation.route.GroupInviteRouteViewModel
import br.com.saqz.groups.presentation.route.GroupSelectionRouteViewModel
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_complete_profile
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_games
import br.com.saqz.groups.resources.groups_notices
import br.com.saqz.groups.resources.groups_notices_placeholder
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import br.com.saqz.groups.ui.ExpireInviteConfirmationDialog
import br.com.saqz.groups.ui.ExpireInviteConfirmationIntent
import br.com.saqz.groups.ui.GroupOnboardingIntent
import br.com.saqz.groups.ui.GroupSettingsIntent
import br.com.saqz.groups.ui.GroupSettingsScreen
import br.com.saqz.groups.ui.GroupSettingsUiState
import br.com.saqz.groups.ui.InviteManagementIntent
import br.com.saqz.groups.ui.InviteManagementScreen
import br.com.saqz.groups.ui.InviteManagementUiState
import br.com.saqz.groups.ui.LogoutConfirmationDialog
import br.com.saqz.groups.ui.LogoutConfirmationIntent
import br.com.saqz.groups.ui.MembershipAdministrationIntent
import br.com.saqz.groups.ui.MembershipAdministrationScreen
import br.com.saqz.groups.ui.group.GroupDetailScreen
import br.com.saqz.groups.ui.group.GroupLoadError
import br.com.saqz.groups.ui.group.GroupMoreScreen
import br.com.saqz.groups.ui.group.GroupsListScreen
import br.com.saqz.groups.ui.group.RoutePage
import org.jetbrains.compose.resources.stringResource

/**
 * Per-route Nav3 entry Roots (T24/T25 wiring): each Root renders an existing feature
 * screen from its dedicated route-adapter ViewModel (T12/T13/T15) instead of the
 * legacy shared enum projection. Roots receive their ViewModel instance and semantic
 * callbacks from the composition root; they never import Nav3 UI or resolve DI.
 */

/** Selector root: memberships come from the selection machine's own state. */
@Composable
fun GroupSelectorRoot(
    viewModel: GroupSelectionRouteViewModel,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupsListScreen(
        memberships = (state as? GroupSelectionState.Selector)?.memberships.orEmpty(),
        onSelectGroup = { groupId -> viewModel.onIntent(GroupOnboardingIntent.Select(groupId)) },
        onOpenCreateGroup = { viewModel.onIntent(GroupOnboardingIntent.OpenCreateGroup) },
        loadListPhoto = loadListPhoto,
        groupPhotoPreview = groupPhotoPreview,
    )
}

@Composable
fun GroupLoadingRoot() {
    SaqzLoadingState(Modifier.fillMaxSize().testTag("group-loading"))
}

@Composable
fun GroupLoadErrorRoot(viewModel: GroupSelectionRouteViewModel) {
    GroupLoadError(onRetry = { viewModel.onIntent(GroupOnboardingIntent.Retry) })
}

/**
 * GroupHome root: projects administration + photo through [GroupHomeRouteViewModel]
 * and keeps [GroupDetailScreen] unchanged by synthesizing its navigation projection
 * from the group's own policy (the legacy `GroupsNavigationState` fields die in T25).
 */
@Composable
fun GroupHomeRoot(
    viewModel: GroupHomeRouteViewModel,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
    onNavigate: (GroupsNavigationIntent) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val group = state.administration.group?.group ?: return
    GroupDetailScreen(
        group = group,
        administration = state.administration,
        navigation = GroupsNavigationState(
            destination = GroupsDestination.HOME,
            groupId = group.id.value,
            access = navigationAccessFor(group),
        ),
        groupPhotoState = state.photo,
        groupPhotoPreview = groupPhotoPreview,
        onNavigationIntent = onNavigate,
        onOpenSettings = { viewModel.onIntent(GroupHomeRouteIntent.OpenSettings) },
        onOpenInvite = { viewModel.onIntent(GroupHomeRouteIntent.OpenInvite) },
        onRequestLogout = { viewModel.onIntent(GroupHomeRouteIntent.RequestLogout) },
    )
    if (state.showLogoutConfirmation) {
        LogoutConfirmationDialog { intent ->
            viewModel.onIntent(
                when (intent) {
                    LogoutConfirmationIntent.Confirm -> GroupHomeRouteIntent.ConfirmLogout
                    LogoutConfirmationIntent.Cancel -> GroupHomeRouteIntent.CancelLogout
                },
            )
        }
    }
}

/** Placeholder roots (ProfileCompletion/People/Games/Notices) keep their exact copy. */
@Composable
fun GroupPlaceholderRoot(
    viewModel: GroupContentPlaceholderRouteViewModel,
    mode: GroupContentPlaceholderMode,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back: (GroupsNavigationIntent) -> Unit = { if (it == GroupsNavigationIntent.OpenHome) onBack() }
    when (mode) {
        GroupContentPlaceholderMode.PROFILE_COMPLETION -> RoutePage(
            title = stringResource(Res.string.groups_complete_profile),
            body = "Complete modalidade e composição antes de criar jogos ou alterar presença e finanças.",
            tag = GroupsNavigationTags.ProfileCompletion,
            onNavigationIntent = back,
        )
        GroupContentPlaceholderMode.PEOPLE -> RoutePage(
            title = stringResource(Res.string.groups_people),
            body = "Gerencie participantes, funções e convites privados.",
            tag = GroupsNavigationTags.People,
            onNavigationIntent = back,
        )
        GroupContentPlaceholderMode.GAMES -> RoutePage(
            title = stringResource(Res.string.groups_games),
            body = if (state.access.operationsMutable) {
                "Consulte jogos e crie novas partidas."
            } else {
                "Consulte os jogos deste grupo."
            },
            tag = GroupsNavigationTags.Games,
            onNavigationIntent = back,
        )
        GroupContentPlaceholderMode.NOTICES -> RoutePage(
            title = stringResource(Res.string.groups_notices),
            body = stringResource(Res.string.groups_notices_placeholder),
            tag = GroupsNavigationTags.NoticesScreen,
            onNavigationIntent = back,
        )
        GroupContentPlaceholderMode.MORE -> GroupMoreRootContent(viewModel)
    }
}

@Composable
private fun GroupMoreRootContent(viewModel: GroupContentPlaceholderRouteViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupMoreScreen(
        access = state.access,
        onOpenPeople = { viewModel.onIntent(GroupContentPlaceholderIntent.OpenPeople) },
        onOpenFinance = { viewModel.onIntent(GroupContentPlaceholderIntent.OpenFinance) },
    )
}

/** Settings root: pre-save name/timezone edits are transient UI state seeded from the group. */
@Composable
fun GroupSettingsRoot(viewModel: GroupAdministrationRouteViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val group = state.group?.group
    var name by rememberSaveable(group?.id?.value) { mutableStateOf(group?.name.orEmpty()) }
    var timeZone by rememberSaveable(group?.id?.value) { mutableStateOf(group?.timeZone?.id.orEmpty()) }
    GroupSettingsScreen(
        GroupSettingsUiState(administration = state, name = name, timeZone = timeZone),
    ) { intent ->
        when (intent) {
            is GroupSettingsIntent.UpdateName -> name = intent.value
            is GroupSettingsIntent.UpdateTimeZone -> timeZone = intent.value
            GroupSettingsIntent.Save ->
                viewModel.onIntent(GroupAdministrationIntent.UpdateSettings(name, timeZone))
            GroupSettingsIntent.Reload -> {
                name = group?.name.orEmpty()
                timeZone = group?.timeZone?.id.orEmpty()
            }
            GroupSettingsIntent.Back -> onBack()
        }
    }
}

@Composable
fun GroupMembershipsRoot(viewModel: GroupAdministrationRouteViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MembershipAdministrationScreen(state) { intent ->
        when (intent) {
            is MembershipAdministrationIntent.ChangeRole ->
                viewModel.onIntent(GroupAdministrationIntent.ChangeRole(intent.userId, intent.role))
            MembershipAdministrationIntent.Back -> onBack()
        }
    }
}

/** Invite root: link state from the invite adapter; capability gating from administration. */
@Composable
fun GroupInviteRoot(
    inviteViewModel: GroupInviteRouteViewModel,
    administrationViewModel: GroupAdministrationRouteViewModel,
    onBack: () -> Unit,
) {
    val inviteState by inviteViewModel.state.collectAsStateWithLifecycle()
    val administration by administrationViewModel.state.collectAsStateWithLifecycle()
    InviteManagementScreen(
        InviteManagementUiState(administration.actions, inviteState.invite),
    ) { intent ->
        inviteViewModel.onIntent(
            when (intent) {
                InviteManagementIntent.Generate -> GroupInviteRouteIntent.Rotate
                is InviteManagementIntent.Share -> GroupInviteRouteIntent.ShareInvite(intent.url)
                InviteManagementIntent.RequestExpire -> GroupInviteRouteIntent.RequestExpire
                InviteManagementIntent.Retry -> GroupInviteRouteIntent.Retry
                InviteManagementIntent.Back -> return@InviteManagementScreen onBack()
            },
        )
    }
    if (inviteState.showExpireConfirmation) {
        ExpireInviteConfirmationDialog { intent ->
            inviteViewModel.onIntent(
                when (intent) {
                    ExpireInviteConfirmationIntent.Confirm -> GroupInviteRouteIntent.ConfirmExpire
                    ExpireInviteConfirmationIntent.Cancel -> GroupInviteRouteIntent.CancelExpire
                },
            )
        }
    }
}

/** Finance/OwnCharges placeholder roots keep the exact legacy copy and tags. */
@Composable
fun FinancePlaceholderRoot(viewModel: FinancePlaceholderRouteViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back: (GroupsNavigationIntent) -> Unit = { if (it == GroupsNavigationIntent.OpenHome) onBack() }
    when (state.mode) {
        FinancePlaceholderMode.FINANCE -> RoutePage(
            title = stringResource(Res.string.groups_finance),
            body = "Cobranças e despesas são registros manuais; o Saqz não processa pagamentos.",
            tag = GroupsNavigationTags.Finance,
            onNavigationIntent = back,
        )
        FinancePlaceholderMode.OWN_CHARGES -> RoutePage(
            title = stringResource(Res.string.groups_own_charges),
            body = "Somente as suas cobranças são exibidas.",
            tag = GroupsNavigationTags.OwnCharges,
            onNavigationIntent = back,
        )
    }
}

/** Policy-derived navigation projection for [GroupDetailScreen] (replaces the legacy VM's accessFor). */
fun navigationAccessFor(group: Group): GroupsNavigationAccess {
    val policy = br.com.saqz.groups.presentation.GroupRoutePolicy.evaluate(group.role, group.profileStatus)
    return GroupsNavigationAccess(
        showPeople = policy.peopleVisible,
        showGames = policy.gamesVisible,
        showFinance = true,
        canCompleteProfile = policy.profileCompletionVisible,
        canMutateOperations = policy.operationsMutable,
        financeDestination = if (policy.financeVisibility == br.com.saqz.groups.presentation.GroupFinanceVisibility.ORGANIZER) {
            GroupsDestination.FINANCE
        } else {
            GroupsDestination.OWN_CHARGES
        },
    )
}
