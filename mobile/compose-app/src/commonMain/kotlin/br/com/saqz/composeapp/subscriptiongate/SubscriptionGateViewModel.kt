package br.com.saqz.composeapp.subscriptiongate

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.subscriptions.domain.purchase.PurchaseInformationGateway
import br.com.saqz.subscriptions.domain.subscription.CustomerInfoProvider
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Composition-root coordinator for the subscription-required gate. It composes the existing
 * group-creation entitlement with the subscriptions purchase-information port, so neither
 * feature depends on the other and the plan/limit rule has one owner.
 */
class SubscriptionGateViewModel(
    private val entitlement: GroupCreationEntitlement,
    private val purchaseInformation: PurchaseInformationGateway,
    private val customerInfo: CustomerInfoProvider,
) : MviViewModel<SubscriptionGateState, SubscriptionGateIntent, SubscriptionGateEffect>(
    SubscriptionGateState(),
) {
    private var visible = false
    private var foreground = true
    private var authorized = false
    private var operationActive = false
    private var lifecycleGeneration = 0L
    private var operationJob: Job? = null
    private var pollingJob: Job? = null

    override fun onIntent(intent: SubscriptionGateIntent) {
        when (intent) {
            SubscriptionGateIntent.Opened -> open()
            SubscriptionGateIntent.Closed -> close()
            is SubscriptionGateIntent.ForegroundChanged -> setForeground(intent.isForeground)
            SubscriptionGateIntent.RequestPurchaseInformation -> requestPurchaseInformation()
            SubscriptionGateIntent.RefreshAuthorization -> checkAuthorization()
        }
    }

    private fun open() {
        if (visible) return
        visible = true
        authorized = false
        lifecycleGeneration++
        update { it.copy(status = SubscriptionGateStatus.Initial, failure = null, maskedEmail = null) }
        startPolling()
        checkAuthorization()
    }

    private fun close() {
        visible = false
        lifecycleGeneration++
        stopPolling()
        cancelOperation()
    }

    private fun setForeground(isForeground: Boolean) {
        if (foreground == isForeground) return
        foreground = isForeground
        lifecycleGeneration++
        if (!isForeground) {
            stopPolling()
            cancelOperation()
        } else if (visible && !authorized) {
            startPolling()
            checkAuthorization()
        }
    }

    private fun checkAuthorization() {
        if (!visible || !foreground || authorized || operationActive) return
        val generation = lifecycleGeneration
        startOperation {
            if (!isCurrent(generation)) return@startOperation
            update { it.copy(status = SubscriptionGateStatus.Verifying, failure = null) }
            try {
                val canCreateGroup = entitlement.canCreateGroup()
                if (!isCurrent(generation)) return@startOperation
                if (canCreateGroup) {
                    authorized = true
                    stopPolling()
                    update {
                        it.copy(status = SubscriptionGateStatus.Authorized, failure = null)
                    }
                    emit(SubscriptionGateEffect.AuthorizationGranted)
                } else {
                    update {
                        it.copy(status = SubscriptionGateStatus.NotAuthorized, failure = null)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (!isCurrent(generation)) return@startOperation
                update {
                    it.copy(
                        status = SubscriptionGateStatus.Failed,
                        failure = SubscriptionGateFailure.Authorization,
                    )
                }
            }
        }
    }

    private fun requestPurchaseInformation() {
        if (!visible || !foreground || authorized || operationActive) return
        val generation = lifecycleGeneration
        startOperation {
            if (!isCurrent(generation)) return@startOperation
            update {
                it.copy(
                    status = SubscriptionGateStatus.Sending,
                    maskedEmail = null,
                    failure = null,
                )
            }
            val email = try {
                customerInfo.current()?.email
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (!isCurrent(generation)) return@startOperation
                null
            }
            if (!isCurrent(generation)) return@startOperation
            val result = try {
                purchaseInformation.request()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (!isCurrent(generation)) return@startOperation
            when (result) {
                null -> update {
                    it.copy(
                        status = SubscriptionGateStatus.Failed,
                        failure = SubscriptionGateFailure.PurchaseInformation,
                        maskedEmail = null,
                    )
                }
                is SaqzResult.Success -> update {
                    it.copy(
                        status = SubscriptionGateStatus.Sent,
                        maskedEmail = maskEmail(email),
                        failure = null,
                    )
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        status = SubscriptionGateStatus.Failed,
                        failure = SubscriptionGateFailure.PurchaseInformation,
                        maskedEmail = null,
                    )
                }
            }
        }
    }

    private fun isCurrent(generation: Long) =
        generation == lifecycleGeneration && visible && foreground && !authorized

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive && visible && foreground && !authorized) {
                delay(POLL_INTERVAL_MILLIS)
                if (visible && foreground && !authorized) checkAuthorization()
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startOperation(block: suspend () -> Unit) {
        operationActive = true
        operationJob = viewModelScope.launch {
            try {
                block()
            } finally {
                operationActive = false
                operationJob = null
            }
        }
    }

    private fun cancelOperation() {
        operationJob?.cancel()
    }

    companion object {
        const val POLL_INTERVAL_MILLIS = 20_000L
    }
}

internal fun maskEmail(email: String?): String? {
    val value = email?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val at = value.indexOf('@')
    if (at <= 0 || at == value.lastIndex) return null
    val local = value.substring(0, at)
    val domain = value.substring(at + 1)
    val maskedLocal = when {
        local.length == 1 -> "*"
        local.length == 2 -> "${local.first()}*"
        else -> "${local.first()}***${local.last()}"
    }
    return "$maskedLocal@$domain"
}
