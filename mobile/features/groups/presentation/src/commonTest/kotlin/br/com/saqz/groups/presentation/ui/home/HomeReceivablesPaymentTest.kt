package br.com.saqz.groups.presentation.ui.home

import br.com.saqz.groups.presentation.home.HomeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeReceivablesPaymentTest {
    /** Disponibilidade desligada: o aviso volta a ser o de hoje, mesmo com o grupo ativo no servidor. */
    @Test fun availabilityOffTurnsThePayButtonBackIntoTodaysNotice() {
        val charges = previewOwnCharges().let { it.copy(groups = it.groups.map { g -> g.copy(payInApp = true) }) }
        val state = HomeState(ownCharges = charges)
        assertTrue(state.withPayment(true).ownCharges!!.groups.all { it.payInApp })
        assertFalse(state.withPayment(false).ownCharges!!.groups.any { it.payInApp })
    }
}
