package br.com.saqz.subscriptions.presentation.ui.planactive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveEffect
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveIntent
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveState
import br.com.saqz.subscriptions.presentation.planactive.PlanActiveViewModel
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_active_create_group
import br.com.saqz.subscriptions.resources.plan_active_groups_available_label
import br.com.saqz.subscriptions.resources.plan_active_next_billing_label
import br.com.saqz.subscriptions.resources.plan_active_retry
import br.com.saqz.subscriptions.resources.plan_active_subtitle
import br.com.saqz.subscriptions.resources.plan_active_title
import br.com.saqz.subscriptions.resources.plan_active_value_label
import br.com.saqz.subscriptions.resources.plan_active_view_my_plan
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

// Badge de 96dp = 4x sectionGap (24dp); segue o mesmo truque de derivar de token que o
// AvatarFactor de GroupReviewHeader usa — dp cru é proibido em mobile/features/* (AGENTS.md §5).
private const val BadgeSectionGapFactor = 4
private const val BadgeAlpha = 0.12f

internal object PlanActiveTags {
    const val Screen = "plan-active"
    const val Error = "plan-active-error"
    const val Retry = "plan-active-retry"
    const val CreateGroup = "plan-active-create-group"
    const val ViewMyPlan = "plan-active-view-my-plan"
}

/** 8d. */
@Composable
fun PlanActiveRoot(
    onCreateGroup: () -> Unit,
    onViewMyPlan: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanActiveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            PlanActiveEffect.NavigateToCreateGroup -> onCreateGroup()
            PlanActiveEffect.NavigateToMyPlan -> onViewMyPlan()
        }
    }

    PlanActiveScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
fun PlanActiveScreen(
    state: PlanActiveState,
    onIntent: (PlanActiveIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier.fillMaxSize().background(colors.background).testTag(PlanActiveTags.Screen),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> SaqzSpinner()
            state.error != null -> PlanActiveError(error = state.error, onIntent = onIntent)
            else -> PlanActiveContent(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun PlanActiveError(error: UiText, onIntent: (PlanActiveIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.padding(metrics.horizontalPadding).testTag(PlanActiveTags.Error),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            text = error.asString(),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        SaqzButton(
            label = stringResource(Res.string.plan_active_retry),
            onClick = { onIntent(PlanActiveIntent.Retry) },
            modifier = Modifier.testTag(PlanActiveTags.Retry),
        )
    }
}

@Composable
private fun PlanActiveContent(state: PlanActiveState, onIntent: (PlanActiveIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val badgeSize = metrics.sectionGap * BadgeSectionGapFactor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(metrics.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(colors.success.copy(alpha = BadgeAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(SaqzIcons.Check, tint = colors.success, size = metrics.iconButtonSize)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
            Text(
                text = stringResource(Res.string.plan_active_title, state.planName),
                style = SaqzTheme.typography.headline,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.plan_active_subtitle),
                style = SaqzTheme.typography.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        SaqzCard(padded = false, modifier = Modifier.fillMaxWidth()) {
            PlanActiveRow(stringResource(Res.string.plan_active_value_label), state.priceLabel)
            SaqzDivider()
            PlanActiveRow(stringResource(Res.string.plan_active_next_billing_label), state.nextBillingLabel)
            SaqzDivider()
            PlanActiveRow(stringResource(Res.string.plan_active_groups_available_label), state.groupsAvailableLabel)
        }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
            SaqzButton(
                label = stringResource(Res.string.plan_active_create_group),
                onClick = { onIntent(PlanActiveIntent.CreateGroup) },
                fullWidth = true,
                modifier = Modifier.testTag(PlanActiveTags.CreateGroup),
            )
            SaqzButton(
                label = stringResource(Res.string.plan_active_view_my_plan),
                onClick = { onIntent(PlanActiveIntent.ViewMyPlan) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.testTag(PlanActiveTags.ViewMyPlan),
            )
        }
    }
}

@Composable
private fun PlanActiveRow(label: String, value: String) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Bold),
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

internal val PreviewPlanActiveState = PlanActiveState(
    isLoading = false,
    planName = "Organizador",
    priceLabel = "R$ 17,91/mês",
    nextBillingLabel = "24 de agosto",
    groupsAvailableLabel = "3",
)

@Preview
@Composable
private fun PlanActiveScreenPreview() = SaqzTheme {
    PlanActiveScreen(state = PreviewPlanActiveState, onIntent = {})
}

@Preview
@Composable
private fun PlanActiveScreenLoadingPreview() = SaqzTheme {
    PlanActiveScreen(state = PlanActiveState(), onIntent = {})
}
