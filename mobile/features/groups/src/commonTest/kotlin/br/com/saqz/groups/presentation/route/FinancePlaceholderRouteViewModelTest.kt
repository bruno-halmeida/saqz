package br.com.saqz.groups.presentation.route

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

/**
 * Inertness/label matrix for [FinancePlaceholderRouteViewModel] (T15). Derived
 * from FINNAV-02, LIFE-01, LIFE-03, LIFE-05.
 */
class FinancePlaceholderRouteViewModelTest {

    @Test
    fun `FINANCE mode projects the organizer label`() {
        val viewModel = FinancePlaceholderRouteViewModel(FinancePlaceholderMode.FINANCE)

        assertEquals(FinancePlaceholderState(FinancePlaceholderMode.FINANCE), viewModel.state.value)
    }

    @Test
    fun `OWN_CHARGES mode projects the athlete label`() {
        val viewModel = FinancePlaceholderRouteViewModel(FinancePlaceholderMode.OWN_CHARGES)

        assertEquals(FinancePlaceholderState(FinancePlaceholderMode.OWN_CHARGES), viewModel.state.value)
    }

    @Test
    fun `each entry receives its own instance for the same mode`() {
        val first = FinancePlaceholderRouteViewModel(FinancePlaceholderMode.FINANCE)
        val second = FinancePlaceholderRouteViewModel(FinancePlaceholderMode.FINANCE)

        assertNotSame(first, second)
    }

    @Test
    fun `state never changes after construction since the placeholder is fully inert`() {
        val viewModel = FinancePlaceholderRouteViewModel(FinancePlaceholderMode.OWN_CHARGES)

        val first = viewModel.state.value
        val second = viewModel.state.value

        assertEquals(first, second)
    }
}
