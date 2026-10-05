package br.com.saqz.subscriptions.presentation.ui.appstore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
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
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.paywall_confirm_retry
import br.com.saqz.subscriptions.resources.paywall_cta
import br.com.saqz.subscriptions.resources.paywall_current
import br.com.saqz.subscriptions.resources.paywall_cycle_annual
import br.com.saqz.subscriptions.resources.paywall_cycle_monthly
import br.com.saqz.subscriptions.resources.paywall_disclosure
import br.com.saqz.subscriptions.resources.paywall_duration_annual
import br.com.saqz.subscriptions.resources.paywall_duration_monthly
import br.com.saqz.subscriptions.resources.paywall_load_error
import br.com.saqz.subscriptions.resources.paywall_notice_confirmation
import br.com.saqz.subscriptions.resources.paywall_notice_failed
import br.com.saqz.subscriptions.resources.paywall_notice_invalid
import br.com.saqz.subscriptions.resources.paywall_notice_nothing
import br.com.saqz.subscriptions.resources.paywall_notice_owned
import br.com.saqz.subscriptions.resources.paywall_notice_pending
import br.com.saqz.subscriptions.resources.paywall_notice_restore_failed
import br.com.saqz.subscriptions.resources.paywall_price_month
import br.com.saqz.subscriptions.resources.paywall_price_year
import br.com.saqz.subscriptions.resources.paywall_privacy
import br.com.saqz.subscriptions.resources.paywall_restore
import br.com.saqz.subscriptions.resources.paywall_retry
import br.com.saqz.subscriptions.resources.paywall_sub
import br.com.saqz.subscriptions.resources.paywall_subscribed
import br.com.saqz.subscriptions.resources.paywall_terms
import br.com.saqz.subscriptions.resources.paywall_title
import org.jetbrains.compose.resources.stringResource

object AppStorePaywallTags {
    const val Screen = "paywall"
    const val Cycle = "paywall-cycle"
    const val PlanPrefix = "paywall-plan-"
    const val PurchasePrefix = "paywall-purchase-"
    const val Notice = "paywall-notice"
    const val ConfirmRetry = "paywall-confirm-retry"
    const val Restore = "paywall-restore"
    const val Terms = "paywall-terms"
    const val Privacy = "paywall-privacy"
    const val Disclosure = "paywall-disclosure"
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
) {
    Column(
        modifier = modifier
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
            state.phase == AppStorePaywallPhase.Loading -> AppStorePaywallCentered { SaqzSpinner() }
            state.phase == AppStorePaywallPhase.LoadFailed -> AppStorePaywallCentered {
                SaqzEmptyState(
                    title = stringResource(Res.string.paywall_load_error),
                    icon = SaqzIcons.CircleAlert,
                    action = stringResource(Res.string.paywall_retry),
                    onAction = { onIntent(AppStorePaywallIntent.Retry) },
                )
            }
            state.phase == AppStorePaywallPhase.Ready -> AppStorePaywallCatalog(
                state = state,
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
        Text(
            text = stringResource(Res.string.paywall_sub),
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
        state.notice?.let { notice -> AppStorePaywallNoticeCard(notice, state.isConfirming, onIntent) }
        state.plans.forEach { plan ->
            val offer = plan.offer(state.cycle) ?: return@forEach
            AppStorePaywallPlanCard(
                plan = plan,
                offer = offer,
                cycle = state.cycle,
                purchasing = state.purchasingProductId == offer.productId,
                enabled = !state.isBusy,
                onPurchase = { onIntent(AppStorePaywallIntent.Purchase(offer.productId)) },
            )
        }
        Text(
            text = stringResource(Res.string.paywall_disclosure),
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
                offer.displayPrice,
            ),
            style = SaqzTheme.typography.headline,
            color = colors.primary,
        )
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
        if (!offer.isCurrent) {
            SaqzButton(
                label = stringResource(Res.string.paywall_cta, plan.name),
                onClick = onPurchase,
                fullWidth = true,
                enabled = enabled,
                loading = purchasing,
                modifier = Modifier.testTag(AppStorePaywallTags.PurchasePrefix + offer.productId),
            )
        }
    }
}

@Composable
private fun AppStorePaywallNoticeCard(
    notice: AppStorePaywallNotice,
    isConfirming: Boolean,
    onIntent: (AppStorePaywallIntent) -> Unit,
) {
    val isError = notice != AppStorePaywallNotice.Pending && notice != AppStorePaywallNotice.NothingToRestore
    SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(AppStorePaywallTags.Notice)) {
        Text(
            text = stringResource(notice.message()),
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

private fun AppStorePaywallNotice.message() = when (this) {
    AppStorePaywallNotice.Pending -> Res.string.paywall_notice_pending
    AppStorePaywallNotice.PurchaseFailed -> Res.string.paywall_notice_failed
    AppStorePaywallNotice.OwnedByAnotherAccount -> Res.string.paywall_notice_owned
    AppStorePaywallNotice.Invalid -> Res.string.paywall_notice_invalid
    AppStorePaywallNotice.ConfirmationPending -> Res.string.paywall_notice_confirmation
    AppStorePaywallNotice.NothingToRestore -> Res.string.paywall_notice_nothing
    AppStorePaywallNotice.RestoreFailed -> Res.string.paywall_notice_restore_failed
}

internal object AppStorePaywallPreviewData {
    private fun plan(plan: Plan, monthly: String, annual: String, benefits: List<String>, current: Boolean = false) =
        AppStorePaywallPlanUi(
            plan = plan,
            name = plan.name,
            benefits = benefits.map(UiText::Raw),
            monthly = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.mensal", monthly, current),
            annual = AppStorePaywallOfferUi("app.saqz.${plan.name.lowercase()}.anual", annual, false),
        )

    val ready = AppStorePaywallState(
        phase = AppStorePaywallPhase.Ready,
        plans = listOf(
            plan(Plan.Titular, "R$ 39,90", "R$ 359,90", listOf("1 grupo", "Até 25 atletas por grupo")),
            plan(Plan.Organizador, "R$ 59,90", "R$ 539,90", listOf("3 grupos", "Atletas ilimitados")),
            plan(Plan.Ilimitado, "R$ 89,90", "R$ 809,90", listOf("Grupos ilimitados", "Vários administradores", "Relatórios")),
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
