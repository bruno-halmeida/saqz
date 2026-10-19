package br.com.saqz.subscriptions.presentation.myplan

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onFailure
import br.com.saqz.domain.onSuccess
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.domain.subscription.SubscriptionError
import br.com.saqz.subscriptions.domain.subscription.SubscriptionProvider
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
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
            MyPlanIntent.OpenCancel -> if (canManageWebPlan()) update { it.copy(isCancelSheetOpen = true, cancelError = null) }
            MyPlanIntent.DismissCancel -> update { it.copy(isCancelSheetOpen = false, cancelError = null) }
            MyPlanIntent.ConfirmCancel -> cancel()
            MyPlanIntent.Reactivate -> reactivate()
            MyPlanIntent.OpenChangePlan,
            MyPlanIntent.ManageStoreSubscription,
            MyPlanIntent.OpenSubscribe,
            -> openManagement(intent)
        }
    }

    private fun openManagement(intent: MyPlanIntent) {
        when (intent) {
            MyPlanIntent.OpenChangePlan -> if (canManageWebPlan()) emit(MyPlanEffect.OpenChangePlan)
            MyPlanIntent.ManageStoreSubscription -> if (canManagePaidPlan() && state.value.managedByStore) {
                emit(MyPlanEffect.ManageStoreSubscription)
            }
            MyPlanIntent.OpenSubscribe -> if (!state.value.isLoading &&
                (state.value.trial?.canSubscribe == true || state.value.canResubscribe)
            ) {
                emit(MyPlanEffect.OpenSubscribe)
            }
            else -> Unit
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

            // Checkout do site que nunca foi pago segue como quem nunca assinou: teste ou "Assinar", não um plano em atraso.
            val neverPaid = subscriptionResult is SaqzResult.Success && subscriptionResult.value.neverPaid()
            val neverSubscribed = neverPaid ||
                subscriptionResult is SaqzResult.Failure && subscriptionResult.error == SubscriptionError.NotFound
            val trialResult = if (neverSubscribed) trialGateway?.ownerTrial() else null
            if (generation != loadGeneration) return@launch

            val trialAccess = (trialResult as? SaqzResult.Success)?.value?.takeIf { it.status != TrialStatus.Subscribed }
            if (neverSubscribed && trialAccess != null) {
                update {
                    it.copy(
                        isLoading = false,
                        loadError = null,
                        plan = null,
                        usage = null,
                        trial = trialAccess.toUi(),
                        receipts = emptyList(),
                        hasMoreReceipts = false,
                        managingStore = null,
                        canResubscribe = false,
                        canReactivate = false,
                        isReactivating = false,
                        reactivateError = null,
                        payUrl = null,
                    )
                }
                return@launch
            }
            if (subscriptionResult is SaqzResult.Failure) {
                update { it.copy(isLoading = false, loadError = subscriptionResult.error.toUiText()) }
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
                    managingStore = loadedSubscription.provider.takeIf { it != SubscriptionProvider.Asaas },
                    canResubscribe = loadedSubscription.canResubscribe(),
                    canReactivate = loadedSubscription.canReactivate(),
                    isReactivating = false,
                    reactivateError = null,
                    payUrl = loadedSubscription.payUrl,
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
        if (!canManageWebPlan() || state.value.isCanceling || state.value.plan?.statusTone == MyPlanStatusTone.Canceled) return
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

    private fun reactivate() {
        val current = state.value
        if (!canManagePaidPlan() || !current.canReactivate || current.isReactivating) return
        // Na loja o app não reativa sozinho: abre a gestão da assinatura na conta da loja.
        if (current.managedByStore) {
            emit(MyPlanEffect.ManageStoreSubscription)
            return
        }
        update { it.copy(isReactivating = true, reactivateError = null) }
        viewModelScope.launch {
            when (val result = gateway.reactivate()) {
                is SaqzResult.Success -> load()
                // 409/404: o plano mudou por outro caminho (outro aparelho, período vencido); a recarga mostra o estado real.
                is SaqzResult.Failure -> if (result.error == SubscriptionError.Conflict || result.error == SubscriptionError.NotFound) {
                    load()
                } else {
                    update { it.copy(isReactivating = false, reactivateError = result.error.toUiText()) }
                }
            }
        }
    }

    private fun canManagePaidPlan(): Boolean =
        !state.value.isLoading && state.value.loadError == null && state.value.plan != null && state.value.trial == null

    /** Troca e cancelamento pelo backend só existem na assinatura da web. */
    private fun canManageWebPlan(): Boolean = canManagePaidPlan() && !state.value.managedByStore
}

private fun br.com.saqz.subscriptions.domain.trial.TrialAccess.toUi() = MyPlanTrialUi(
    status = status,
    endsAt = endsAt,
    isOwner = isOwner,
    canSubscribe = isOwner && status != TrialStatus.Subscribed,
    trialDays = trialDays,
    daysLeft = daysLeft,
    usage = usage,
)

/**
 * Só a assinatura que acabou de vez: cancelada (ou vencida sem renovar, CANCELED na App Store) e já
 * sem direito. Em atraso de pagamento a saída é regularizar na loja ou no Asaas, não abrir uma
 * segunda assinatura; cancelada mas ainda no período pago, o acesso continua e não há o que assinar.
 */
internal fun MySubscription.canResubscribe(): Boolean =
    !entitled && (canceledAt != null || status == SubscriptionStatus.Canceled)

/** Cancelada que ainda dá acesso até o fim do período pago: é o que o "Reativar" desfaz. */
internal fun MySubscription.canReactivate(): Boolean = entitled && canceledAt != null

/** Checkout do site que nunca foi pago: a linha existe em atraso, mas ninguém assinou. As lojas só criam a assinatura na compra. */
internal fun MySubscription.neverPaid(): Boolean = provider == SubscriptionProvider.Asaas && !paymentConfirmed
