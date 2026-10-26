package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.receivables.presentation.AthleteChargesRoute
import br.com.saqz.receivables.presentation.ReceivablePaymentRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentPushNavigationTest {
    private fun push(game: String? = null, month: String? = null) =
        GroupLinkEvent.NotificationOpen("ceret", channel = PAYMENT_CHANNEL, settleGameId = game, settleBillingMonth = month)

    @Test fun settleTimeOpensThePaymentOfThatGameOrMonth() {
        assertEquals(ReceivablePaymentRoute("ceret", "", gameId = "g1"), paymentPushDestination(push(game = "g1"), "ceret", true))
        assertEquals(ReceivablePaymentRoute("ceret", "", billingMonth = "2026-10"), paymentPushDestination(push(month = "2026-10"), "ceret", false))
    }

    @Test fun withoutSettleTheOrganizerGoesToTheCashboxAndTheAthleteToHisCharges() {
        assertEquals(FinanceRoute.GroupCashbox("ceret"), paymentPushDestination(push(), "ceret", administers = true))
        assertEquals(AthleteChargesRoute("ceret", ""), paymentPushDestination(push(), "ceret", administers = false))
    }
}
