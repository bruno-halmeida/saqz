package br.com.saqz.receivables.data

import br.com.saqz.domain.SaqzResult
import br.com.saqz.network.*
import br.com.saqz.receivables.domain.*
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KtorReceiptWalletGateway(private val network: AuthenticatedNetworkClient) : ReceiptWalletGateway {
    override suspend fun balance(accountId: String) = call("${path(accountId)}/wallet", WalletBalanceTransport.serializer()) {
        require(it.accountId == accountId && it.pendingReceivablesCents >= 0 && it.refreshedAt.isNotBlank())
        ReceiptWalletBalance(it.accountId, it.availableBalanceCents, it.pendingReceivablesCents, it.refreshedAt)
    }
    override suspend fun statement(accountId: String, cursor: String?) = call("${path(accountId)}/wallet/statement",
        WalletStatementTransport.serializer(), query = mapOf("limit" to "20") + cursor?.let { mapOf("cursor" to it) }.orEmpty()) {
        require(it.items.size <= 20 && it.items.map { e -> e.id }.distinct().size == it.items.size)
        require(it.nextCursor == null || it.items.isNotEmpty() && it.nextCursor != cursor)
        ReceiptWalletStatement(it.items.map { e ->
            require(e.id.isNotBlank() && e.kind.isNotBlank() && e.occurredOn.isNotBlank())
            ReceiptWalletEntry(e.id, e.kind, e.amountCents, e.balanceCents, e.occurredOn, e.description)
        }, it.nextCursor)
    }
    override suspend fun destinations(accountId: String) = call("${path(accountId)}/bank-destinations",
        ListSerializer(WalletBankTransport.serializer())) {
        require(it.map { d -> d.id }.distinct().size == it.size); it.map(WalletBankTransport::domain)
    }
    override suspend fun saveDestination(accountId: String, requestId: String, details: ReceiptBankDetails):
        SaqzResult<ReceiptBankDestination, WalletError> {
        if (!details.valid() || requestId.isBlank()) return SaqzResult.Failure(WalletError.INVALID)
        return call("${path(accountId)}/bank-destinations", WalletBankTransport.serializer(), requestId,
            Json.encodeToString(WalletBankCommand(requestId, details.bankCode, details.accountType, details.ownerName,
                details.cpfCnpj, details.agency, details.account, details.accountDigit)), map = WalletBankTransport::domain)
    }
    override suspend fun recoverDestination(accountId: String, requestId: String) =
        call("${path(accountId)}/bank-destinations/by-request/${requestId.encodeURLPathPart()}",
            WalletBankTransport.serializer(), map = WalletBankTransport::domain)

    override suspend fun withdraw(accountId: String, requestId: String, destinationId: String, amountCents: Long):
        SaqzResult<ReceiptWithdrawal, WalletError> {
        if (requestId.isBlank() || destinationId.isBlank() || amountCents <= 0) return SaqzResult.Failure(WalletError.INVALID)
        return call("${path(accountId)}/withdrawals", WalletWithdrawalTransport.serializer(), requestId,
            Json.encodeToString(WalletWithdrawalCommand(requestId, destinationId, amountCents, true))) {
            require(it.destinationId == destinationId && it.amountCents == amountCents); it.domain(requestId)
        }
    }
    override suspend fun recoverWithdrawal(accountId: String, requestId: String) =
        call("${path(accountId)}/withdrawals/by-request/${requestId.encodeURLPathPart()}",
            WalletWithdrawalTransport.serializer()) { it.domain(requestId) }

    private suspend fun <T, R> call(path: String, serializer: KSerializer<T>, requestId: String? = null,
        body: String? = null, query: Map<String, String> = emptyMap(), map: (T) -> R): SaqzResult<R, WalletError> {
        val writing = requestId != null
        // A financial write is submitted once. Recovery uses the recorded requestId through GET.
        val response = network.execute(if (writing) HttpMethod.Post else HttpMethod.Get, path,
            EnvelopeTransport.serializer(serializer), NetworkRequest(body = body, query = query))
        return when (response) {
            is NetworkResult.Failure -> SaqzResult.Failure(response.error.walletError(writing))
            is NetworkResult.Success -> {
                val e = response.value
                val mismatchedRequest = e.requestId.isBlank() || writing && e.requestId != requestId
                if (e.error != null || e.value == null || mismatchedRequest) {
                    SaqzResult.Failure(if (writing || e.error == "RESULT_PENDING") WalletError.UNCERTAIN else WalletError.INVALID)
                } else try { SaqzResult.Success(map(e.value)) }
                catch (_: IllegalArgumentException) { SaqzResult.Failure(if (writing) WalletError.UNCERTAIN else WalletError.INVALID) }
            }
        }
    }
    private fun path(accountId: String) = "api/receivables/accounts/${accountId.encodeURLPathPart()}"
}
private fun NetworkError.walletError(writing: Boolean): WalletError = when (this) {
    is NetworkError.HttpStatus -> status.walletError(writing)
    is NetworkError.ApiProblemError -> problem.status.walletError(writing)
    NetworkError.Unavailable -> WalletError.SIGNED_OUT
    NetworkError.InvalidResponse, NetworkError.PayloadTooLarge -> if (writing) WalletError.UNCERTAIN else WalletError.INVALID
    else -> if (writing) WalletError.UNCERTAIN else WalletError.NETWORK
}
private fun Int.walletError(writing: Boolean) = when (this) {
    400 -> WalletError.INVALID
    401 -> WalletError.SIGNED_OUT
    403 -> WalletError.RECENT_AUTHENTICATION
    404 -> WalletError.DENIED
    409 -> WalletError.CONFLICT
    422 -> WalletError.INSUFFICIENT_BALANCE
    else -> if (writing) WalletError.UNCERTAIN else WalletError.UNAVAILABLE
}
