package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanChangeOptionUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanPendingPaymentUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanReceiptUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import br.com.saqz.subscriptions.presentation.myplan.MyPlanUsageUi
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_retry
import br.com.saqz.subscriptions.resources.myplan_title
import org.jetbrains.compose.resources.stringResource

internal object MyPlanTags {
    const val Screen = "myplan"
    const val PlanCard = "myplan-plan-card"
    const val UsageCard = "myplan-usage-card"
    const val ChangePlan = "myplan-change-plan"
    const val PaymentMethod = "myplan-payment-method"
    const val Receipts = "myplan-receipts"
    const val LoadMoreReceipts = "myplan-load-more-receipts"
    const val AddCoupon = "myplan-add-coupon"
    const val CancelButton = "myplan-cancel-button"
    const val ChangeSheet = "myplan-change-sheet"
    const val ReceiptsSheet = "myplan-receipts-sheet"
    const val CancelSheet = "myplan-cancel-sheet"
    const val PendingPaymentSheet = "myplan-pending-payment-sheet"
}

/** 8e — plano atual, uso, recibos e o menu Gerenciar. A tela só empilha; cada bloco é uma
 * seção em `MyPlanSections.kt`. */
@Composable
fun MyPlanScreen(
    state: MyPlanState,
    onBack: () -> Unit,
    onIntent: (MyPlanIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenChangePlan: (() -> Unit)? = null,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().testTag(MyPlanTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.myplan_title), onBack = onBack)
        when {
            state.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
            state.loadError != null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzEmptyState(
                    title = state.loadError.asString(),
                    icon = SaqzIcons.CircleAlert,
                    action = stringResource(Res.string.myplan_retry),
                    onAction = { onIntent(MyPlanIntent.Retry) },
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                state.plan?.let { MyPlanCurrentCard(it) }
                state.usage?.let { MyPlanUsageCard(it) }
                MyPlanManageSection(
                    state = state,
                    onIntent = onIntent,
                    onOpenChangePlan = onOpenChangePlan,
                )
                // Assinatura já efetivamente cancelada (achado do Codex no PR #93) não tem
                // o que cancelar de novo — o backend já rejeita com AlreadyCanceled.
                if (state.plan?.statusTone != MyPlanStatusTone.Canceled) {
                    MyPlanCancelSection(onIntent = onIntent)
                }
            }
        }
    }

    MyPlanChangeSheet(state = state, onIntent = onIntent)
    MyPlanReceiptsSheet(state = state, onIntent = onIntent)
    MyPlanCancelSheet(state = state, onIntent = onIntent)
    MyPlanPendingPaymentSheet(state = state, onIntent = onIntent)
}

// Dado das previews: os mesmos números do export (8e), para o print do PR bater com o
// desenho — R$ 17,91/mês, cupom GALERA10 não existe no contrato de `MySubscription`
// (ver MyPlanMappers.kt), então o preço mostrado é o de tabela do plano.
internal object MyPlanPreviewData {
    val active = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Ativo"),
            statusTone = MyPlanStatusTone.Active,
            priceLine = UiText.Raw("R$ 19,90/mês"),
            nextChargeDate = "24/08/2026",
            paymentMethodLabel = UiText.Raw("Pix"),
            pendingChangeLine = null,
        ),
        usage = MyPlanUsageUi(
            ratioLabel = UiText.Raw("2 de 3 grupos"),
            progress = 2f / 3f,
            helperText = UiText.Raw("Perto do limite? Suba de plano quando precisar de mais grupos."),
        ),
        receipts = listOf(
            MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 19,90"),
            MyPlanReceiptUi("evt-2", "01/06/2026", "R$ 19,90"),
            MyPlanReceiptUi("evt-3", "01/05/2026", "R$ 19,90"),
        ),
        changeOptions = listOf(
            MyPlanChangeOptionUi(Plan.Titular, "Amador", UiText.Raw("Grátis"), isCurrent = false),
            MyPlanChangeOptionUi(Plan.Organizador, "Organizador", UiText.Raw("R$ 19,90/mês"), isCurrent = true),
            MyPlanChangeOptionUi(Plan.Ilimitado, "Quadra Cheia", UiText.Raw("R$ 39,90/mês"), isCurrent = false),
        ),
    )

    val pendingDowngrade = active.copy(
        plan = active.plan?.copy(
            pendingChangeLine = UiText.Raw("Troca para Amador vale a partir de 01/09/2026"),
        ),
    )

    val loading = MyPlanState(isLoading = true)

    val error = MyPlanState(isLoading = false, loadError = UiText.Raw("Não foi possível carregar seu plano agora."))
}

@Preview
@Composable
private fun MyPlanScreenActivePreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.active, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun MyPlanScreenPendingDowngradePreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.pendingDowngrade, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun MyPlanScreenLoadingPreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.loading, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun MyPlanScreenErrorPreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.error, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun MyPlanScreenChangeSheetPreview() = SaqzTheme {
    MyPlanScreen(
        state = MyPlanPreviewData.active.copy(isChangeSheetOpen = true),
        onBack = {},
        onIntent = {},
    )
}

@Preview
@Composable
private fun MyPlanScreenCancelSheetPreview() = SaqzTheme {
    MyPlanScreen(
        state = MyPlanPreviewData.active.copy(isCancelSheetOpen = true),
        onBack = {},
        onIntent = {},
    )
}
