package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.subscription_required_body
import br.com.saqz.groups.resources.subscription_required_title_trial_ended
import br.com.saqz.groups.resources.subscription_required_view_plans
import org.jetbrains.compose.resources.stringResource

internal const val HomeTrialEndedTag = "home-trial-ended"
internal const val HomeTrialEndedPlansTag = "home-trial-ended-plans"

/**
 * O teste do Organizador acabou e o dono ficou sem plano: o card explica o que mudou e leva a
 * "Ver planos". Fica no topo da Início, sem bloquear nada embaixo dele.
 */
@Composable
internal fun HomeTrialEndedNotice(modifier: Modifier = Modifier) {
    val openPlans = LocalOpenSubscriptionPlans.current
    SaqzCard(modifier = modifier.testTag(HomeTrialEndedTag)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = stringResource(Res.string.subscription_required_title_trial_ended),
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.subscription_required_body),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzButton(
                label = stringResource(Res.string.subscription_required_view_plans),
                onClick = openPlans,
                size = SaqzButtonSize.Sm,
                modifier = Modifier.testTag(HomeTrialEndedPlansTag),
            )
        }
    }
}
