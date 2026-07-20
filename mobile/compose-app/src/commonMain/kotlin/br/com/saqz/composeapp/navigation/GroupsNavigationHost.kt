package br.com.saqz.composeapp.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.data.GroupProfileStatusDto
import br.com.saqz.groups.presentation.GroupAdministrationState

internal object GroupsNavigationTags {
    const val Home = "groups-home"
    const val ProfileCompletion = "groups-profile-completion"
    const val People = "groups-people"
    const val Games = "groups-games"
    const val GameDetail = "groups-game-detail"
    const val Finance = "groups-finance"
    const val OwnCharges = "groups-own-charges"
}

@Composable
internal fun GroupsNavigationHost(
    navigation: GroupsNavigationState,
    administration: GroupAdministrationState,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
    onOpenSettings: () -> Unit,
    onSwitchGroup: () -> Unit,
    onRequestLogout: () -> Unit,
) {
    val group = administration.group?.group ?: return
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(SaqzTheme.metrics.horizontalPadding)
            .testTag(navigation.destination.tag()),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Text(group.name, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
        Text(group.role.name, color = SaqzTheme.colors.textSecondary)

        when (navigation.destination) {
            GroupsDestination.HOME -> {
                if (group.profileStatus == GroupProfileStatusDto.INCOMPLETE) {
                    Text(
                        "O perfil deste grupo precisa ser concluído. Os dados existentes continuam disponíveis para leitura.",
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
                if (navigation.access.canCompleteProfile) {
                    RouteAction("Concluir perfil", GroupsNavigationTags.ProfileCompletion, onClick = {
                        onNavigationIntent(GroupsNavigationIntent.OpenProfileCompletion)
                    })
                }
                if (navigation.access.showPeople) RouteAction("Pessoas", GroupsNavigationTags.People, onClick = {
                    onNavigationIntent(GroupsNavigationIntent.OpenPeople)
                })
                if (navigation.access.showGames) RouteAction("Jogos", GroupsNavigationTags.Games, onClick = {
                    onNavigationIntent(GroupsNavigationIntent.OpenGames)
                })
                if (navigation.access.showFinance) RouteAction(
                    if (navigation.access.financeDestination == GroupsDestination.OWN_CHARGES) "Minhas cobranças" else "Financeiro",
                    GroupsNavigationTags.Finance,
                    onClick = { onNavigationIntent(GroupsNavigationIntent.OpenFinance) },
                )
                if (administration.actions.canEditSettings) RouteAction("Configurações", "groups-settings", onOpenSettings)
                RouteAction("Trocar grupo", "groups-switch", onSwitchGroup)
                RouteAction("Sair", "groups-logout", onRequestLogout, SaqzButtonVariant.Ghost)
            }
            GroupsDestination.PROFILE_COMPLETION -> RoutePage(
                "Concluir perfil do grupo",
                "Complete modalidade e composição antes de criar jogos ou alterar presença e finanças.",
                onNavigationIntent,
            )
            GroupsDestination.PEOPLE -> RoutePage(
                "Pessoas",
                "Gerencie participantes, funções e convites privados.",
                onNavigationIntent,
            )
            GroupsDestination.GAMES -> RoutePage(
                "Jogos",
                if (navigation.access.canMutateOperations) "Consulte jogos e crie novas partidas." else "Consulte os jogos deste grupo.",
                onNavigationIntent,
            )
            GroupsDestination.GAME_DETAIL -> RoutePage(
                "Detalhes do jogo",
                "Jogo ${navigation.gameId.orEmpty()}",
                onNavigationIntent,
            )
            GroupsDestination.FINANCE -> RoutePage(
                "Financeiro",
                "Cobranças e despesas são registros manuais; o Saqz não processa pagamentos.",
                onNavigationIntent,
            )
            GroupsDestination.OWN_CHARGES -> RoutePage(
                "Minhas cobranças",
                "Somente as suas cobranças são exibidas.",
                onNavigationIntent,
            )
            GroupsDestination.SETUP,
            GroupsDestination.SELECTOR,
            GroupsDestination.LOADING,
            GroupsDestination.LOAD_ERROR,
            -> Unit
        }
    }
}

@Composable
private fun RoutePage(
    title: String,
    body: String,
    onNavigationIntent: (GroupsNavigationIntent) -> Unit,
) {
    Text(title, style = SaqzTheme.typography.lead, color = SaqzTheme.colors.textPrimary)
    Text(body, color = SaqzTheme.colors.textSecondary)
    RouteAction(
        "Voltar ao grupo",
        "groups-back",
        onClick = { onNavigationIntent(GroupsNavigationIntent.OpenHome) },
    )
}

@Composable
private fun RouteAction(
    label: String,
    tag: String,
    onClick: () -> Unit,
    variant: SaqzButtonVariant = SaqzButtonVariant.Secondary,
) = SaqzButton(
    label,
    onClick,
    Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag),
    variant,
)

private fun GroupsDestination.tag(): String = when (this) {
    GroupsDestination.HOME -> GroupsNavigationTags.Home
    GroupsDestination.PROFILE_COMPLETION -> GroupsNavigationTags.ProfileCompletion
    GroupsDestination.PEOPLE -> GroupsNavigationTags.People
    GroupsDestination.GAMES -> GroupsNavigationTags.Games
    GroupsDestination.GAME_DETAIL -> GroupsNavigationTags.GameDetail
    GroupsDestination.FINANCE -> GroupsNavigationTags.Finance
    GroupsDestination.OWN_CHARGES -> GroupsNavigationTags.OwnCharges
    else -> "groups-unscoped"
}
