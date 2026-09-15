package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.core.common.formatting.formatInstantDateTimePtBr
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import br.com.saqz.subscriptions.presentation.myplan.MyPlanTrialUi
import br.com.saqz.subscriptions.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MyPlanHero(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(SaqzTheme.colors.primary, RoundedCornerShape(SaqzTheme.metrics.blockRadius))
            .padding(SaqzTheme.metrics.sectionGap),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        content = content,
    )
}

@Composable
internal fun MyPlanTrialCard(trial: MyPlanTrialUi, onSubscribe: () -> Unit, modifier: Modifier = Modifier) {
    if (trial.status == TrialStatus.Subscribed) return
    val colors = SaqzTheme.colors
    val typography = SaqzTheme.typography
    val hasTrial = trial.status != TrialStatus.Ineligible
    Column(modifier.testTag(MyPlanTags.TrialCard), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.sectionGap)) {
        MyPlanHero {
            SaqzIcon(SaqzIcons.Users, tint = colors.accent, size = SaqzTheme.metrics.iconButtonSize)
            Text(stringResource(when (trial.status) {
                TrialStatus.Expired -> Res.string.myplan_trial_ended_label
                TrialStatus.Ineligible -> Res.string.myplan_current_plan_label
                else -> Res.string.myplan_trial_eyebrow
            }), style = typography.eyebrow, color = colors.accent)
            Text(stringResource(if (hasTrial) Res.string.myplan_trial_name else Res.string.myplan_no_plan),
                style = typography.headline, color = colors.onPrimary)
            Text(stringResource(when (trial.status) {
                TrialStatus.Expired -> Res.string.myplan_trial_expired
                TrialStatus.Active -> Res.string.myplan_trial_active
                TrialStatus.Available -> Res.string.myplan_trial_available
                else -> Res.string.myplan_trial_ineligible
            }, if (trial.status == TrialStatus.Available) trial.trialDays.toString()
                else formatInstantDateTimePtBr(trial.endsAt) ?: stringResource(Res.string.myplan_trial_date_unavailable)),
                style = typography.support, color = colors.onPrimary)
        }
        if (trial.status == TrialStatus.Active || trial.status == TrialStatus.Available) {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                Text(stringResource(Res.string.myplan_included), style = typography.subtitle, color = colors.textPrimary)
                SaqzCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                        SaqzIcon(SaqzIcons.Users, tint = colors.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                            Text(stringResource(Res.string.myplan_groups_benefit), style = typography.title, color = colors.textPrimary)
                            Text(stringResource(Res.string.myplan_athletes_benefit), style = typography.support,
                    color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        SaqzCard(tone = SaqzCardTone.Soft) {
            Text(stringResource(Res.string.myplan_trial_next), style = typography.subtitle, color = colors.textPrimary)
            if (trial.status == TrialStatus.Expired) {
                Text(stringResource(Res.string.myplan_trial_readonly), style = typography.support,
                    color = colors.textSecondary)
            }
            Text(
                stringResource(if (hasTrial) Res.string.myplan_trial_no_charge else Res.string.myplan_choose_help),
                style = typography.support,
                    color = colors.textSecondary)
            if (trial.canSubscribe) {
                SaqzButton(stringResource(if (hasTrial) Res.string.myplan_trial_subscribe else Res.string.myplan_choose_plan),
                    onClick = onSubscribe, fullWidth = true, modifier = Modifier.testTag(MyPlanTags.Subscribe))
            } else if (!trial.isOwner) {
                Text(stringResource(Res.string.myplan_trial_organizer), style = typography.support,
                    color = colors.textSecondary)
            }
        }
    }
}
