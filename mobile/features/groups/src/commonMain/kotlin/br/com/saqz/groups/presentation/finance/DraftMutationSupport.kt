package br.com.saqz.groups.presentation.finance

import br.com.saqz.groups.data.finance.FinanceGatewayFailure
import br.com.saqz.groups.data.finance.OrganizerFinanceGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed interface FinanceMutationResult<out T> {
    data class Success<T>(val value: T) : FinanceMutationResult<T>

    data class Failure(val failure: FinanceGatewayFailure) : FinanceMutationResult<Nothing>
}

class DraftMutationSupport<S, D, O, E>(
    private val capability: FinanceCapability,
    private val scope: CoroutineScope,
    private val state: () -> S,
    private val setState: (S) -> Unit,
    private val canMutate: (S) -> Boolean,
    private val mutatingState: (S) -> S,
    private val failedState: (S, FinanceGatewayFailure, E) -> S,
    private val mapFailure: (FinanceGatewayFailure) -> E,
) {
    private var retry: O? = null

    val organizer: OrganizerFinanceGateway?
        get() = (capability as? FinanceCapability.Organizer)?.gateway

    fun restore(
        read: (onSuccess: (D?) -> Unit, onFailure: () -> Unit) -> Unit,
        valid: (D) -> Boolean,
        restored: (D) -> Unit,
        unavailable: (S) -> S,
    ) {
        if (organizer == null) return
        read(
            { draft -> draft?.takeIf(valid)?.let(restored) },
            { setState(unavailable(state())) },
        )
    }

    fun persist(
        draft: D,
        write: (D, onFailure: () -> Unit) -> Unit,
        unavailable: (S) -> S,
    ) {
        write(draft) { setState(unavailable(state())) }
    }

    fun retry(execute: (O) -> Unit) {
        retry?.let(execute)
    }

    fun <T> execute(
        operation: O,
        perform: suspend (OrganizerFinanceGateway) -> FinanceMutationResult<T>,
        succeeded: suspend (OrganizerFinanceGateway, T) -> Unit,
    ) {
        val current = state()
        if (!canMutate(current)) return
        val gateway = organizer ?: return fail(FinanceGatewayFailure.Forbidden)

        retry = operation
        setState(mutatingState(current))
        scope.launch {
            when (val result = perform(gateway)) {
                is FinanceMutationResult.Success -> {
                    retry = null
                    succeeded(gateway, result.value)
                }

                is FinanceMutationResult.Failure -> fail(result.failure)
            }
        }
    }

    fun fail(failure: FinanceGatewayFailure) {
        setState(failedState(state(), failure, mapFailure(failure)))
    }
}
