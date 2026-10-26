package br.com.saqz.receivables.data

import br.com.saqz.network.NetworkError
import br.com.saqz.receivables.domain.ReceiptError

/** Erro de rede → erro do recebimento, para cadastro e gestão; leitura falha como rede, escrita como incerta. */
internal fun NetworkError.paymentError(writing: Boolean): ReceiptError = when (this) {
    is NetworkError.HttpStatus -> status.paymentError(writing)
    is NetworkError.ApiProblemError -> problem.status.paymentError(writing)
    NetworkError.Unavailable -> ReceiptError.SIGNED_OUT
    NetworkError.InvalidResponse, NetworkError.PayloadTooLarge -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.INVALID
    NetworkError.Timeout, NetworkError.Connectivity, NetworkError.Unknown -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.NETWORK
}

private fun Int.paymentError(writing: Boolean): ReceiptError = when (this) {
    401 -> ReceiptError.SIGNED_OUT
    403, 404 -> ReceiptError.DENIED
    409 -> ReceiptError.STALE
    400 -> ReceiptError.INVALID
    else -> if (writing) ReceiptError.UNCERTAIN else ReceiptError.UNAVAILABLE
}
