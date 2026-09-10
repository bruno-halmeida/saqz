package br.com.saqz.groups.presentation.monthlygeneration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_retry
import br.com.saqz.groups.resources.group_cashbox_load_failure_body
import br.com.saqz.groups.resources.monthly_generation_title
import br.com.saqz.groups.resources.monthly_generation_note
import br.com.saqz.groups.resources.monthly_generation_month
import br.com.saqz.groups.resources.monthly_generation_due
import br.com.saqz.groups.resources.monthly_generation_amount
import br.com.saqz.groups.resources.monthly_generation_members
import br.com.saqz.groups.resources.monthly_generation_empty
import br.com.saqz.groups.resources.monthly_generation_review
import br.com.saqz.groups.resources.monthly_generation_summary
import br.com.saqz.groups.resources.monthly_generation_confirm
import br.com.saqz.groups.resources.monthly_generation_edit
import br.com.saqz.groups.resources.monthly_generation_failed
import br.com.saqz.groups.resources.monthly_generation_validation
import br.com.saqz.groups.resources.monthly_generation_denied
import org.jetbrains.compose.resources.stringResource

internal object MonthlyGenerationTags {
    const val Screen = "monthly-generation"
    const val Month = "monthly-generation-month"
    const val Due = "monthly-generation-due"
    const val Amount = "monthly-generation-amount"
    const val Review = "monthly-generation-review"
    const val Confirm = "monthly-generation-confirm"
    const val Edit = "monthly-generation-edit"
    const val Retry = "monthly-generation-retry"
    const val Summary = "monthly-generation-summary"
    fun member(id: String) = "monthly-generation-member-$id"
}

@Composable
internal fun MonthlyGenerationScreen(
    state: MonthlyGenerationState,
    onBack: () -> Unit,
    onIntent: (MonthlyGenerationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().background(SaqzTheme.colors.background).imePadding().testTag(MonthlyGenerationTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.monthly_generation_title), onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            when {
                state.isLoading -> SaqzSpinner()
                state.loadFailed -> {
                    Text(stringResource(Res.string.group_cashbox_load_failure_body), color = SaqzTheme.colors.textPrimary)
                    SaqzButton(
                        label = stringResource(Res.string.group_cashbox_retry),
                        onClick = { onIntent(MonthlyGenerationIntent.Retry) },
                        modifier = Modifier.testTag(MonthlyGenerationTags.Retry),
                    )
                }
                else -> {
                    Text(
                        stringResource(Res.string.monthly_generation_note),
                        style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
                    )
                    if (state.reviewing) MonthlyReview(state, onIntent) else MonthlyFormFields(state, onIntent)
                }
            }
            state.error?.let { error ->
                Text(
                    stringResource(when (error) {
                        GroupUiError.Validation -> Res.string.monthly_generation_validation
                        GroupUiError.AccessDenied, GroupUiError.NotFound -> Res.string.monthly_generation_denied
                        else -> Res.string.monthly_generation_failed
                    }),
                    color = SaqzTheme.colors.errorForeground, style = SaqzTheme.typography.support,
                )
            }
        }
    }
}

@Composable
private fun MonthlyFormFields(state: MonthlyGenerationState, onIntent: (MonthlyGenerationIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        SaqzInput(
            value = state.form.month, onValueChange = { onIntent(MonthlyGenerationIntent.MonthChanged(it)) },
            label = stringResource(Res.string.monthly_generation_month),
            modifier = Modifier.testTag(MonthlyGenerationTags.Month),
        )
        SaqzInput(
            value = state.form.dueDate, onValueChange = { onIntent(MonthlyGenerationIntent.DueDateChanged(it)) },
            label = stringResource(Res.string.monthly_generation_due),
            modifier = Modifier.testTag(MonthlyGenerationTags.Due),
        )
        SaqzInput(
            value = state.form.amount, onValueChange = { onIntent(MonthlyGenerationIntent.AmountChanged(it)) },
            label = stringResource(Res.string.monthly_generation_amount), keyboardType = KeyboardType.Decimal,
            modifier = Modifier.testTag(MonthlyGenerationTags.Amount),
        )
        Text(stringResource(Res.string.monthly_generation_members), color = SaqzTheme.colors.textPrimary)
        if (state.members.isEmpty()) {
            Text(stringResource(Res.string.monthly_generation_empty), color = SaqzTheme.colors.textSecondary)
        }
        state.members.forEach { member ->
            SaqzSwitch(
                checked = member.id in state.form.selectedIds, label = member.name,
                onCheckedChange = { onIntent(MonthlyGenerationIntent.ToggleMember(member.id)) },
                modifier = Modifier.testTag(MonthlyGenerationTags.member(member.id)),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_review),
            onClick = { onIntent(MonthlyGenerationIntent.Review) },
            enabled = state.form.selectedIds.isNotEmpty(), fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Review),
        )
    }
}

@Composable
private fun MonthlyReview(state: MonthlyGenerationState, onIntent: (MonthlyGenerationIntent) -> Unit) {
    Column(
        Modifier.testTag(MonthlyGenerationTags.Summary),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Text(
            stringResource(
                Res.string.monthly_generation_summary, state.form.month, state.form.dueDate,
                formatBrl(state.form.command("")?.amountCents ?: 0), state.form.selectedIds.size,
            ),
            style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary,
        )
        state.members.filter { it.id in state.form.selectedIds }.forEach {
            Text(it.name, color = SaqzTheme.colors.textPrimary)
        }
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_confirm),
            onClick = { onIntent(MonthlyGenerationIntent.Confirm) },
            enabled = !state.isSaving, loading = state.isSaving, fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Confirm),
        )
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_edit),
            onClick = { onIntent(MonthlyGenerationIntent.Edit) },
            variant = SaqzButtonVariant.Ghost, enabled = !state.isSaving, fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Edit),
        )
    }
}

@Preview
@Composable
private fun MonthlyGenerationPreview() = SaqzTheme {
    MonthlyGenerationScreen(
        state = MonthlyGenerationState(
            isLoading = false,
            members = listOf(MonthlyMemberUi("ana", "Ana Souza"), MonthlyMemberUi("bia", "Bia Santos")),
            form = MonthlyForm("2026-08", "80,00", "12/08/2026"),
        ),
        onBack = {}, onIntent = {},
    )
}
