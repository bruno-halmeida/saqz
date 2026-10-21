package br.com.saqz.subscriptions.presentation.ui.myplan

import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import androidx.compose.material.Text
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanReceiptUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import br.com.saqz.subscriptions.presentation.myplan.MyPlanUsageUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanTrialUi
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_pay_now
import br.com.saqz.subscriptions.resources.myplan_reactivate
import br.com.saqz.subscriptions.resources.myplan_resubscribe
import br.com.saqz.subscriptions.resources.myplan_retry
import br.com.saqz.subscriptions.resources.myplan_title
import org.jetbrains.compose.resources.stringResource

internal object MyPlanTags {
    const val Screen = "myplan"
    const val PlanCard = "myplan-plan-card"
    const val UsageCard = "myplan-usage-card"
    const val Receipts = "myplan-receipts"
    const val ChangePlan = "myplan-change-plan"
    const val ManageAppStore = "myplan-manage-app-store"
    const val LoadMoreReceipts = "myplan-load-more-receipts"
    const val CancelButton = "myplan-cancel-button"
    const val ReceiptsSheet = "myplan-receipts-sheet"
    const val CancelSheet = "myplan-cancel-sheet"
    const val TrialCard = "myplan-trial-card"
    const val TrialCountdown = "myplan-trial-countdown"
    const val Subscribe = "myplan-trial-subscribe"
    const val Resubscribe = "myplan-resubscribe"
    const val PayNow = "myplan-pay-now"
    const val Reactivate = "myplan-reactivate"
    const val ReactivateError = "myplan-reactivate-error"
    const val CourtesyNote = "myplan-courtesy-note"
    const val SubscriptionNote = "myplan-subscription-note"
}

/** 8e — plano atual, uso, recibos e o menu Gerenciar. A tela só empilha; cada bloco é uma
 * seção em `MyPlanSections.kt`. */
@Composable
fun MyPlanScreen(
    state: MyPlanState,
    onBack: () -> Unit,
    onIntent: (MyPlanIntent) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Loja cuja gestão este aparelho abre. Assinatura de outra loja (App Store vista no
     * Android, por exemplo) só ganha a explicação de onde gerenciar.
     */
    deviceStore: SubscriptionProvider? = null,
    /** Contratação disponível: a web (Asaas) ou a loja deste aparelho. */
    purchasesAvailable: Boolean = br.com.saqz.domain.StoreLaunchPolicy.purchases,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .testTag(MyPlanTags.Screen),
    ) {
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
                // Site em atraso (na carência ou congelado): a mesma fatura vencida do aviso. Sem fatura, sem botão.
                state.payUrl?.let { url ->
                    val uriHandler = LocalUriHandler.current
                    SaqzButton(
                        stringResource(Res.string.myplan_pay_now),
                        onClick = { runCatching { uriHandler.openUri(url) } },
                        fullWidth = true,
                        modifier = Modifier.testTag(MyPlanTags.PayNow),
                    )
                }
                // Assinatura encerrada: a loja do aparelho (App Store, Google Play). Sem loja o botão some,
                // como o do teste.
                if (state.canResubscribe && purchasesAvailable) {
                    SaqzButton(
                        stringResource(Res.string.myplan_resubscribe),
                        onClick = { onIntent(MyPlanIntent.OpenSubscribe) },
                        fullWidth = true,
                        modifier = Modifier.testTag(MyPlanTags.Resubscribe),
                    )
                }
                // Cancelada com acesso até o fim do período: na web o backend reativa; na loja o botão abre a
                // gestão da assinatura (só quando é a loja deste aparelho, como a linha de gerenciar).
                val reactivateAvailable = if (state.managedByStore) state.managingStore == deviceStore else purchasesAvailable
                if (state.canReactivate && reactivateAvailable) {
                    SaqzButton(
                        stringResource(Res.string.myplan_reactivate),
                        onClick = { onIntent(MyPlanIntent.Reactivate) },
                        fullWidth = true,
                        loading = state.isReactivating,
                        modifier = Modifier.testTag(MyPlanTags.Reactivate),
                    )
                    state.reactivateError?.let {
                        Text(
                            text = it.asString(),
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.errorForeground,
                            modifier = Modifier.testTag(MyPlanTags.ReactivateError),
                        )
                    }
                }
                state.trial?.let { trial ->
                    MyPlanTrialCard(
                        trial,
                        onSubscribe = { onIntent(MyPlanIntent.OpenSubscribe) },
                        purchasesAvailable = purchasesAvailable,
                    )
                }
                state.usage?.let { MyPlanUsageCard(it) }
                if (state.plan != null && !state.courtesyOnly) {
                    MyPlanManageSection(
                        state = state,
                        onIntent = onIntent,
                        deviceStore = deviceStore,
                    )
                }
                // Assinatura já efetivamente cancelada (achado do Codex no PR #93) não tem
                // o que cancelar de novo — o backend já rejeita com AlreadyCanceled. A da
                // loja (App Store ou Google Play) só se cancela na conta da loja.
                if (state.plan != null && !state.courtesyOnly &&
                    state.plan.statusTone != MyPlanStatusTone.Canceled && !state.managedByStore
                ) {
                    MyPlanCancelSection(onIntent = onIntent)
                }
            }
        }
    }

    MyPlanReceiptsSheet(state = state, onIntent = onIntent)
    MyPlanCancelSheet(state = state, onIntent = onIntent)
}

// Dado das previews: os mesmos números do export (8e), para o print do PR bater com o desenho.
internal object MyPlanPreviewData {
    val active = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Organizador",
            statusLabel = UiText.Raw("Ativo"),
            statusTone = MyPlanStatusTone.Active,
            nextChargeDate = "24/08/2026",
        ),
        usage = MyPlanUsageUi(
            ratioLabel = UiText.Raw("2 de 3 grupos"),
            progress = 2f / 3f,
            helperText = UiText.Raw("Você ainda pode criar 1 grupo."),
        ),
        receipts = listOf(
            MyPlanReceiptUi("evt-1", "01/07/2026", "R$ 19,90"),
            MyPlanReceiptUi("evt-2", "01/06/2026", "R$ 19,90"),
            MyPlanReceiptUi("evt-3", "01/05/2026", "R$ 19,90"),
        ),
    )

    val courtesyOnly = MyPlanState(
        isLoading = false,
        plan = MyPlanCardUi(
            name = "Ilimitado",
            statusLabel = UiText.Raw("Ativo"),
            statusTone = MyPlanStatusTone.Active,
            nextChargeDate = null,
            titleOverride = UiText.Raw("Plano Ilimitado ativo"),
            courtesyNote = UiText.Raw("Cortesia até 31/12/2026"),
        ),
        courtesyOnly = true,
    )

    val courtesyOverPaid = active.copy(
        plan = MyPlanCardUi(
            name = "Ilimitado",
            statusLabel = UiText.Raw("Ativo"),
            statusTone = MyPlanStatusTone.Active,
            nextChargeDate = "24/08/2026",
            courtesyNote = UiText.Raw("Cortesia sem prazo"),
            subscriptionNote = UiText.Raw("Sua assinatura é a do plano Organizador."),
        ),
        usage = null,
    )

    val loading = MyPlanState(isLoading = true)

    val error = MyPlanState(isLoading = false, loadError = UiText.Raw("Não foi possível carregar seu plano agora."))
}

@Preview
@Composable
private fun MyPlanScreenActivePreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.active, onBack = {}, onIntent = {})
}

@Preview(name = "Só cortesia")
@Composable
private fun MyPlanScreenCourtesyOnlyPreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.courtesyOnly, onBack = {}, onIntent = {})
}

@Preview(name = "Cortesia maior que o pago")
@Composable
private fun MyPlanScreenCourtesyOverPaidPreview() = SaqzTheme {
    MyPlanScreen(state = MyPlanPreviewData.courtesyOverPaid, onBack = {}, onIntent = {})
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

@Preview(name = "App Store")
@Composable
private fun MyPlanScreenAppStorePreview() = SaqzTheme {
    MyPlanScreen(
        state = MyPlanPreviewData.active.copy(managingStore = SubscriptionProvider.AppStore),
        onBack = {},
        onIntent = {},
        deviceStore = SubscriptionProvider.AppStore,
    )
}

@Preview(name = "Google Play")
@Composable
private fun MyPlanScreenGooglePlayPreview() = SaqzTheme {
    MyPlanScreen(
        state = MyPlanPreviewData.active.copy(managingStore = SubscriptionProvider.GooglePlay),
        onBack = {},
        onIntent = {},
        deviceStore = SubscriptionProvider.GooglePlay,
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
