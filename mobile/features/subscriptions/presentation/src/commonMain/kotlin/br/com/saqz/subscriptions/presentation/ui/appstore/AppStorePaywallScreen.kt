package br.com.saqz.subscriptions.presentation.ui.appstore

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzEmptyState
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
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallIntent
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallNotice
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallOfferUi
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPhase
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallPlanUi
import br.com.saqz.subscriptions.presentation.appstore.AppStorePaywallState
import br.com.saqz.subscriptions.presentation.appstore.PaywallCouponMessage
import br.com.saqz.subscriptions.presentation.appstore.PaywallCouponSheet
import br.com.saqz.subscriptions.presentation.appstore.PaywallCouponUi
import br.com.saqz.subscriptions.presentation.limit.PaywallGroupLimit
import br.com.saqz.subscriptions.presentation.store.PaywallStoreKind
import br.com.saqz.subscriptions.presentation.ui.limit.PaywallGroupLimitNotice
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.paywall_confirm_retry
import br.com.saqz.subscriptions.resources.paywall_coupon_apply
import br.com.saqz.subscriptions.resources.paywall_coupon_change
import br.com.saqz.subscriptions.resources.paywall_coupon_cta
import br.com.saqz.subscriptions.resources.paywall_coupon_exhausted
import br.com.saqz.subscriptions.resources.paywall_coupon_expired
import br.com.saqz.subscriptions.resources.paywall_coupon_failed
import br.com.saqz.subscriptions.resources.paywall_coupon_field
import br.com.saqz.subscriptions.resources.paywall_coupon_invalid_cycle
import br.com.saqz.subscriptions.resources.paywall_coupon_invalid_plan
import br.com.saqz.subscriptions.resources.paywall_coupon_not_eligible
import br.com.saqz.subscriptions.resources.paywall_coupon_not_found
import br.com.saqz.subscriptions.resources.paywall_coupon_off
import br.com.saqz.subscriptions.resources.paywall_coupon_off_months
import br.com.saqz.subscriptions.resources.paywall_coupon_off_one_month
import br.com.saqz.subscriptions.resources.paywall_coupon_off_one_year
import br.com.saqz.subscriptions.resources.paywall_coupon_off_years
import br.com.saqz.subscriptions.resources.paywall_coupon_only_web
import br.com.saqz.subscriptions.resources.paywall_coupon_open
import br.com.saqz.subscriptions.resources.paywall_coupon_pending
import br.com.saqz.subscriptions.resources.paywall_coupon_then
import br.com.saqz.subscriptions.resources.paywall_coupon_title
import br.com.saqz.subscriptions.resources.paywall_notice_coupon_redeem
import br.com.saqz.subscriptions.resources.paywall_cta
import br.com.saqz.subscriptions.resources.paywall_current
import br.com.saqz.subscriptions.resources.paywall_cycle_annual
import br.com.saqz.subscriptions.resources.paywall_cycle_monthly
import br.com.saqz.subscriptions.resources.paywall_disclosure
import br.com.saqz.subscriptions.resources.paywall_disclosure_play
import br.com.saqz.subscriptions.resources.paywall_duration_annual
import br.com.saqz.subscriptions.resources.paywall_duration_monthly
import br.com.saqz.subscriptions.resources.paywall_load_error
import br.com.saqz.subscriptions.resources.paywall_plan_too_small
import br.com.saqz.subscriptions.resources.paywall_notice_confirmation
import br.com.saqz.subscriptions.resources.paywall_notice_confirmation_play
import br.com.saqz.subscriptions.resources.paywall_notice_failed
import br.com.saqz.subscriptions.resources.paywall_notice_invalid
import br.com.saqz.subscriptions.resources.paywall_notice_nothing
import br.com.saqz.subscriptions.resources.paywall_notice_nothing_play
import br.com.saqz.subscriptions.resources.paywall_notice_owned
import br.com.saqz.subscriptions.resources.paywall_notice_owned_play
import br.com.saqz.subscriptions.resources.paywall_notice_pending
import br.com.saqz.subscriptions.resources.paywall_notice_restore_failed
import br.com.saqz.subscriptions.resources.paywall_price_month
import br.com.saqz.subscriptions.resources.paywall_price_year
import br.com.saqz.subscriptions.resources.paywall_privacy
import br.com.saqz.subscriptions.resources.paywall_restore
import br.com.saqz.subscriptions.resources.paywall_retry
import br.com.saqz.subscriptions.resources.paywall_sub
import br.com.saqz.subscriptions.resources.paywall_sub_play
import br.com.saqz.subscriptions.resources.paywall_subscribed
import br.com.saqz.subscriptions.resources.paywall_terms
import br.com.saqz.subscriptions.resources.paywall_title
import org.jetbrains.compose.resources.stringResource

object AppStorePaywallTags {
    const val Screen = "paywall"
    const val Cycle = "paywall-cycle"
    const val PlanPrefix = "paywall-plan-"
    const val PurchasePrefix = "paywall-purchase-"
    const val TooSmallPrefix = "paywall-too-small-"
    const val Notice = "paywall-notice"
    const val ConfirmRetry = "paywall-confirm-retry"
    const val Restore = "paywall-restore"
    const val Terms = "paywall-terms"
    const val Privacy = "paywall-privacy"
    const val Disclosure = "paywall-disclosure"
    const val CouponOpen = "paywall-coupon-open"
    const val CouponSheet = "paywall-coupon-sheet"
    const val CouponField = "paywall-coupon-field"
    const val CouponPlan = "paywall-coupon-plan"
    const val CouponApply = "paywall-coupon-apply"
    const val CouponMessage = "paywall-coupon-message"
    const val CouponDiscountPrefix = "paywall-coupon-discount-"
    const val CouponPurchase = "paywall-coupon-purchase"
}

/**
 * Guideline 3.1.2 da Apple: nome, duração e preço de cada assinatura, o aviso de renovação
 * automática, Termos, Privacidade e "Restaurar compras" na mesma tela da compra. O preço é o
 * `displayPrice` do StoreKit; no anual ele é o total cobrado, sem equivalente mensal.
 *
 * [AppStorePaywallPhase.Unavailable] não desenha nada: quem hospeda mostra o portão de sempre.
 */
@Composable
fun AppStorePaywallScreen(
    state: AppStorePaywallState,
    onIntent: (AppStorePaywallIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    groupLimit: PaywallGroupLimit? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AppStorePaywallContent(state, onIntent, onBack, onOpenTerms, onOpenPrivacy, groupLimit)
        AppStorePaywallCouponSheet(state, onIntent)
    }
}

@Composable
private fun AppStorePaywallContent(
    state: AppStorePaywallState,
    onIntent: (AppStorePaywallIntent) -> Unit,
    onBack: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
    groupLimit: PaywallGroupLimit?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(AppStorePaywallTags.Screen),
    ) {
        SaqzTopAppBar(title = stringResource(Res.string.paywall_title), onBack = onBack)
        when {
            state.isSubscribed -> AppStorePaywallCentered {
                SaqzSpinner(onDark = false)
                Text(
                    text = stringResource(Res.string.paywall_subscribed),
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
            // O limite de grupos se explica sempre, carregando ou não os planos.
            state.phase == AppStorePaywallPhase.Loading -> AppStorePaywallCentered {
                groupLimit?.let { PaywallGroupLimitNotice(it) }
                SaqzSpinner()
            }
            state.phase == AppStorePaywallPhase.LoadFailed -> AppStorePaywallCentered {
                groupLimit?.let { PaywallGroupLimitNotice(it) }
                SaqzEmptyState(
                    title = stringResource(Res.string.paywall_load_error),
                    icon = SaqzIcons.CircleAlert,
                    action = stringResource(Res.string.paywall_retry),
                    onAction = { onIntent(AppStorePaywallIntent.Retry) },
                )
            }
            state.phase == AppStorePaywallPhase.Ready -> AppStorePaywallCatalog(
                state = state,
                groupLimit = groupLimit,
                onIntent = onIntent,
                onOpenTerms = onOpenTerms,
                onOpenPrivacy = onOpenPrivacy,
            )
            else -> Unit
        }
    }
}

@Composable
private fun AppStorePaywallCentered(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun AppStorePaywallCatalog(
    state: AppStorePaywallState,
    groupLimit: PaywallGroupLimit?,
    onIntent: (AppStorePaywallIntent) -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val cycles = listOf(SubscriptionCycle.Monthly, SubscriptionCycle.Annual)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
    ) {
        groupLimit?.let { PaywallGroupLimitNotice(it) }
        Text(
            text = stringResource(if (state.store == PaywallStoreKind.GooglePlay) Res.string.paywall_sub_play else Res.string.paywall_sub),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        SaqzSegmented(
            options = listOf(
                stringResource(Res.string.paywall_cycle_monthly),
                stringResource(Res.string.paywall_cycle_annual),
            ),
            selected = cycles.indexOf(state.cycle),
            onSelect = { index -> onIntent(AppStorePaywallIntent.SelectCycle(cycles[index])) },
            modifier = Modifier.testTag(AppStorePaywallTags.Cycle),
        )
        state.notice?.let { notice -> AppStorePaywallNoticeCard(notice, state.store, state.isConfirming, onIntent) }
        state.plans.forEach { plan ->
            val offer = plan.offer(state.cycle) ?: return@forEach
            val coupon = state.couponFor(plan.plan)
            AppStorePaywallPlanCard(
                plan = plan,
                offer = offer,
                cycle = state.cycle,
                purchasing = state.purchasingProductId == (coupon?.purchaseOfferId ?: offer.productId),
                enabled = !state.isBusy,
                tooSmallFor = groupLimit?.takeUnless { it.fits(plan.maxGroups) },
                coupon = coupon,
                onPurchase = {
                    onIntent(if (coupon != null) AppStorePaywallIntent.PurchaseWithCoupon else AppStorePaywallIntent.Purchase(offer.productId))
                },
            )
        }
        SaqzButton(
            label = state.coupon?.let { stringResource(Res.string.paywall_coupon_change, it.code) }
                ?: stringResource(Res.string.paywall_coupon_open),
            onClick = { onIntent(AppStorePaywallIntent.OpenCoupon) },
            variant = SaqzButtonVariant.Ghost,
            size = SaqzButtonSize.Sm,
            enabled = !state.isBusy,
            modifier = Modifier.align(Alignment.CenterHorizontally).testTag(AppStorePaywallTags.CouponOpen),
        )
        Text(
            text = stringResource(
                if (state.store == PaywallStoreKind.GooglePlay) Res.string.paywall_disclosure_play else Res.string.paywall_disclosure,
            ),
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.testTag(AppStorePaywallTags.Disclosure),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid, Alignment.CenterHorizontally),
        ) {
            SaqzButton(
                label = stringResource(Res.string.paywall_terms),
                onClick = onOpenTerms,
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                modifier = Modifier.testTag(AppStorePaywallTags.Terms),
            )
            SaqzButton(
                label = stringResource(Res.string.paywall_privacy),
                onClick = onOpenPrivacy,
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                modifier = Modifier.testTag(AppStorePaywallTags.Privacy),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.paywall_restore),
            onClick = { onIntent(AppStorePaywallIntent.Restore) },
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            enabled = !state.isBusy,
            loading = state.isRestoring,
            modifier = Modifier.testTag(AppStorePaywallTags.Restore),
        )
    }
}

@Composable
private fun AppStorePaywallPlanCard(
    plan: AppStorePaywallPlanUi,
    offer: AppStorePaywallOfferUi,
    cycle: SubscriptionCycle,
    purchasing: Boolean,
    enabled: Boolean,
    onPurchase: () -> Unit,
    tooSmallFor: PaywallGroupLimit? = null,
    coupon: PaywallCouponUi? = null,
) {
    val colors = SaqzTheme.colors
    SaqzCard(
        modifier = Modifier.testTag(AppStorePaywallTags.PlanPrefix + plan.plan.name),
        tone = if (offer.isCurrent) SaqzCardTone.Soft else SaqzCardTone.Default,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = plan.name, style = SaqzTheme.typography.title, color = colors.textPrimary)
            if (offer.isCurrent) {
                SaqzStatusChip(text = stringResource(Res.string.paywall_current), tone = SaqzChipTone.Success, dot = true)
            }
        }
        Text(
            text = stringResource(
                if (cycle == SubscriptionCycle.Annual) Res.string.paywall_price_year else Res.string.paywall_price_month,
                coupon?.discountedPrice ?: offer.displayPrice,
            ),
            style = SaqzTheme.typography.headline,
            color = colors.primary,
        )
        if (coupon != null) {
            val off = coupon.discountLabel()
            Text(
                text = if (coupon.discountedPrice != null) stringResource(Res.string.paywall_coupon_then, off, offer.displayPrice) else off,
                style = SaqzTheme.typography.support,
                color = colors.successForeground,
                maxLines = 1,
                modifier = Modifier.testTag(AppStorePaywallTags.CouponDiscountPrefix + plan.plan.name),
            )
        }
        Text(
            text = stringResource(
                if (cycle == SubscriptionCycle.Annual) Res.string.paywall_duration_annual else Res.string.paywall_duration_monthly,
            ),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        plan.benefits.forEach { benefit ->
            Text(text = "• ${benefit.asString()}", style = SaqzTheme.typography.support, color = colors.textPrimary)
        }
        if (tooSmallFor != null) {
            Text(
                text = stringResource(Res.string.paywall_plan_too_small, tooSmallFor.groupsUsed),
                style = SaqzTheme.typography.support,
                color = colors.errorForeground,
                modifier = Modifier.testTag(AppStorePaywallTags.TooSmallPrefix + plan.plan.name),
            )
        }
        if (!offer.isCurrent) {
            SaqzButton(
                label = if (coupon != null) stringResource(Res.string.paywall_coupon_cta) else stringResource(Res.string.paywall_cta, plan.name),
                onClick = onPurchase,
                fullWidth = true,
                enabled = enabled && tooSmallFor == null,
                loading = purchasing,
                modifier = Modifier.testTag(
                    if (coupon != null) AppStorePaywallTags.CouponPurchase else AppStorePaywallTags.PurchasePrefix + offer.productId,
                ),
            )
        }
    }
}

@Composable
private fun AppStorePaywallNoticeCard(
    notice: AppStorePaywallNotice,
    store: PaywallStoreKind,
    isConfirming: Boolean,
    onIntent: (AppStorePaywallIntent) -> Unit,
) {
    val isError = notice != AppStorePaywallNotice.Pending && notice != AppStorePaywallNotice.NothingToRestore
    SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(AppStorePaywallTags.Notice)) {
        Text(
            text = stringResource(notice.message(store)),
            style = SaqzTheme.typography.support,
            color = if (isError) SaqzTheme.colors.errorForeground else SaqzTheme.colors.textPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (notice == AppStorePaywallNotice.ConfirmationPending) {
            SaqzButton(
                label = stringResource(Res.string.paywall_confirm_retry),
                onClick = { onIntent(AppStorePaywallIntent.RetryConfirmation) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                loading = isConfirming,
                modifier = Modifier.testTag(AppStorePaywallTags.ConfirmRetry),
            )
        }
    }
}

private fun AppStorePaywallNotice.message(store: PaywallStoreKind) = when (this) {
    AppStorePaywallNotice.Pending -> Res.string.paywall_notice_pending
    AppStorePaywallNotice.PurchaseFailed -> Res.string.paywall_notice_failed
    AppStorePaywallNotice.OwnedByAnotherAccount ->
        if (store == PaywallStoreKind.GooglePlay) Res.string.paywall_notice_owned_play else Res.string.paywall_notice_owned
    AppStorePaywallNotice.Invalid -> Res.string.paywall_notice_invalid
    AppStorePaywallNotice.ConfirmationPending ->
        if (store == PaywallStoreKind.GooglePlay) Res.string.paywall_notice_confirmation_play else Res.string.paywall_notice_confirmation
    AppStorePaywallNotice.NothingToRestore ->
        if (store == PaywallStoreKind.GooglePlay) Res.string.paywall_notice_nothing_play else Res.string.paywall_notice_nothing
    AppStorePaywallNotice.RestoreFailed -> Res.string.paywall_notice_restore_failed
    AppStorePaywallNotice.CouponRedeemOpened -> Res.string.paywall_notice_coupon_redeem
}

@Composable
private fun PaywallCouponUi.discountLabel(): String = when {
    durationCycles <= 0 -> stringResource(Res.string.paywall_coupon_off, discountPercent)
    cycle == SubscriptionCycle.Annual && durationCycles == 1 -> stringResource(Res.string.paywall_coupon_off_one_year, discountPercent)
    cycle == SubscriptionCycle.Annual -> stringResource(Res.string.paywall_coupon_off_years, discountPercent, durationCycles)
    durationCycles == 1 -> stringResource(Res.string.paywall_coupon_off_one_month, discountPercent)
    else -> stringResource(Res.string.paywall_coupon_off_months, discountPercent, durationCycles)
}

/** O cupom vale para o plano escolhido aqui e o ciclo da tela; trocar o plano pede validar de novo. */
@Composable
private fun AppStorePaywallCouponSheet(state: AppStorePaywallState, onIntent: (AppStorePaywallIntent) -> Unit) {
    val sheet = state.couponSheet
    val plans = state.plans.filter { it.offer(state.cycle) != null }
    SaqzBottomSheet(
        open = sheet != null,
        onClose = { onIntent(AppStorePaywallIntent.CloseCoupon) },
        title = stringResource(Res.string.paywall_coupon_title),
        modifier = Modifier.testTag(AppStorePaywallTags.CouponSheet),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.paywall_coupon_apply),
                onClick = { onIntent(AppStorePaywallIntent.ApplyCoupon) },
                fullWidth = true,
                enabled = sheet != null && sheet.code.isNotEmpty(),
                loading = sheet?.isValidating == true,
                modifier = Modifier.testTag(AppStorePaywallTags.CouponApply),
            )
        },
    ) {
        if (sheet == null) return@SaqzBottomSheet
        SaqzInput(
            value = sheet.code,
            onValueChange = { onIntent(AppStorePaywallIntent.CouponCodeChanged(it)) },
            label = stringResource(Res.string.paywall_coupon_field),
            enabled = !sheet.isValidating,
            modifier = Modifier.fillMaxWidth().testTag(AppStorePaywallTags.CouponField),
        )
        if (plans.size > 1) {
            SaqzSegmented(
                options = plans.map { it.name },
                selected = plans.indexOfFirst { it.plan == sheet.plan }.coerceAtLeast(0),
                onSelect = { index -> onIntent(AppStorePaywallIntent.CouponPlanChanged(plans[index].plan)) },
                modifier = Modifier.testTag(AppStorePaywallTags.CouponPlan),
            )
        }
        sheet.message?.let {
            Text(
                text = stringResource(it.text()),
                style = SaqzTheme.typography.support,
                color = if (it == PaywallCouponMessage.Pending) SaqzTheme.colors.textSecondary else SaqzTheme.colors.errorForeground,
                maxLines = 1,
                modifier = Modifier
                    .testTag(AppStorePaywallTags.CouponMessage)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

private fun PaywallCouponMessage.text() = when (this) {
    PaywallCouponMessage.NotFound -> Res.string.paywall_coupon_not_found
    PaywallCouponMessage.Expired -> Res.string.paywall_coupon_expired
    PaywallCouponMessage.InvalidPlan -> Res.string.paywall_coupon_invalid_plan
    PaywallCouponMessage.InvalidCycle -> Res.string.paywall_coupon_invalid_cycle
    PaywallCouponMessage.NotEligible -> Res.string.paywall_coupon_not_eligible
    PaywallCouponMessage.Exhausted -> Res.string.paywall_coupon_exhausted
    PaywallCouponMessage.OnlyOnWeb -> Res.string.paywall_coupon_only_web
    PaywallCouponMessage.Pending -> Res.string.paywall_coupon_pending
    PaywallCouponMessage.Failed -> Res.string.paywall_coupon_failed
}

internal object AppStorePaywallPreviewData {
    private fun plan(
        plan: Plan,
        monthly: String,
        annual: String,
        benefits: List<String>,
        current: Boolean = false,
        maxGroups: Int? = null,
    ) =
        AppStorePaywallPlanUi(
            plan = plan,
            name = plan.name,
            benefits = benefits.map(UiText::Raw),
            monthly = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.mensal", monthly, current),
            annual = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.anual", annual, false),
            maxGroups = maxGroups,
        )

    val ready = AppStorePaywallState(
        phase = AppStorePaywallPhase.Ready,
        plans = listOf(
            plan(Plan.Titular, "R$ 39,90", "R$ 359,90", listOf("1 grupo", "Até 25 atletas por grupo"), maxGroups = 1),
            plan(Plan.Organizador, "R$ 59,90", "R$ 539,90", listOf("3 grupos", "Atletas ilimitados"), maxGroups = 3),
            plan(Plan.Ilimitado, "R$ 89,90", "R$ 809,90", listOf("Grupos ilimitados", "Atletas ilimitados", "Relatórios")),
        ),
    )
}

@Composable
private fun AppStorePaywallPreview(state: AppStorePaywallState) = SaqzTheme {
    AppStorePaywallScreen(state = state, onIntent = {}, onBack = {}, onOpenTerms = {}, onOpenPrivacy = {})
}

@Preview(name = "Mensal")
@Composable
private fun AppStorePaywallMonthlyPreview() = AppStorePaywallPreview(AppStorePaywallPreviewData.ready)

@Preview(name = "Google Play")
@Composable
private fun AppStorePaywallGooglePlayPreview() =
    AppStorePaywallPreview(AppStorePaywallPreviewData.ready.copy(store = PaywallStoreKind.GooglePlay))

@Preview(name = "Anual")
@Composable
private fun AppStorePaywallAnnualPreview() =
    AppStorePaywallPreview(AppStorePaywallPreviewData.ready.copy(cycle = SubscriptionCycle.Annual))

@Preview(name = "Comprando")
@Composable
private fun AppStorePaywallPurchasingPreview() = AppStorePaywallPreview(
    AppStorePaywallPreviewData.ready.copy(purchasingProductId = "app.saqz.organizador.mensal"),
)

@Preview(name = "Confirmação pendente")
@Composable
private fun AppStorePaywallConfirmationPreview() = AppStorePaywallPreview(
    AppStorePaywallPreviewData.ready.copy(notice = AppStorePaywallNotice.ConfirmationPending),
)

@Preview(name = "Outra conta")
@Composable
private fun AppStorePaywallOwnedPreview() = AppStorePaywallPreview(
    AppStorePaywallPreviewData.ready.copy(notice = AppStorePaywallNotice.OwnedByAnotherAccount),
)

@Preview(name = "Limite de grupos")
@Composable
private fun AppStorePaywallLimitPreview() = SaqzTheme {
    AppStorePaywallScreen(
        state = AppStorePaywallPreviewData.ready,
        onIntent = {}, onBack = {}, onOpenTerms = {}, onOpenPrivacy = {},
        groupLimit = PaywallGroupLimit(groupsUsed = 3, groupsLimit = 3, planName = "Organizador"),
    )
}

@Preview(name = "Cupom aplicado")
@Composable
private fun AppStorePaywallCouponPreview() = AppStorePaywallPreview(
    AppStorePaywallPreviewData.ready.copy(
        store = PaywallStoreKind.GooglePlay,
        coupon = PaywallCouponUi("VERAO30", Plan.Organizador, SubscriptionCycle.Monthly, 30, 3, discountedPrice = "R$ 41,93"),
    ),
)

@Preview(name = "Folha do cupom")
@Composable
private fun AppStorePaywallCouponSheetPreview() = AppStorePaywallPreview(
    AppStorePaywallPreviewData.ready.copy(
        couponSheet = PaywallCouponSheet(code = "VERAO30", plan = Plan.Organizador, message = PaywallCouponMessage.Pending),
    ),
)

@Preview(name = "Carregando")
@Composable
private fun AppStorePaywallLoadingPreview() = AppStorePaywallPreview(AppStorePaywallState())

@Preview(name = "Falha ao carregar")
@Composable
private fun AppStorePaywallLoadFailedPreview() =
    AppStorePaywallPreview(AppStorePaywallState(phase = AppStorePaywallPhase.LoadFailed))

@Preview(name = "Assinatura confirmada")
@Composable
private fun AppStorePaywallSubscribedPreview() =
    AppStorePaywallPreview(AppStorePaywallPreviewData.ready.copy(isSubscribed = true))
