package br.com.saqz.subscriptions.domain.googleplay

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription

sealed interface GooglePlaySubmissionError : SaqzError {
    /** 409 `GOOGLE_PLAY_PURCHASE_OWNED_BY_ANOTHER_ACCOUNT`: a assinatura pertence a outra conta Saqz. */
    data object OwnedByAnotherAccount : GooglePlaySubmissionError

    /** 422 `GOOGLE_PLAY_PURCHASE_INVALID`: reenviar não muda nada. */
    data object Invalid : GooglePlaySubmissionError

    /** Rede ou 5xx: a compra fica sem reconhecimento e é reenviada na próxima abertura. */
    data class Data(val error: DataError) : GooglePlaySubmissionError
}

/** Contrato em `docs/subscriptions/google-play.md`. O backend relê a compra no Google e a reconhece. */
interface GooglePlaySubscriptionGateway {
    /** `GET /subscriptions/app-store/account-token`: o mesmo UUID vale como `obfuscatedAccountId`. */
    suspend fun obfuscatedAccountId(): SaqzResult<String, GooglePlaySubmissionError>

    /** `POST /subscriptions/google-play/purchases`. Idempotente pelo token da compra. */
    suspend fun submitPurchase(productId: String, purchaseToken: String): SaqzResult<MySubscription, GooglePlaySubmissionError>
}
