package br.com.saqz.subscriptions.domain.port

/**
 * Assinatura como o StoreKit a devolve: nome e preço já localizados pela App Store. O preço
 * exibido na tela de compra vem sempre daqui, nunca do backend — é a Apple quem cobra.
 */
data class AppStoreProduct(
    val id: String,
    val displayName: String,
    val displayPrice: String,
)

/** `jwsRepresentation` de uma transação e o id que o Swift usa para dar `finish()`. */
data class AppStoreSignedTransaction(
    val transactionId: String,
    val signedTransaction: String,
)

sealed interface AppStoreProductsResult {
    data class Loaded(val products: List<AppStoreProduct>) : AppStoreProductsResult
    data class Failed(val message: String) : AppStoreProductsResult
}

sealed interface AppStorePurchaseResult {
    data class Purchased(val transaction: AppStoreSignedTransaction) : AppStorePurchaseResult

    /** Ask to Buy ou SCA: a compra só termina depois, e chega por [AppStoreTransactionListener]. */
    data object Pending : AppStorePurchaseResult
    data object Cancelled : AppStorePurchaseResult
    data class Failed(val message: String) : AppStorePurchaseResult
}

sealed interface AppStoreTransactionsResult {
    data class Loaded(val transactions: List<AppStoreSignedTransaction>) : AppStoreTransactionsResult
    data class Failed(val message: String) : AppStoreTransactionsResult
}

fun interface AppStoreProductsCallback { fun onAppStoreProducts(result: AppStoreProductsResult) }

fun interface AppStorePurchaseCallback { fun onAppStorePurchase(result: AppStorePurchaseResult) }

fun interface AppStoreTransactionsCallback { fun onAppStoreTransactions(result: AppStoreTransactionsResult) }

fun interface AppStoreTransactionListener { fun onAppStoreTransactionUpdate(transaction: AppStoreSignedTransaction) }

fun interface AppStoreManagementCallback { fun onAppStoreManagementClosed() }

/**
 * StoreKit 2, implementado em Swift (`IOSAppStorePurchases`). Só o iOS entrega este port; no
 * Android ele não existe e a tela de compra cai no portão de assinatura de sempre.
 *
 * Os nomes levam `AppStore` de propósito: o framework exporta todos os ports juntos para o
 * Objective-C, e dois métodos com o mesmo nome e rótulos fazem o Kotlin/Native renomear o
 * seletor de um deles — o adapter Swift deixa de conformar ao protocolo sem aviso no Gradle.
 *
 * Toda chamada acontece na main thread: o adapter é `@MainActor`.
 */
interface AppStorePurchasesPort {
    fun canMakeAppStorePayments(): Boolean

    fun loadAppStoreProducts(productIds: List<String>, done: AppStoreProductsCallback)

    /** [appAccountToken] é o UUID que o backend devolve: prende a compra à conta do Saqz. */
    fun purchaseAppStoreProduct(productId: String, appAccountToken: String, done: AppStorePurchaseCallback)

    /** Só depois que o backend aceitou (ou recusou de vez) a transação. */
    fun finishAppStoreTransaction(transactionId: String)

    /** `Transaction.unfinished`: compras que ainda não chegaram ao backend. */
    fun readUnfinishedAppStoreTransactions(done: AppStoreTransactionsCallback)

    /** `AppStore.sync()` seguido de `Transaction.currentEntitlements`. */
    fun restoreAppStorePurchases(done: AppStoreTransactionsCallback)

    /**
     * `Transaction.updates` (renovação, Ask to Buy aprovado, troca de plano na App Store). O
     * Swift escuta desde a abertura do app e guarda o que chegar antes deste registro.
     */
    fun listenForAppStoreTransactions(listener: AppStoreTransactionListener)

    fun showAppStoreSubscriptionManagement(done: AppStoreManagementCallback)
}
