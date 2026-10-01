package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupOnboarding
import br.com.saqz.groups.presentation.ui.home.HomeStepRow
import br.com.saqz.groups.presentation.ui.home.HomeStepState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_step_game_body
import br.com.saqz.groups.resources.home_step_game_title
import br.com.saqz.groups.resources.home_step_group_body
import br.com.saqz.groups.resources.home_step_group_title
import br.com.saqz.groups.resources.home_step_invite_body
import br.com.saqz.groups.resources.home_step_invite_title
import br.com.saqz.groups.resources.home_steps_title
import org.jetbrains.compose.resources.stringResource

internal object GroupOnboardingStepsTags {
    const val Steps = "group-onboarding-steps"

    fun step(number: Int) = "group-onboarding-step-$number"
}

/**
 * Os três passos do organizador, continuados da Início: criar o grupo (feito ao chegar aqui),
 * marcar o primeiro jogo e chamar a galera. Cada linha é um atalho para a tela da ação.
 *
 * Tudo é derivado do estado do grupo: jogo existe quando o guia não é mais
 * [GroupOnboarding.CreateGame]; a galera foi chamada quando há mais de uma pessoa no grupo.
 * Com os três feitos a lista some, e não volta.
 */
@Composable
internal fun GroupOnboardingSteps(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val steps = groupOnboardingSteps(state) ?: return
    Column(
        modifier = modifier.testTag(GroupOnboardingStepsTags.Steps),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_steps_title))
        SaqzCard(padded = false) {
            HomeStepRow(
                number = 1,
                title = stringResource(Res.string.home_step_group_title),
                body = stringResource(Res.string.home_step_group_body),
                state = HomeStepState.Done,
                onClick = { onIntent(GroupDetailsIntent.EditGroup) },
                modifier = Modifier.testTag(GroupOnboardingStepsTags.step(1)),
            )
            SaqzDivider()
            HomeStepRow(
                number = 2,
                title = stringResource(Res.string.home_step_game_title),
                body = stringResource(Res.string.home_step_game_body),
                state = steps.game,
                onClick = { onIntent(GroupDetailsIntent.CreateNextGame) },
                modifier = Modifier.testTag(GroupOnboardingStepsTags.step(2)),
            )
            SaqzDivider()
            HomeStepRow(
                number = 3,
                title = stringResource(Res.string.home_step_invite_title),
                body = stringResource(Res.string.home_step_invite_body),
                state = steps.invite,
                onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
                modifier = Modifier.testTag(GroupOnboardingStepsTags.step(3)),
            )
        }
    }
}

internal data class GroupOnboardingStepsUi(val game: HomeStepState, val invite: HomeStepState)

/** `null` quando os três passos estão feitos: a lista não existe mais. */
internal fun groupOnboardingSteps(state: GroupDetailsState): GroupOnboardingStepsUi? {
    // Para o gestor, `onboarding` só é CreateGame enquanto o grupo não tem jogo nenhum.
    val gameMarked = state.onboarding != GroupOnboarding.CreateGame
    val peopleInvited = state.memberCount > 1
    if (gameMarked && peopleInvited) return null
    return GroupOnboardingStepsUi(
        game = if (gameMarked) HomeStepState.Done else HomeStepState.Now,
        invite = when {
            peopleInvited -> HomeStepState.Done
            gameMarked -> HomeStepState.Now
            else -> HomeStepState.Next
        },
    )
}

@Preview
@Composable
private fun GroupOnboardingStepsPreview() = SaqzTheme {
    GroupOnboardingSteps(state = GroupHeroPreviewData.adminFirstGame, onIntent = {})
}
