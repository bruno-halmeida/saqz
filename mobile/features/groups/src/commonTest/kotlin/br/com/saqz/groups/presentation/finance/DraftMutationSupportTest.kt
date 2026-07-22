package br.com.saqz.groups.presentation.finance

import br.com.saqz.groups.data.finance.FinanceGatewayFailure
import br.com.saqz.groups.presentation.finance.charges.FinanceError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DraftMutationSupportTest {
    @Test
    fun `athlete capability maps a mutation attempt to forbidden without a gateway`() = runTest {
        var state: FinanceError? = null
        val support = DraftMutationSupport<FinanceError?, Unit, String, FinanceError>(
            capability = FinanceCapability.Athlete,
            scope = this,
            state = { state },
            setState = { state = it },
            canMutate = { true },
            mutatingState = { it },
            failedState = { _, _, error -> error },
            mapFailure = { FinanceError.FORBIDDEN },
        )

        support.execute<Unit>(
            operation = "generate",
            perform = { error("athlete must not receive an organizer gateway") },
            succeeded = { _, _ -> error("athlete mutation must not succeed") },
        )

        assertEquals(FinanceError.FORBIDDEN, state)
    }
}
