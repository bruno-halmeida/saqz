package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.RedeemCheckoutLogin
import br.com.saqz.subscriptions.application.RedeemCheckoutLoginResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class CheckoutLoginRequest @JsonCreator constructor(
    @JsonProperty("token") val token: String?,
)

data class CheckoutLoginResponse(val customToken: String)

class CheckoutLoginTokenInvalidException : RuntimeException()

/**
 * Anonymous on purpose: the visitor has the e-mail, not a Firebase session yet.
 * The one-time code is exchanged here for a custom token minted in bootstrap.
 */
@RestController
class CheckoutLoginController(
    private val redeem: RedeemCheckoutLogin,
) {
    @PostMapping("/subscriptions/checkout-login")
    fun redeem(@RequestBody body: CheckoutLoginRequest): CheckoutLoginResponse =
        when (val result = redeem.execute(body.token.orEmpty())) {
            is RedeemCheckoutLoginResult.Success -> CheckoutLoginResponse(result.customToken)
            RedeemCheckoutLoginResult.Invalid -> throw CheckoutLoginTokenInvalidException()
        }
}
