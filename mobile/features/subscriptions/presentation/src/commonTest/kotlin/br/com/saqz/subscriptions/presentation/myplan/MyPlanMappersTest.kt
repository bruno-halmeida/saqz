package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.domain.subscription.Plan
import br.com.saqz.subscriptions.domain.subscription.SubscriptionCycle
import br.com.saqz.subscriptions.domain.subscription.SubscriptionStatus
import br.com.saqz.subscriptions.domain.subscription.SubscriptionUsage
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card_brand_last4
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card_last4
import br.com.saqz.subscriptions.resources.myplan_billing_pix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * VUL-196: o endpoint de leitura ainda não expõe `cardLast4`/`cardBrand` — os três cenários
 * cobrem o binding pronto (nenhum, só o final, final + bandeira) sem quebrar o rótulo de hoje.
 */
class MyPlanMappersTest {
    @Test
    fun `pix has no payment method last four`() = assertEquals(
        UiText.Res(Res.string.myplan_billing_pix),
        subscription(BillingType.Pix).paymentMethodLabel(),
    )

    @Test
    fun `card without last four falls back to the generic label`() = assertEquals(
        UiText.Res(Res.string.myplan_billing_credit_card),
        subscription(BillingType.CreditCard).paymentMethodLabel(),
    )

    @Test
    fun `card with last four but no brand shows the digits`() = assertEquals(
        UiText.Res(Res.string.myplan_billing_credit_card_last4, listOf("1234")),
        subscription(BillingType.CreditCard, cardLast4 = "1234").paymentMethodLabel(),
    )

    @Test
    fun `card with brand and last four shows both`() = assertEquals(
        UiText.Res(Res.string.myplan_billing_credit_card_brand_last4, listOf("Mastercard", "1234")),
        subscription(BillingType.CreditCard, cardLast4 = "1234", cardBrand = "Mastercard").paymentMethodLabel(),
    )

    @Test
    fun `no payment method yet is null`() = assertNull(subscription(paymentMethod = null).paymentMethodLabel())

    private fun subscription(
        paymentMethod: BillingType? = BillingType.Pix,
        cardLast4: String? = null,
        cardBrand: String? = null,
    ) = MySubscription(
        status = SubscriptionStatus.Active,
        entitled = true,
        plan = Plan.Organizador,
        cycle = SubscriptionCycle.Monthly,
        pendingPlan = null,
        pendingPlanEffectiveAt = null,
        currentPeriodEnd = "2026-08-30T00:00:00Z",
        paymentMethod = paymentMethod,
        usage = SubscriptionUsage(groupsUsed = 2, groupsLimit = 3),
        readOnly = false,
        pastDueSince = null,
        canceledAt = null,
        cardLast4 = cardLast4,
        cardBrand = cardBrand,
    )
}
