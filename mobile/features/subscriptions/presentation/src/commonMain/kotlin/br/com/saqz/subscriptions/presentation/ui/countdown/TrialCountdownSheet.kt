package br.com.saqz.subscriptions.presentation.ui.countdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.trial_countdown_description
import br.com.saqz.subscriptions.resources.trial_countdown_last_day
import br.com.saqz.subscriptions.resources.trial_countdown_not_now
import br.com.saqz.subscriptions.resources.trial_countdown_title
import br.com.saqz.subscriptions.resources.trial_countdown_view_plans
import br.com.saqz.subscriptions.resources.trial_usage_athletes
import br.com.saqz.subscriptions.resources.trial_usage_cash
import br.com.saqz.subscriptions.resources.trial_usage_games
import br.com.saqz.subscriptions.resources.trial_usage_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

object TrialCountdownTags {
    const val Sheet = "trial-countdown-sheet"
    const val ViewPlans = "trial-countdown-view-plans"
    const val NotNow = "trial-countdown-not-now"
    const val Usage = "trial-countdown-usage"
}

/**
 * "Faltam N dias do seu teste" com o que o organizador já fez. "Ver planos" fecha a folha e abre a mesma tela de
 * planos do diálogo de assinatura ([onViewPlans] vem do host de navegação); "Agora não" só fecha.
 */
@Composable
fun TrialCountdownSheet(
    daysLeft: Int,
    usage: TrialUsage?,
    onDismiss: () -> Unit,
    onViewPlans: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzBottomSheet(
        open = true,
        onClose = onDismiss,
        modifier = modifier.testTag(TrialCountdownTags.Sheet),
        title = if (daysLeft <= 0) {
            stringResource(Res.string.trial_countdown_last_day)
        } else {
            pluralStringResource(Res.plurals.trial_countdown_title, daysLeft, daysLeft)
        },
        description = stringResource(Res.string.trial_countdown_description),
        splitFooter = {
            SaqzButton(
                stringResource(Res.string.trial_countdown_not_now),
                onDismiss,
                Modifier.weight(1f).testTag(TrialCountdownTags.NotNow),
                SaqzButtonVariant.Secondary,
            )
            SaqzButton(
                stringResource(Res.string.trial_countdown_view_plans),
                {
                    onDismiss()
                    onViewPlans()
                },
                Modifier.weight(1f).testTag(TrialCountdownTags.ViewPlans),
            )
        },
        content = { if (usage != null && !usage.isEmpty) TrialUsageSummary(usage) },
    )
}

/** Jogos, atletas confirmados e caixa do teste; some quando o organizador ainda não fez nada. */
@Composable
internal fun TrialUsageSummary(usage: TrialUsage, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    Column(modifier.testTag(TrialCountdownTags.Usage), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.trial_usage_title), style = typography.subtitle, color = colors.textPrimary)
        Text(pluralStringResource(Res.plurals.trial_usage_games, usage.games, usage.games),
            style = typography.support, color = colors.textSecondary)
        Text(pluralStringResource(Res.plurals.trial_usage_athletes, usage.confirmedAthletes, usage.confirmedAthletes),
            style = typography.support, color = colors.textSecondary)
        Text(stringResource(Res.string.trial_usage_cash, formatBrl(usage.cashCents)),
            style = typography.support, color = colors.textSecondary)
    }
}
