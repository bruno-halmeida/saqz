package br.com.saqz.subscriptions.presentation.myplan

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onFailure
import br.com.saqz.domain.onSuccess
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.trial.TrialGateway
import br.com.saqz.subscriptions.domain.trial.TrialStatus
import kotlinx.coroutines.launch

private const val RECEIPTS_PAGE_SIZE = 20

/**
 * 8e. Carrega `mySubscription()` + `receipts()` no init — quem manda no que a tela mostra é
 * sempre `MyPlanState`.
 */
class MyPlanViewModel(
    private val gateway: SubscriptionGateway,
    initialState: MyPlanState = MyPlanState(),
    private val trialGateway: TrialGateway? = null,
) : MviViewModel<MyPlanState, MyPlanIntent, MyPlanEffect>(initialState) {

    // Guarda de geração (AGENTS.md §4): um cancelamento bem-sucedido recarrega tudo, e essa
    // recarga descarta a própria resposta se outra já tiver começado depois dela.
    private var loadGeneration = 0

    // Contador próprio dos recibos: `load`, `RetryReceipts` e `LoadMoreReceipts`/`RetryLoadMore` disputam a
    // mesma lista, e o `offset` de um "carregar mais" é calculado no despacho — se uma
    // recarga chegar no meio, a página em voo virou lixo e não pode ser concatenada.
    private var receiptsGeneration = 0

    init {
        load()
    }

    override fun handleIntent(intent: MyPlanIntent) {
        when (intent) {
            MyPlanIntent.Retry -> load()
            MyPlanIntent.Refresh -> load()
            MyPlanIntent.OpenReceipts -> if (canManagePaidPlan()) update { it.copy(isReceiptsSheetOpen = true) }
            MyPlanIntent.DismissReceipts -> update { it.copy(isReceiptsSheetOpen = false) }
            MyPlanIntent.RetryReceipts -> loadReceipts()
            MyPlanIntent.LoadMoreReceipts -> loadMoreReceipts()
            MyPlanIntent.RetryLoadMore -> loadMoreReceipts()
            MyPlanIntent.OpenCancel -> if (canManagePaidPlan()) update { it.copy(isCancelSheetOpen = true, cancelError = null) }
            MyPlanIntent.DismissCancel -> update { it.copy(isCancelSheetOpen = false, cancelError = null) }
            MyPlanIntent.ConfirmCancel -> cancel()
            MyPlanIntent.OpenChangePlan -> if (canManagePaidPlan()) emit(MyPlanEffect.OpenChangePlan)
            MyPlanIntent.OpenSubscribe -> if (!state.value.isLoading && state.value.trial?.canSubscribe == true) {
                emit(MyPlanEffect.OpenSubscribe)
            }
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
                isCancelSheetOpen = false,
                isReceiptsSheetOpen = false,
            )
        }
        viewModelScope.launch {
            val subscriptionResult = gateway.mySubscription()
            if (generation != loadGeneration) return@launch

            val trialResult = if (subscriptionResult is SaqzResult.Failure &&
                subscriptionResult.error == SubscriptionError.NotFound
            ) trialGateway?.ownerTrial() else null
            if (generation != loadGeneration) return@launch

            if (subscriptionResult is SaqzResult.Failure) {
                if (trialResult !is SaqzResult.Success || trialResult.value.status == TrialStatus.Subscribed) {
                    update { it.copy(isLoading = false, loadError = subscriptionResult.error.toUiText()) }
                    return@launch
                }
                update {
                    it.copy(
                        isLoading = false,
                        loadError = null,
                        plan = null,
                        usage = null,
                        trial = trialResult.value.toUi(),
                        receipts = emptyList(),
                        hasMoreReceipts = false,
                    )
                }
                return@launch
            }

            val loadedSubscription = (subscriptionResult as SaqzResult.Success).value

            val receiptsResult = gateway.receipts(limit = RECEIPTS_PAGE_SIZE, offset = 0)
            if (generation != loadGeneration || receiptsLoadGeneration != receiptsGeneration) return@launch
            update {
                it.copy(
                    isLoading = false,
                    loadError = null,
                    plan = loadedSubscription.toCardUi(),
                    usage = loadedSubscription.toUsageUi(),
                    trial = null,
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
                )
            }
        }
    }

    private fun loadReceipts() {
        if (!canManagePaidPlan()) return
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
        if (!canManagePaidPlan()) return
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

    private fun cancel() {
        if (!canManagePaidPlan() || state.value.isCanceling || state.value.plan?.statusTone == MyPlanStatusTone.Canceled) return
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

    private fun canManagePaidPlan(): Boolean =
        !state.value.isLoading && state.value.loadError == null && state.value.plan != null && state.value.trial == null
}

private fun br.com.saqz.subscriptions.domain.trial.TrialAccess.toUi() = MyPlanTrialUi(
    status = status,
    endsAt = endsAt,
    isOwner = isOwner,
    canSubscribe = isOwner && status != TrialStatus.Subscribed,
    trialDays = trialDays,
)
