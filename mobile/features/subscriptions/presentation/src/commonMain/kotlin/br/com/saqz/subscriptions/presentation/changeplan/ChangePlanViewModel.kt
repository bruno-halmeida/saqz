package br.com.saqz.subscriptions.presentation.changeplan

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.ChangedPlan
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionGateway
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.changeplan_pending_note
import br.com.saqz.subscriptions.resources.changeplan_pix_summary
import br.com.saqz.subscriptions.resources.changeplan_pix_waiting
import br.com.saqz.subscriptions.resources.changeplan_scheduled_sub
import br.com.saqz.subscriptions.resources.changeplan_scheduled_title
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ChangePlanViewModel(
    private val gateway: SubscriptionGateway,
    initialState: ChangePlanState = ChangePlanState(),
) : MviViewModel<ChangePlanState, ChangePlanIntent, Nothing>(initialState) {

    private var loadGeneration = 0
    private val requestIds = mutableMapOf<Plan, String>()

    init {
        load()
    }

    override fun onIntent(intent: ChangePlanIntent) {
        when (intent) {
            ChangePlanIntent.Retry -> load()
            is ChangePlanIntent.SelectPlan -> select(intent.plan)
            ChangePlanIntent.DismissConfirm -> update {
                it.copy(confirmTarget = null, submitError = null)
            }
            ChangePlanIntent.ConfirmChange -> confirm()
            ChangePlanIntent.PixPaid -> checkPixPaid()
            ChangePlanIntent.BackToCatalog -> load()
        }
    }

    private fun select(plan: Plan) {
        val card = state.value.plans.firstOrNull { it.plan == plan } ?: return
        if (card.isCurrent || state.value.isSubmitting) return
        update { it.copy(confirmTarget = card, submitError = null) }
    }

    private fun requestIdFor(plan: Plan): String =
        requestIds.getOrPut(plan) { Uuid.random().toString() }

    private fun load() {
        val generation = ++loadGeneration
        update {
            it.copy(
                isLoading = true,
                isSubmitting = false,
                loadError = null,
                submitError = null,
                confirmTarget = null,
                phase = ChangePlanPhase.Catalog,
                pix = null,
                scheduled = null,
            )
        }
        viewModelScope.launch {
            val subscriptionResult = gateway.mySubscription()
            val plansResult = gateway.listPlans()
            if (generation != loadGeneration) return@launch
            if (subscriptionResult is SaqzResult.Failure) {
                update { it.copy(isLoading = false, loadError = subscriptionResult.error.toChangePlanUiText()) }
                return@launch
            }
            if (plansResult is SaqzResult.Failure) {
                update { it.copy(isLoading = false, loadError = plansResult.error.toChangePlanUiText()) }
                return@launch
            }
            val subscription = (subscriptionResult as SaqzResult.Success).value
            val catalog = (plansResult as SaqzResult.Success).value
            update {
                it.copy(
                    isLoading = false,
                    loadError = null,
                    cycle = subscription.cycle,
                    currentPlan = subscription.plan,
                    pendingNote = subscription.toPendingNote(),
                    plans = catalog.map { item -> item.toCardUi(subscription.plan, subscription.cycle) },
                )
            }
        }
    }

    private fun confirm() {
        val target = state.value.confirmTarget ?: return
        if (state.value.isSubmitting) return
        update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = gateway.changePlan(requestIdFor(target.plan), target.plan)) {
                is SaqzResult.Failure -> update {
                    it.copy(isSubmitting = false, submitError = result.error.toChangePlanUiText())
                }
                is SaqzResult.Success -> applyChange(result.value)
            }
        }
    }

    private fun applyChange(result: ChangedPlan) {
        val pendingUpgrade = result.pendingUpgradePlan
        val pendingPlan = result.pendingPlan
        when {
            pendingUpgrade != null -> update {
                it.copy(
                    isSubmitting = false,
                    confirmTarget = null,
                    phase = ChangePlanPhase.Pix,
                    pix = ChangePlanPixUi(
                        targetPlan = pendingUpgrade,
                        summary = UiText.Res(
                            Res.string.changeplan_pix_summary,
                            listOf((result.chargedCents ?: 0L).toBrlString()),
                        ),
                        copyPaste = result.pixCopyPaste.orEmpty(),
                        invoiceUrl = result.invoiceUrl,
                    ),
                )
            }
            pendingPlan != null -> update {
                it.copy(
                    isSubmitting = false,
                    confirmTarget = null,
                    phase = ChangePlanPhase.Scheduled,
                    scheduled = ChangePlanScheduledUi(
                        title = UiText.Res(Res.string.changeplan_scheduled_title),
                        subtitle = UiText.Res(
                            Res.string.changeplan_scheduled_sub,
                            listOf(
                                pendingPlan.name,
                                result.pendingPlanEffectiveAt?.let(::isoDateToPtBr).orEmpty(),
                            ),
                        ),
                    ),
                    pendingNote = UiText.Res(
                        Res.string.changeplan_pending_note,
                        listOf(
                            pendingPlan.name,
                            result.pendingPlanEffectiveAt?.let(::isoDateToPtBr).orEmpty(),
                        ),
                    ),
                )
            }
            else -> load()
        }
    }

    private fun checkPixPaid() {
        val target = state.value.pix?.targetPlan ?: return
        if (state.value.isSubmitting) return
        update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            when (val result = gateway.mySubscription()) {
                is SaqzResult.Failure -> update {
                    it.copy(isSubmitting = false, submitError = result.error.toChangePlanUiText())
                }
                is SaqzResult.Success -> {
                    if (result.value.plan == target) {
                        load()
                    } else {
                        update {
                            it.copy(
                                isSubmitting = false,
                                submitError = UiText.Res(Res.string.changeplan_pix_waiting),
                            )
                        }
                    }
                }
            }
        }
    }
}
