package br.com.saqz.subscriptions.presentation.myplan

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onFailure
import br.com.saqz.domain.onSuccess
import br.com.saqz.subscriptions.domain.subscription.ChangePlanCommand
import br.com.saqz.subscriptions.domain.subscription.ChangePlanResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.PlanDetails
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_downgrade_blocked
import br.com.saqz.subscriptions.resources.myplan_pending_payment_message
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 8e. Carrega `plans()` + `mySubscription()` + `receipts()` no init — a primeira tela do
 * projeto a chamar um gateway de verdade (VUL-112). `plans` fica em memória só para
 * resolver nome/limite de cada `Plan`; quem manda no que a tela mostra é sempre
 * `MyPlanState`.
 */
class MyPlanViewModel(
    private val gateway: SubscriptionGateway,
    initialState: MyPlanState = MyPlanState(),
) : MviViewModel<MyPlanState, MyPlanIntent, Nothing>(initialState) {

    private var subscription: MySubscription? = null
    private var plans: List<PlanDetails> = emptyList()

    // Guarda de geração (AGENTS.md §4): uma troca ou um cancelamento bem-sucedido recarrega
    // tudo, e essa recarga descarta a própria resposta se outra já tiver começado depois dela.
    private var loadGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: MyPlanIntent) {
        when (intent) {
            MyPlanIntent.Retry -> load()
            MyPlanIntent.OpenChangePlan -> update { it.copy(isChangeSheetOpen = true, changeError = null) }
            MyPlanIntent.DismissChangePlan -> update { it.copy(isChangeSheetOpen = false, changeError = null) }
            is MyPlanIntent.SelectPlan -> changePlan(intent.planId)
            MyPlanIntent.OpenReceipts -> update { it.copy(isReceiptsSheetOpen = true) }
            MyPlanIntent.DismissReceipts -> update { it.copy(isReceiptsSheetOpen = false) }
            MyPlanIntent.OpenCancel -> update { it.copy(isCancelSheetOpen = true, cancelError = null) }
            MyPlanIntent.DismissCancel -> update { it.copy(isCancelSheetOpen = false, cancelError = null) }
            MyPlanIntent.ConfirmCancel -> cancel()
            MyPlanIntent.DismissPendingPayment -> update { it.copy(pendingPayment = null) }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            val plansResult = gateway.plans()
            val subscriptionResult = gateway.mySubscription()
            if (generation != loadGeneration) return@launch

            when (subscriptionResult) {
                is SaqzResult.Failure -> update {
                    it.copy(isLoading = false, loadError = subscriptionResult.error.toUiText())
                }
                is SaqzResult.Success -> {
                    subscription = subscriptionResult.value
                    plans = (plansResult as? SaqzResult.Success)?.value.orEmpty()
                    val receipts = when (val result = gateway.receipts()) {
                        is SaqzResult.Success -> result.value
                        is SaqzResult.Failure -> emptyList()
                    }
                    if (generation != loadGeneration) return@launch
                    update {
                        it.copy(
                            isLoading = false,
                            loadError = null,
                            plan = subscriptionResult.value.toCardUi(plans),
                            usage = subscriptionResult.value.toUsageUi(),
                            receipts = receipts.map { receipt -> receipt.toUi() },
                            changeOptions = plans.map { details -> details.toChangeOptionUi(subscriptionResult.value) },
                        )
                    }
                }
            }
        }
    }

    // Intent inválido retorna cedo (AGENTS.md §4): um segundo toque em "trocar" enquanto a
    // primeira chamada ainda está no ar não abre uma segunda em paralelo.
    private fun changePlan(planId: Plan) {
        if (state.value.isChangingPlan) return
        update { it.copy(isChangingPlan = true, changeError = null) }
        viewModelScope.launch {
            gateway.changePlan(ChangePlanCommand(requestId = newRequestId(), targetPlanId = planId))
                .onSuccess(::applyChangeResult)
                .onFailure { error -> update { it.copy(isChangingPlan = false, changeError = changeErrorMessage(error, planId)) } }
        }
    }

    private fun applyChangeResult(result: ChangePlanResult) {
        if (result.pendingUpgradePlanId != null) {
            update {
                it.copy(
                    isChangingPlan = false,
                    isChangeSheetOpen = false,
                    pendingPayment = MyPlanPendingPaymentUi(
                        message = UiText.Res(Res.string.myplan_pending_payment_message),
                        pixCopyPaste = result.pixCopyPaste,
                        invoiceUrl = result.invoiceUrl,
                    ),
                )
            }
        } else {
            update { it.copy(isChangingPlan = false, isChangeSheetOpen = false) }
        }
        // Troca imediata e downgrade agendado já chegam completos em `MySubscription` — uma
        // recarga é mais simples que remontar o card a partir de `ChangePlanResult` e mantém
        // uma fonte só de verdade para o card e o uso.
        load()
    }

    private fun changeErrorMessage(error: SubscriptionError, targetPlanId: Plan): UiText =
        if (error == SubscriptionError.DowngradeBlocked) downgradeBlockedMessage(targetPlanId) else error.toUiText()

    private fun downgradeBlockedMessage(targetPlanId: Plan): UiText {
        val used = subscription?.usage?.groupsUsed ?: 0
        val target = plans.firstOrNull { it.id == targetPlanId }
        return UiText.Res(
            Res.string.myplan_downgrade_blocked,
            listOf(used, target.displayName(targetPlanId), target?.maxGroups ?: 0),
        )
    }

    private fun cancel() {
        if (state.value.isCanceling) return
        update { it.copy(isCanceling = true, cancelError = null) }
        viewModelScope.launch {
            gateway.cancel()
                .onSuccess {
                    update { it.copy(isCanceling = false, isCancelSheetOpen = false) }
                    load()
                }
                .onFailure { error -> update { it.copy(isCanceling = false, cancelError = error.toUiText()) } }
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun newRequestId(): String = Uuid.random().toString()
