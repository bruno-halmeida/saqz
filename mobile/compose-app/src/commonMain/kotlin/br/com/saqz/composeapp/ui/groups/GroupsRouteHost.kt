package br.com.saqz.composeapp.ui.groups

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.showsGroupChrome
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.ui.GroupsDestinationContent
import br.com.saqz.groups.ui.GroupsRouteChrome
import br.com.saqz.groups.ui.GroupsSelectorChrome

@Composable
internal fun GroupsRouteHost(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    groupPhotoState: GroupPhotoState = GroupPhotoState(),
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)? = null,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    gameDetailState: GameDetailState? = null,
    onGameDetailIntent: (GameDetailIntent) -> Unit = {},
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onRetryGroup: () -> Unit,
    onOpenInvite: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    val content = @Composable {
        GroupsDestinationContent(
            navigation = navigation,
            administration = administration,
            groupPhotoState = groupPhotoState,
            groupPhotoPreview = groupPhotoPreview,
            gameDetailState = gameDetailState,
            onGameDetailIntent = onGameDetailIntent,
            onNavigationIntent = onNavigationIntent,
            onOpenSettings = onOpenSettings,
            onSelectGroup = onSelectGroup,
            onOpenCreateGroup = onOpenCreateGroup,
            onRetryGroup = onRetryGroup,
            onOpenInvite = onOpenInvite,
            onRequestLogout = onRequestLogout,
            loadListPhoto = loadListPhoto,
        )
    }
    when {
        navigation.destination == GroupsDestination.SELECTOR -> GroupsSelectorChrome(
            navigation = navigation,
            onNavigationIntent = onNavigationIntent,
            content = content,
        )
        navigation.groupId != null && navigation.destination.showsGroupChrome() -> GroupsRouteChrome(
            navigation = navigation,
            administration = administration,
            onNavigationIntent = onNavigationIntent,
            onOpenSettings = onOpenSettings,
            content = content,
        )
        else -> content()
    }
}
