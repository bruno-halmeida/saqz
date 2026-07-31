package br.com.saqz.subscriptions.presentation.planselection.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.presentation.planselection.CouponUiState
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionEffect
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionIntent
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionState
import br.com.saqz.subscriptions.presentation.planselection.PlanSelectionViewModel
import br.com.saqz.subscriptions.presentation.planselection.PlanUi
import br.com.saqz.subscriptions.presentation.planselection.priceCentsFor
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.plan_selection_badge_free
import br.com.saqz.subscriptions.resources.plan_selection_continue
import br.com.saqz.subscriptions.resources.plan_selection_coupon_apply
import br.com.saqz.subscriptions.resources.plan_selection_coupon_applied_description
import br.com.saqz.subscriptions.resources.plan_selection_coupon_applied_title
import br.com.saqz.subscriptions.resources.plan_selection_coupon_expired
import br.com.saqz.subscriptions.resources.plan_selection_coupon_expired_note
import br.com.saqz.subscriptions.resources.plan_selection_coupon_label
import br.com.saqz.subscriptions.resources.plan_selection_coupon_not_found
import br.com.saqz.subscriptions.resources.plan_selection_coupon_placeholder
import br.com.saqz.subscriptions.resources.plan_selection_coupon_remove
import br.com.saqz.subscriptions.resources.plan_selection_coupon_try_another
import br.com.saqz.subscriptions.resources.plan_selection_cycle_annual
import br.com.saqz.subscriptions.resources.plan_selection_cycle_monthly
import br.com.saqz.subscriptions.resources.plan_selection_price_annual_suffix
import br.com.saqz.subscriptions.resources.plan_selection_price_free
import br.com.saqz.subscriptions.resources.plan_selection_price_monthly_suffix
import br.com.saqz.subscriptions.resources.plan_selection_retry
import br.com.saqz.subscriptions.resources.plan_selection_subtitle
import br.com.saqz.subscriptions.resources.plan_selection_title
import br.com.saqz.subscriptions.resources.plan_selection_total_annual
import br.com.saqz.subscriptions.resources.plan_selection_total_monthly
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

internal object PlanSelectionTags {
    const val CycleToggle = "plan-selection-cycle"
    const val CouponInput = "plan-selection-coupon-input"
    const val CouponApply = "plan-selection-coupon-apply"
    const val CouponRemove = "plan-selection-coupon-remove"
    const val CouponTryAnother = "plan-selection-coupon-try-another"
    const val Retry = "plan-selection-retry"
    const val Continue = "plan-selection-continue"

    fun planCard(id: Plan): String = "plan-selection-card-${id.name}"
}

// ponytail: sem token em SaqzMetrics pra nenhum destes — nenhum componente do design
// system pediu essas medidas ainda. Se um segundo lugar pedir o mesmo número, o token
// nasce lá (mesmo critério do SaqzStepperButton/SaqzChoiceChip em SaqzControls.kt).
private val PlanCardBorderWidth = 1.dp
private val PlanCardSelectedBorderWidth = 2.dp
private val PlanFeatureIconSize = 16.dp
private val PlanRadioDotSize = 20.dp
private val PlanRadioDotBorderWidth = 2.dp
private val PlanRadioDotFillSize = 10.dp
private val CouponAppliedIconSize = 14.dp
private val CouponAppliedTextGap = 2.dp
private val CouponRemoveIconSize = 18.dp
private val CouponExpiredIconSize = 18.dp

/**
 * 8a/8b — a tela não navega: o único efeito sai por [onContinue], e quem decide a rota
 * (`SubscriptionsRoute.Payment`) é o `SaqzNavHost` (VUL-113, fora do escopo daqui).
 */
@Composable
fun PlanSelectionRoot(
    onBack: () -> Unit,
    onContinue: (planId: Plan, cycle: SubscriptionCycle, couponCode: String?) -> Unit,
    viewModel: PlanSelectionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is PlanSelectionEffect.NavigateToPayment -> onContinue(effect.planId, effect.cycle, effect.couponCode)
        }
    }
    PlanSelectionScreen(state = state, onIntent = viewModel::onIntent, onBack = onBack)
}

@Composable
fun PlanSelectionScreen(
    state: PlanSelectionState,
    onIntent: (PlanSelectionIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzTopAppBar(title = stringResource(Res.string.plan_selection_title), onBack = onBack)
        when {
            state.isLoading -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }

            state.loadError != null -> SaqzEmptyState(
                title = state.loadError.asString(),
                action = stringResource(Res.string.plan_selection_retry),
                onAction = { onIntent(PlanSelectionIntent.Retry) },
                modifier = Modifier.weight(1f).testTag(PlanSelectionTags.Retry),
            )

            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    Text(
                        text = stringResource(Res.string.plan_selection_subtitle),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                    CycleToggle(cycle = state.cycle, onIntent = onIntent)
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                        state.plans.forEach { plan ->
                            PlanCard(
                                plan = plan,
                                cycle = state.cycle,
                                selected = plan.id == state.selectedPlanId,
                                onSelect = { onIntent(PlanSelectionIntent.SelectPlan(plan.id)) },
                                modifier = Modifier.testTag(PlanSelectionTags.planCard(plan.id)),
                            )
                        }
                    }
                    CouponSection(state = state, onIntent = onIntent)
                }
                PlanSelectionFooter(state = state, onIntent = onIntent)
            }
        }
    }
}

@Composable
private fun CycleToggle(cycle: SubscriptionCycle, onIntent: (PlanSelectionIntent) -> Unit) {
    SaqzSegmented(
        options = listOf(
            stringResource(Res.string.plan_selection_cycle_monthly),
            stringResource(Res.string.plan_selection_cycle_annual),
        ),
        selected = if (cycle == SubscriptionCycle.Monthly) 0 else 1,
        onSelect = { index ->
            onIntent(PlanSelectionIntent.SelectCycle(if (index == 0) SubscriptionCycle.Monthly else SubscriptionCycle.Annual))
        },
        modifier = Modifier.testTag(PlanSelectionTags.CycleToggle),
    )
}

@Composable
private fun PlanCard(
    plan: PlanUi,
    cycle: SubscriptionCycle,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .border(
                width = if (selected) PlanCardSelectedBorderWidth else PlanCardBorderWidth,
                color = if (selected) colors.primary else colors.border,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
            PlanRadioDot(selected = selected)
            Text(
                text = plan.name,
                style = SaqzTheme.typography.label,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            plan.badge?.let { badge ->
                SaqzStatusChip(
                    text = badge.asString(),
                    tone = if (plan.isHighlighted) SaqzChipTone.Accent else SaqzChipTone.Neutral,
                )
            }
        }
        PlanPrice(plan = plan, cycle = cycle)
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            plan.features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    SaqzIcon(SaqzIcons.Check, tint = colors.primary, size = PlanFeatureIconSize)
                    Text(text = feature.asString(), style = SaqzTheme.typography.support, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun PlanRadioDot(selected: Boolean) {
    val colors = SaqzTheme.colors
    Box(
        modifier = Modifier
            .size(PlanRadioDotSize)
            .clip(CircleShape)
            .border(PlanRadioDotBorderWidth, if (selected) colors.primary else colors.border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(Modifier.size(PlanRadioDotFillSize).clip(CircleShape).background(colors.primary, CircleShape))
        }
    }
}

@Composable
private fun PlanPrice(plan: PlanUi, cycle: SubscriptionCycle) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    if (plan.isFree) {
        Text(
            text = stringResource(Res.string.plan_selection_price_free),
            style = SaqzTheme.typography.title,
            color = colors.textPrimary,
        )
    } else {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(text = formatBrl(plan.priceCentsFor(cycle)), style = SaqzTheme.typography.title, color = colors.textPrimary)
            Text(
                text = stringResource(
                    if (cycle == SubscriptionCycle.Monthly) {
                        Res.string.plan_selection_price_monthly_suffix
                    } else {
                        Res.string.plan_selection_price_annual_suffix
                    },
                ),
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CouponSection(state: PlanSelectionState, onIntent: (PlanSelectionIntent) -> Unit) {
    when (val coupon = state.coupon) {
        is CouponUiState.Applied -> AppliedCouponCard(
            coupon = coupon,
            onRemove = { onIntent(PlanSelectionIntent.RemoveCoupon) },
        )

        is CouponUiState.Expired -> ExpiredCouponBanner(
            code = coupon.code,
            onTryAnother = { onIntent(PlanSelectionIntent.RemoveCoupon) },
        )

        else -> CouponInput(state = state, onIntent = onIntent)
    }
}

@Composable
private fun CouponInput(state: PlanSelectionState, onIntent: (PlanSelectionIntent) -> Unit) {
    val coupon = state.coupon
    val errorText = when (coupon) {
        CouponUiState.NotFound -> stringResource(Res.string.plan_selection_coupon_not_found)
        is CouponUiState.Error -> coupon.message.asString()
        else -> null
    }
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzInput(
            value = state.couponCode,
            onValueChange = { onIntent(PlanSelectionIntent.UpdateCouponCode(it)) },
            label = stringResource(Res.string.plan_selection_coupon_label),
            placeholder = stringResource(Res.string.plan_selection_coupon_placeholder),
            errorText = errorText,
            enabled = !state.isValidatingCoupon,
            modifier = Modifier.fillMaxWidth().testTag(PlanSelectionTags.CouponInput),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SaqzButton(
                label = stringResource(Res.string.plan_selection_coupon_apply),
                onClick = { onIntent(PlanSelectionIntent.ApplyCoupon) },
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                enabled = state.couponCode.isNotBlank() && !state.isValidatingCoupon,
                loading = state.isValidatingCoupon,
                modifier = Modifier.testTag(PlanSelectionTags.CouponApply),
            )
        }
    }
}

@Composable
private fun AppliedCouponCard(coupon: CouponUiState.Applied, onRemove: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.cardRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.success.copy(alpha = 0.12f), shape)
            .padding(metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Box(
            // ponytail: 24dp bate com switchThumbSize por coincidência de número, não de
            // significado — mesmo critério do SaqzChoiceChip reusando iconButtonSize.
            modifier = Modifier.size(metrics.switchThumbSize).clip(CircleShape).background(colors.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(SaqzIcons.Check, tint = colors.onPrimary, size = CouponAppliedIconSize)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CouponAppliedTextGap)) {
            Text(
                text = stringResource(Res.string.plan_selection_coupon_applied_title, coupon.code),
                style = SaqzTheme.typography.label,
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.plan_selection_coupon_applied_description, coupon.discountPercent),
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.textSecondary, textDecoration = TextDecoration.LineThrough)) {
                        append(formatBrl(coupon.listPriceCents))
                    }
                    append("  ")
                    withStyle(SpanStyle(color = colors.success, fontWeight = FontWeight.Bold)) {
                        append(formatBrl(coupon.finalPriceCents))
                    }
                },
                style = SaqzTheme.typography.support,
            )
        }
        SaqzIconButton(
            onClick = onRemove,
            contentDescription = stringResource(Res.string.plan_selection_coupon_remove),
            modifier = Modifier.testTag(PlanSelectionTags.CouponRemove),
        ) {
            SaqzIcon(SaqzIcons.Close, tint = colors.textSecondary, size = CouponRemoveIconSize)
        }
    }
}

@Composable
private fun ExpiredCouponBanner(code: String, onTryAnother: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.cardRadius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.warning.copy(alpha = 0.14f), shape)
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
            SaqzIcon(SaqzIcons.Clock, tint = colors.warningForeground, size = CouponExpiredIconSize)
            Text(
                text = stringResource(Res.string.plan_selection_coupon_expired, code),
                style = SaqzTheme.typography.label,
                color = colors.warningForeground,
            )
        }
        Text(
            text = stringResource(Res.string.plan_selection_coupon_expired_note),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        SaqzButton(
            label = stringResource(Res.string.plan_selection_coupon_try_another),
            onClick = onTryAnother,
            variant = SaqzButtonVariant.Ghost,
            size = SaqzButtonSize.Sm,
            modifier = Modifier.testTag(PlanSelectionTags.CouponTryAnother),
        )
    }
}

@Composable
private fun PlanSelectionFooter(state: PlanSelectionState, onIntent: (PlanSelectionIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    Column {
        SaqzDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(
                        if (state.cycle == SubscriptionCycle.Monthly) {
                            Res.string.plan_selection_total_monthly
                        } else {
                            Res.string.plan_selection_total_annual
                        },
                    ),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
                Text(
                    text = state.totalCents?.let(::formatBrl).orEmpty(),
                    style = SaqzTheme.typography.title,
                    color = colors.textPrimary,
                )
            }
            SaqzButton(
                label = stringResource(Res.string.plan_selection_continue),
                onClick = { onIntent(PlanSelectionIntent.Confirm) },
                fullWidth = true,
                // Cupom no ar ainda não decidiu o preço: confirmar antes dele responder
                // navegaria sem o desconto, calado — a pessoa nem saberia que perdeu.
                enabled = state.selectedPlan != null && !state.isValidatingCoupon,
                modifier = Modifier.testTag(PlanSelectionTags.Continue),
            )
        }
    }
}

@Preview
@Composable
private fun PlanSelectionScreenPreview() = SaqzTheme {
    PlanSelectionScreen(state = PlanSelectionPreviewData.default, onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun PlanSelectionScreenCouponAppliedPreview() = SaqzTheme {
    PlanSelectionScreen(state = PlanSelectionPreviewData.couponApplied, onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun PlanSelectionScreenCouponNotFoundPreview() = SaqzTheme {
    PlanSelectionScreen(state = PlanSelectionPreviewData.couponNotFound, onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun PlanSelectionScreenCouponExpiredPreview() = SaqzTheme {
    PlanSelectionScreen(state = PlanSelectionPreviewData.couponExpired, onIntent = {}, onBack = {})
}

private object PlanSelectionPreviewData {
    private val plans = listOf(
        PlanUi(
            id = Plan.Titular,
            name = "Titular",
            monthlyPriceCents = 0,
            annualPriceCents = 0,
            isFree = true,
            isHighlighted = false,
            badge = UiText.Res(Res.string.plan_selection_badge_free),
            features = listOf(
                UiText.Raw("1 grupo com até 20 membros"),
                UiText.Raw("Presença Vou / Talvez / Não vou"),
            ),
        ),
        PlanUi(
            id = Plan.Organizador,
            name = "Organizador",
            monthlyPriceCents = 1990,
            annualPriceCents = 19900,
            isFree = false,
            isHighlighted = true,
            badge = UiText.Raw("Mais escolhido"),
            features = listOf(
                UiText.Raw("Até 3 grupos, membros sem limite"),
                UiText.Raw("Caixa do grupo e mensalidades"),
                UiText.Raw("Cobrança e acerto pós-jogo"),
            ),
        ),
        PlanUi(
            id = Plan.Ilimitado,
            name = "Ilimitado",
            monthlyPriceCents = 3990,
            annualPriceCents = 39900,
            isFree = false,
            isHighlighted = false,
            badge = null,
            features = listOf(
                UiText.Raw("Grupos sem limite"),
                UiText.Raw("Relatórios do grupo"),
                UiText.Raw("Suporte prioritário via WhatsApp"),
            ),
        ),
    )

    val default = PlanSelectionState(plans = plans, selectedPlanId = Plan.Organizador, isLoading = false)

    val couponApplied = default.copy(
        couponCode = "GALERA10",
        coupon = CouponUiState.Applied(
            code = "GALERA10",
            discountPercent = 10,
            listPriceCents = 1990,
            finalPriceCents = 1791,
        ),
    )

    val couponNotFound = default.copy(couponCode = "VOLEI99", coupon = CouponUiState.NotFound)

    val couponExpired = default.copy(couponCode = "SAQUE20", coupon = CouponUiState.Expired(code = "SAQUE20"))
}
