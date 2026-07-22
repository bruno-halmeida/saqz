package br.com.saqz.groups.presentation

import br.com.saqz.groups.port.GroupCancelable
import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.port.GroupLinkEventListener
import br.com.saqz.groups.port.GroupOperationResult
import br.com.saqz.groups.port.GroupResultCallback
import br.com.saqz.groups.port.GroupValueCallback
import br.com.saqz.groups.port.GroupValueResult
import br.com.saqz.groups.port.NativeGroupLinkPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface DeferredResolution<out T, out E> {
    data class Success<T>(val value: T) : DeferredResolution<T, Nothing>
    data class Failure<E>(val error: E) : DeferredResolution<Nothing, E>
}

internal class DeferredLinkStateMachine<S, T, E>(
    private val links: NativeGroupLinkPort,
    private val eventFilter: (GroupLinkEvent) -> String?,
    private val readPending: (GroupValueCallback) -> Unit,
    private val writePending: (String?, GroupResultCallback) -> Unit,
    private val resolve: suspend (String) -> DeferredResolution<T, E>,
    private val resolvedState: (T) -> S,
    private val onResolved: (T) -> Unit,
    private val initialState: () -> S,
    private val pendingState: () -> S,
    private val receivingState: (S) -> S,
    private val processing: (S) -> Boolean,
    private val retryAfter: (S) -> Int?,
    private val processingState: (S, Boolean) -> S,
    private val clearedState: (S) -> S,
    private val failureState: (S, E) -> Pair<S, Boolean>,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(initialState())
    val state: StateFlow<S> = mutableState.asStateFlow()
    private var pendingCode: String? = null
    private var sessionReady = false
    private var subscription: GroupCancelable? = null

    fun start() {
        if (subscription != null) return
        subscription = links.start(object : GroupLinkEventListener {
            override fun onEvent(event: GroupLinkEvent) {
                eventFilter(event)?.let(::receive)
            }
        })
    }

    fun stop() { subscription?.cancel(); subscription = null }

    fun restore() = readPending(object : GroupValueCallback {
        override fun complete(result: GroupValueResult) {
            val restored = (result as? GroupValueResult.Success)?.value ?: return
            pendingCode = restored
            mutableState.value = pendingState()
            attempt()
        }
    })

    fun setSessionReady(ready: Boolean) { sessionReady = ready; if (ready) attempt() }
    fun retry() { if (retryAfter(mutableState.value) == null) attempt() }
    fun logout() { sessionReady = false; clearPending(); mutableState.value = initialState() }
    fun discard() = clearPending()

    private fun receive(code: String) {
        pendingCode = code
        persist(code)
        mutableState.value = receivingState(mutableState.value)
        attempt()
    }

    private fun attempt() {
        val code = pendingCode ?: return
        if (!sessionReady || processing(mutableState.value) || retryAfter(mutableState.value) != null) return
        mutableState.value = processingState(mutableState.value, true)
        scope.launch {
            when (val result = resolve(code)) {
                is DeferredResolution.Success -> if (pendingCode == code) {
                    clearPending()
                    mutableState.value = resolvedState(result.value)
                    onResolved(result.value)
                } else {
                    retryLatest()
                }
                is DeferredResolution.Failure -> if (pendingCode == code) handleFailure(result.error) else retryLatest()
            }
        }
    }

    private fun retryLatest() { mutableState.value = processingState(mutableState.value, false); attempt() }
    private fun handleFailure(error: E) {
        val (next, clear) = failureState(mutableState.value, error)
        mutableState.value = next
        if (clear) clearPending()
    }
    private fun clearPending() {
        pendingCode = null
        persist(null)
        mutableState.value = clearedState(mutableState.value)
    }

    private fun persist(value: String?) = writePending(
        value,
        object : GroupResultCallback {
            override fun complete(result: GroupOperationResult) = Unit
        },
    )
}
