package br.com.saqz.subscriptions.presentation.myplan

import br.com.saqz.designsystem.UiText
import br.com.saqz.subscriptions.domain.subscription.BillingType
import br.com.saqz.subscriptions.domain.subscription.MySubscription
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card_brand_last4
import br.com.saqz.subscriptions.resources.myplan_billing_credit_card_last4

/**
 * VUL-196: "final 1234" + bandeira quando o backend devolver `cardLast4`/`cardBrand` — o
 * endpoint de leitura ainda não expõe nenhum dos dois, então os dois chegam nulos e cai
 * no rótulo genérico de [BillingType.toUiText] (binding pronto, sem quebrar hoje). Arquivo
 * próprio para não estourar o teto de funções por arquivo do `MyPlanMappers.kt`.
 */
internal fun MySubscription.paymentMethodLabel(): UiText? {
    val method = paymentMethod ?: return null
    val last4 = cardLast4
    if (method != BillingType.CreditCard || last4 == null) return method.toUiText()
    val brand = cardBrand
    return if (brand != null) {
        UiText.Res(Res.string.myplan_billing_credit_card_brand_last4, listOf(brand, last4))
    } else {
        UiText.Res(Res.string.myplan_billing_credit_card_last4, listOf(last4))
    }
}
