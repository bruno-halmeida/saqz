package br.com.saqz.composeapp.navigation

import br.com.saqz.groups.port.GroupLinkEvent
import br.com.saqz.groups.presentation.navigation.FinanceRoute
import br.com.saqz.groups.presentation.navigation.GroupsRoute
import br.com.saqz.receivables.presentation.AthleteChargesRoute
import br.com.saqz.receivables.presentation.AutopayRoute
import br.com.saqz.receivables.presentation.FirstReviewRoute
import br.com.saqz.receivables.presentation.ReceivablePaymentRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentPushNavigationTest {
    private fun push(game: String? = null, month: String? = null, kind: String? = null) =
        GroupLinkEvent.NotificationOpen("ceret", channel = PAYMENT_CHANNEL, settleGameId = game, settleBillingMonth = month,
            paymentKind = kind)

    @Test fun settleTimeOpensThePaymentOfThatGameOrMonth() {
        assertEquals(ReceivablePaymentRoute("ceret", "", gameId = "g1"), paymentPushDestination(push(game = "g1"), "ceret", true))
        assertEquals(ReceivablePaymentRoute("ceret", "", billingMonth = "2026-10"), paymentPushDestination(push(month = "2026-10"), "ceret", false))
    }

    @Test fun withoutSettleTheOrganizerGoesToTheCashboxAndTheAthleteToHisCharges() {
        assertEquals(FinanceRoute.GroupCashbox("ceret"), paymentPushDestination(push(), "ceret", administers = true))
        assertEquals(AthleteChargesRoute("ceret", ""), paymentPushDestination(push(), "ceret", administers = false))
    }

    /** Rodada 3: o tipo do aviso decide o destino. */
    @Test fun eachPaymentKindOpensItsScreen() {
        assertEquals(FirstReviewRoute("ceret"), paymentPushDestination(push(kind = "REVIEW"), "ceret", true))
        assertEquals(FinanceRoute.GroupCashbox("ceret"), paymentPushDestination(push(kind = "PAID"), "ceret", true))
        assertEquals(GroupsRoute.Members("ceret"), paymentPushDestination(push(kind = "AUTOPAY_CANCELLED"), "ceret", true))
        assertEquals(AthleteChargesRoute("ceret", ""), paymentPushDestination(push(kind = "CONFIRMED"), "ceret", true))
        assertEquals(AutopayRoute("ceret", ""), paymentPushDestination(push(month = "2026-10", kind = "PRICE_CHANGE"), "ceret", false))
        assertEquals(ReceivablePaymentRoute("ceret", "", billingMonth = "2026-09", offerCardUpdate = true),
            paymentPushDestination(push(month = "2026-09", kind = "REFUSED"), "ceret", false))
        assertEquals(GroupsRoute.Members("ceret"), paymentPushDestination(push(kind = "REFUSED"), "ceret", true))
        assertEquals(ReceivablePaymentRoute("ceret", "", gameId = "g1"), paymentPushDestination(push(game = "g1", kind = "SETTLE"), "ceret", false))
    }
}
