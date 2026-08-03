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

private const val RECEIPTS_PAGE_SIZE = 20

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

    // Contador próprio dos recibos: `load`, `RetryReceipts` e `LoadMoreReceipts`/`RetryLoadMore` disputam a
    // mesma lista, e o `offset` de um "carregar mais" é calculado no despacho — se uma
    // recarga chegar no meio, a página em voo virou lixo e não pode ser concatenada.
    private var receiptsGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: MyPlanIntent) {
        when (intent) {
            MyPlanIntent.Retry -> load()
            MyPlanIntent.OpenChangePlan -> update { it.copy(isChangeSheetOpen = true, changeError = null) }
            MyPlanIntent.DismissChangePlan -> update { it.copy(isChangeSheetOpen = false, changeError = null) }
            is MyPlanIntent.SelectPlan -> {
                update { it.copy(isChangeSheetOpen = false) }
                changePlan(intent.planId)
            }
            MyPlanIntent.OpenReceipts -> update { it.copy(isReceiptsSheetOpen = true) }
            MyPlanIntent.DismissReceipts -> update { it.copy(isReceiptsSheetOpen = false) }
            MyPlanIntent.RetryReceipts -> loadReceipts()
            MyPlanIntent.LoadMoreReceipts -> loadMoreReceipts()
            MyPlanIntent.RetryLoadMore -> loadMoreReceipts()
            MyPlanIntent.OpenCancel -> update { it.copy(isCancelSheetOpen = true, cancelError = null) }
            MyPlanIntent.DismissCancel -> update { it.copy(isCancelSheetOpen = false, cancelError = null) }
            MyPlanIntent.ConfirmCancel -> cancel()
            MyPlanIntent.DismissPendingPayment -> update { it.copy(pendingPayment = null) }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        val receiptsLoadGeneration = ++receiptsGeneration
        update {
            it.copy(
                isLoading = true,
                loadError = null,
                receiptsError = null,
                loadMoreReceiptsError = null,
                isLoadingMoreReceipts = false,
            )
        }
        viewModelScope.launch {
            val plansResult = gateway.plans()
            val subscriptionResult = gateway.mySubscription()
            if (generation != loadGeneration) return@launch

            // plans() é obrigatório: nome de exibição, preço e as opções de troca dependem
            // dele. Uma falha aqui não pode virar catálogo vazio silencioso (achado do
            // Codex no PR #93) — é carga que falhou, mesmo caminho de `mySubscription()`.
            if (plansResult is SaqzResult.Failure || subscriptionResult is SaqzResult.Failure) {
                val error = (subscriptionResult as? SaqzResult.Failure)?.error
                    ?: (plansResult as SaqzResult.Failure).error
                update { it.copy(isLoading = false, loadError = error.toUiText()) }
                return@launch
            }

            val loadedPlans = (plansResult as SaqzResult.Success).value
            val loadedSubscription = (subscriptionResult as SaqzResult.Success).value
            subscription = loadedSubscription
            plans = loadedPlans

            val receiptsResult = gateway.receipts(limit = RECEIPTS_PAGE_SIZE, offset = 0)
            if (generation != loadGeneration || receiptsLoadGeneration != receiptsGeneration) return@launch
            update {
                it.copy(
                    isLoading = false,
                    loadError = null,
                    plan = loadedSubscription.toCardUi(loadedPlans),
                    usage = loadedSubscription.toUsageUi(),
                    // Falha aqui não pode virar "nenhum recibo ainda" (achado do Codex no
                    // PR #93): mantém a última lista boa e guarda o erro à parte.
                    receipts = (receiptsResult as? SaqzResult.Success)?.value?.map { r -> r.toUi() } ?: it.receipts,
                    receiptsError = (receiptsResult as? SaqzResult.Failure)?.error?.toUiText(),
                    loadMoreReceiptsError = null,
                    hasMoreReceipts = when (receiptsResult) {
                        is SaqzResult.Success -> receiptsResult.value.size == RECEIPTS_PAGE_SIZE
                        is SaqzResult.Failure -> it.hasMoreReceipts
                    },
                    isLoadingMoreReceipts = false,
                    changeOptions = loadedPlans.map { details -> details.toChangeOptionUi(loadedSubscription) },
                )
            }
        }
    }

    private fun loadReceipts() {
        val generation = ++receiptsGeneration
        update {
            it.copy(
                receiptsError = null,
                loadMoreReceiptsError = null,
                isLoadingMoreReceipts = false,
            )
        }
        viewModelScope.launch {
            when (val result = gateway.receipts(limit = RECEIPTS_PAGE_SIZE, offset = 0)) {
                is SaqzResult.Success -> if (generation == receiptsGeneration) {
                    update {
                        it.copy(
                            receipts = result.value.map { r -> r.toUi() },
                            receiptsError = null,
                            loadMoreReceiptsError = null,
                            hasMoreReceipts = result.value.size == RECEIPTS_PAGE_SIZE,
                        )
                    }
                }
                is SaqzResult.Failure -> if (generation == receiptsGeneration) {
                    update { it.copy(receiptsError = result.error.toUiText()) }
                }
            }
        }
    }

    private fun loadMoreReceipts() {
        val currentState = state.value
        if (!currentState.hasMoreReceipts || currentState.isLoadingMoreReceipts) return

        val offset = currentState.receipts.size
        val generation = ++receiptsGeneration
        update { it.copy(isLoadingMoreReceipts = true, loadMoreReceiptsError = null) }
        viewModelScope.launch {
            when (val result = gateway.receipts(limit = RECEIPTS_PAGE_SIZE, offset = offset)) {
                is SaqzResult.Success -> if (generation == receiptsGeneration) {
                    update {
                        it.copy(
                            receipts = it.receipts + result.value.map { r -> r.toUi() },
                            hasMoreReceipts = result.value.size == RECEIPTS_PAGE_SIZE,
                            isLoadingMoreReceipts = false,
                            loadMoreReceiptsError = null,
                        )
                    }
                }
                is SaqzResult.Failure -> if (generation == receiptsGeneration) {
                    update {
                        it.copy(
                            isLoadingMoreReceipts = false,
                            loadMoreReceiptsError = result.error.toUiText(),
                        )
                    }
                }
            }
        }
    }

    // Intent inválido retorna cedo (AGENTS.md §4): um segundo toque em "trocar" enquanto a
    // primeira chamada ainda está no ar não abre uma segunda em paralelo — e trocar e
    // cancelar são a MESMA corrida (achado do Codex no PR #93): as duas mexem na cobrança,
    // então uma em voo bloqueia a outra também, não só ela mesma.
    private fun changePlan(planId: Plan) {
        if (state.value.isChangingPlan || state.value.isCanceling) return
        update { it.copy(isChangingPlan = true, changeError = null) }
        viewModelScope.launch {
            gateway.changePlan(ChangePlanCommand(requestId = newRequestId(), targetPlanId = planId))
                .onSuccess(::applyChangeResult)
                .onFailure { error ->
                    update {
                        it.copy(
                            isChangingPlan = false,
                            isChangeSheetOpen = true,
                            changeError = changeErrorMessage(error, planId),
                        )
                    }
                }
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
        if (state.value.isCanceling || state.value.isChangingPlan) return
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
