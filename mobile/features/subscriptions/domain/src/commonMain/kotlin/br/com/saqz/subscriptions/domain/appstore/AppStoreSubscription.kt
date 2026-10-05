package br.com.saqz.subscriptions.domain.appstore

import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.subscriptions.domain.subscription.MySubscription

sealed interface AppStoreSubmissionError : SaqzError {
    /** 409 `APP_STORE_TRANSACTION_OWNED_BY_ANOTHER_ACCOUNT`: o Apple ID já assina em outra conta Saqz. */
    data object OwnedByAnotherAccount : AppStoreSubmissionError

    /** 422 `APP_STORE_TRANSACTION_INVALID`: reenviar não muda nada. */
    data object Invalid : AppStoreSubmissionError

    /** Rede ou 5xx: a transação fica sem `finish()` e volta pelo StoreKit. */
    data class Data(val error: DataError) : AppStoreSubmissionError
}

/** Contrato em `docs/subscriptions/app-store.md`. O backend decide o acesso; o app só entrega. */
interface AppStoreSubscriptionGateway {
    /** `GET /subscriptions/app-store/account-token`: o `appAccountToken` de toda compra. */
    suspend fun appAccountToken(): SaqzResult<String, AppStoreSubmissionError>

    /** `POST /subscriptions/app-store/transactions`. Idempotente pelo id da transação. */
    suspend fun submitTransaction(signedTransaction: String): SaqzResult<MySubscription, AppStoreSubmissionError>
}
