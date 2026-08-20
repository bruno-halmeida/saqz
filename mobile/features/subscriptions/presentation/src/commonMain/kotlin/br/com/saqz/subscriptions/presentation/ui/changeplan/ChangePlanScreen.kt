package br.com.saqz.subscriptions.presentation.ui.changeplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanCardUi
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanIntent
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanPhase
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanPixUi
import br.com.saqz.subscriptions.presentation.changeplan.ChangePlanState
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_back_catalog
import br.com.saqz.subscriptions.resources.changeplan_confirm
import br.com.saqz.subscriptions.resources.changeplan_confirm_title
import br.com.saqz.subscriptions.resources.changeplan_cta
import br.com.saqz.subscriptions.resources.changeplan_current
import br.com.saqz.subscriptions.resources.changeplan_keep
import br.com.saqz.subscriptions.resources.changeplan_pix_copy
import br.com.saqz.subscriptions.resources.changeplan_pix_invoice
import br.com.saqz.subscriptions.resources.changeplan_pix_paid
import br.com.saqz.subscriptions.resources.changeplan_pix_title
import br.com.saqz.subscriptions.resources.changeplan_retry
import br.com.saqz.subscriptions.resources.changeplan_sub
import br.com.saqz.subscriptions.resources.changeplan_title
import br.com.saqz.subscriptions.resources.changeplan_upgraded_sub
import br.com.saqz.subscriptions.resources.changeplan_upgraded_title
import org.jetbrains.compose.resources.stringResource

internal object ChangePlanTags {
    const val Screen = "changeplan"
    const val PlanPrefix = "changeplan-plan-"
    const val ConfirmSheet = "changeplan-confirm"
}

@Composable
fun ChangePlanScreen(
    state: ChangePlanState,
    onBack: () -> Unit,
    onIntent: (ChangePlanIntent) -> Unit,
    onCopyPix: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().testTag(ChangePlanTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.changeplan_title), onBack = onBack)
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
            state.loadError != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzEmptyState(
                    title = state.loadError.asString(),
                    icon = SaqzIcons.CircleAlert,
                    action = stringResource(Res.string.changeplan_retry),
                    onAction = { onIntent(ChangePlanIntent.Retry) },
                )
            }
            state.phase == ChangePlanPhase.Pix && state.pix != null -> ChangePlanPixBody(
                pix = state.pix,
                isSubmitting = state.isSubmitting,
                error = state.submitError,
                onIntent = onIntent,
                onCopyPix = onCopyPix,
                onOpenInvoice = onOpenInvoice,
            )
            state.phase == ChangePlanPhase.Scheduled && state.scheduled != null -> ChangePlanResultBody(
                title = state.scheduled.title.asString(),
                subtitle = state.scheduled.subtitle.asString(),
                onBackToCatalog = { onIntent(ChangePlanIntent.BackToCatalog) },
            )
            state.phase == ChangePlanPhase.Upgraded -> ChangePlanResultBody(
                title = stringResource(Res.string.changeplan_upgraded_title),
                subtitle = stringResource(Res.string.changeplan_upgraded_sub),
                onBackToCatalog = { onIntent(ChangePlanIntent.BackToCatalog) },
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                Text(
                    text = stringResource(Res.string.changeplan_sub),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                state.pendingNote?.let { note ->
                    Text(
                        text = note.asString(),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.primary,
                    )
                }
                state.plans.forEach { card ->
                    ChangePlanCard(
                        card = card,
                        enabled = !state.isSubmitting,
                        onSelect = { onIntent(ChangePlanIntent.SelectPlan(card.plan)) },
                    )
                }
                state.submitError?.let { error ->
                    Text(
                        text = error.asString(),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.errorForeground,
                    )
                }
            }
        }
    }
    val confirm = state.confirmTarget
    SaqzBottomSheet(
        open = confirm != null,
        onClose = { onIntent(ChangePlanIntent.DismissConfirm) },
        modifier = Modifier.testTag(ChangePlanTags.ConfirmSheet),
        title = confirm?.let { stringResource(Res.string.changeplan_confirm_title, it.name) }.orEmpty(),
    ) {
        SaqzButton(
            label = stringResource(Res.string.changeplan_confirm),
            onClick = { onIntent(ChangePlanIntent.ConfirmChange) },
            loading = state.isSubmitting,
        )
        SaqzButton(
            label = stringResource(Res.string.changeplan_keep),
            onClick = { onIntent(ChangePlanIntent.DismissConfirm) },
            variant = SaqzButtonVariant.Secondary,
        )
        state.submitError?.let { error ->
            Text(
                text = error.asString(),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
            )
        }
    }
}

@Composable
private fun ChangePlanCard(
    card: ChangePlanCardUi,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = SaqzTheme.colors
    SaqzCard(
        modifier = Modifier.testTag(ChangePlanTags.PlanPrefix + card.plan.name),
        tone = if (card.isCurrent) SaqzCardTone.Soft else SaqzCardTone.Default,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = card.name, style = SaqzTheme.typography.title, color = colors.textPrimary)
            if (card.isCurrent) {
                SaqzStatusChip(
                    text = stringResource(Res.string.changeplan_current),
                    tone = SaqzChipTone.Success,
                    dot = true,
                )
            } else {
                card.scheduledLabel?.asString()?.let { label ->
                    SaqzStatusChip(
                        text = label,
                        tone = SaqzChipTone.Accent,
                        dot = true,
                    )
                }
            }
        }
        Text(
            text = card.priceLabel.asString(),
            style = SaqzTheme.typography.subtitle,
            color = colors.primary,
        )
        card.benefits.forEach { benefit ->
            Text(
                text = benefit.asString(),
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
        }
        if (!card.isCurrent && !card.isScheduled) {
            SaqzButton(
                label = stringResource(Res.string.changeplan_cta),
                onClick = onSelect,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun ChangePlanPixBody(
    pix: ChangePlanPixUi,
    isSubmitting: Boolean,
    error: UiText?,
    onIntent: (ChangePlanIntent) -> Unit,
    onCopyPix: (String) -> Unit,
    onOpenInvoice: (String) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Text(text = stringResource(Res.string.changeplan_pix_title), style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(text = pix.summary.asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        SaqzCard {
            Text(text = pix.copyPaste, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
        }
        SaqzButton(
            label = stringResource(Res.string.changeplan_pix_copy),
            onClick = { onCopyPix(pix.copyPaste) },
            enabled = pix.copyPaste.isNotBlank(),
        )
        pix.invoiceUrl?.let { url ->
            SaqzButton(
                label = stringResource(Res.string.changeplan_pix_invoice),
                onClick = { onOpenInvoice(url) },
                variant = SaqzButtonVariant.Secondary,
            )
        }
        SaqzButton(
            label = stringResource(Res.string.changeplan_pix_paid),
            onClick = { onIntent(ChangePlanIntent.PixPaid) },
            loading = isSubmitting,
        )
        error?.let {
            Text(text = it.asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
        }
    }
}

@Composable
private fun ChangePlanResultBody(title: String, subtitle: String, onBackToCatalog: () -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        Text(text = title, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(text = subtitle, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
        SaqzButton(
            label = stringResource(Res.string.changeplan_back_catalog),
            onClick = onBackToCatalog,
            variant = SaqzButtonVariant.Secondary,
        )
    }
}

internal object ChangePlanPreviewData {
    val catalog = ChangePlanState(
        isLoading = false,
        currentPlan = Plan.Organizador,
        plans = listOf(
            ChangePlanCardUi(
                plan = Plan.Titular,
                name = "Titular",
                priceLabel = UiText.Raw("R$ 39,90/mês"),
                benefits = listOf(UiText.Raw("1 grupo"), UiText.Raw("Até 25 atletas por grupo")),
                isCurrent = false,
            ),
            ChangePlanCardUi(
                plan = Plan.Organizador,
                name = "Organizador",
                priceLabel = UiText.Raw("R$ 59,90/mês"),
                benefits = listOf(UiText.Raw("3 grupos"), UiText.Raw("Atletas ilimitados")),
                isCurrent = true,
            ),
            ChangePlanCardUi(
                plan = Plan.Ilimitado,
                name = "Ilimitado",
                priceLabel = UiText.Raw("R$ 89,90/mês"),
                benefits = listOf(UiText.Raw("Grupos ilimitados"), UiText.Raw("Atletas ilimitados")),
                isCurrent = false,
            ),
        ),
    )
}

@Preview
@Composable
private fun ChangePlanScreenCatalogPreview() = SaqzTheme {
    ChangePlanScreen(state = ChangePlanPreviewData.catalog, onBack = {}, onIntent = {}, onCopyPix = {}, onOpenInvoice = {})
}

@Preview
@Composable
private fun ChangePlanScreenCurrentPreview() = SaqzTheme {
    ChangePlanScreen(
        state = ChangePlanPreviewData.catalog.copy(confirmTarget = ChangePlanPreviewData.catalog.plans.last()),
        onBack = {},
        onIntent = {},
        onCopyPix = {},
        onOpenInvoice = {},
    )
}
