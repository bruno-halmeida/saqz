package br.com.saqz.receivables.domain

import br.com.saqz.domain.SaqzResult

data class ReceiptNotice(val id: String, val title: String, val message: String)
fun interface ReceiptNoticesGateway { suspend fun active(): SaqzResult<List<ReceiptNotice>, ReceiptError> }
