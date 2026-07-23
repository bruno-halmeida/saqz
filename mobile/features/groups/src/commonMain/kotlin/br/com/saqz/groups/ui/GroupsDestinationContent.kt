package br.com.saqz.groups.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzLoadingState
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupAdministrationState
import br.com.saqz.groups.presentation.games.detail.GameDetailIntent
import br.com.saqz.groups.presentation.games.detail.GameDetailState
import br.com.saqz.groups.presentation.navigation.GroupsDestination
import br.com.saqz.groups.presentation.navigation.GroupsNavigationIntent
import br.com.saqz.groups.presentation.navigation.GroupsNavigationState
import br.com.saqz.groups.presentation.navigation.GroupsNavigationTags
import br.com.saqz.groups.presentation.photo.ExistingGroupPhoto
import br.com.saqz.groups.presentation.photo.GroupPhotoRenderState
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.groups_complete_profile
import br.com.saqz.groups.resources.groups_finance
import br.com.saqz.groups.resources.groups_games
import br.com.saqz.groups.resources.groups_notices
import br.com.saqz.groups.resources.groups_notices_placeholder
import br.com.saqz.groups.resources.groups_own_charges
import br.com.saqz.groups.resources.groups_people
import br.com.saqz.groups.ui.games.detail.GameDetailScreen
import org.jetbrains.compose.resources.stringResource

@Composable
fun GroupsDestinationContent(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    groupPhotoState: GroupPhotoState,
    groupPhotoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> GroupPhotoRenderState)?,
    gameDetailState: GameDetailState?,
    onGameDetailIntent: (GameDetailIntent) -> Unit,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectGroup: (String) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onRetryGroup: () -> Unit,
    onOpenInvite: () -> Unit,
    onRequestLogout: () -> Unit,
    loadListPhoto: (suspend (String) -> ExistingGroupPhoto?)? = null,
    athleteRoster: (@Composable () -> Unit)? = null,
) {
    when (navigation.destination) {
        GroupsDestination.SETUP -> Unit
        GroupsDestination.SELECTOR -> GroupsListScreen(
            memberships = navigation.memberships,
            onSelectGroup = onSelectGroup,
            onOpenCreateGroup = onOpenCreateGroup,
            loadListPhoto = loadListPhoto,
            groupPhotoPreview = groupPhotoPreview,
        )
        GroupsDestination.LOADING -> SaqzLoadingState(
            Modifier.fillMaxSize().testTag("group-loading"),
        )
        GroupsDestination.LOAD_ERROR -> GroupLoadError(onRetryGroup)
        GroupsDestination.HOME -> administration.group?.group?.let { group ->
            GroupDetailScreen(
                group = group,
                administration = administration,
                navigation = navigation,
                groupPhotoState = groupPhotoState,
                groupPhotoPreview = groupPhotoPreview,
                onNavigationIntent = onNavigationIntent,
                onOpenSettings = onOpenSettings,
                onOpenInvite = onOpenInvite,
                onRequestLogout = onRequestLogout,
            )
        }
        GroupsDestination.PROFILE_COMPLETION -> RoutePage(
            title = stringResource(Res.string.groups_complete_profile),
            body = "Complete modalidade e composição antes de criar jogos ou alterar presença e finanças.",
            tag = GroupsNavigationTags.ProfileCompletion,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.PEOPLE -> if (athleteRoster != null) {
            athleteRoster()
        } else {
            RoutePage(
                title = stringResource(Res.string.groups_people),
                body = "Gerencie participantes, funções e convites privados.",
                tag = GroupsNavigationTags.People,
                onNavigationIntent = onNavigationIntent,
            )
        }
        GroupsDestination.GAMES -> RoutePage(
            title = stringResource(Res.string.groups_games),
            body = if (navigation.access.canMutateOperations) {
                "Consulte jogos e crie novas partidas."
            } else {
                "Consulte os jogos deste grupo."
            },
            tag = GroupsNavigationTags.Games,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.GAME_DETAIL -> if (gameDetailState == null) {
            RoutePage(
                title = "Detalhes do jogo",
                body = "Jogo ${navigation.gameId.orEmpty()}",
                tag = GroupsNavigationTags.GameDetail,
                onNavigationIntent = onNavigationIntent,
            )
        } else {
            Box(Modifier.fillMaxSize().testTag(GroupsNavigationTags.GameDetail)) {
                GameDetailScreen(gameDetailState, onGameDetailIntent)
            }
        }
        GroupsDestination.FINANCE -> RoutePage(
            title = stringResource(Res.string.groups_finance),
            body = "Cobranças e despesas são registros manuais; o Saqz não processa pagamentos.",
            tag = GroupsNavigationTags.Finance,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.OWN_CHARGES -> RoutePage(
            title = stringResource(Res.string.groups_own_charges),
            body = "Somente as suas cobranças são exibidas.",
            tag = GroupsNavigationTags.OwnCharges,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.NOTICES -> RoutePage(
            title = stringResource(Res.string.groups_notices),
            body = stringResource(Res.string.groups_notices_placeholder),
            tag = GroupsNavigationTags.NoticesScreen,
            onNavigationIntent = onNavigationIntent,
        )
        GroupsDestination.MORE -> GroupMoreScreen(navigation, onNavigationIntent)
    }
}

@Composable
private fun GroupMoreScreen(
    navigation: GroupsNavigationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(GroupsNavigationTags.More),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        if (navigation.access.showPeople) {
            SaqzButton(
                label = stringResource(Res.string.groups_people),
                onClick = { onNavigationIntent(GroupsNavigationIntent.OpenPeople) },
                modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.MorePeople),
                variant = SaqzButtonVariant.Secondary,
            )
        }
        if (navigation.access.showFinance) {
            SaqzButton(
                label = stringResource(
                    if (navigation.access.financeDestination == GroupsDestination.OWN_CHARGES) {
                        Res.string.groups_own_charges
                    } else {
                        Res.string.groups_finance
                    },
                ),
                onClick = { onNavigationIntent(GroupsNavigationIntent.OpenFinance) },
                modifier = Modifier.fillMaxWidth().testTag(GroupsNavigationTags.MoreFinance),
                variant = SaqzButtonVariant.Secondary,
            )
        }
    }
}
