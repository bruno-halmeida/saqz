package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.subscriptions.application.ListReceipts
import br.com.saqz.subscriptions.application.Receipt
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class ReceiptResponse(
    val asaasEventId: String,
    val asaasPaymentId: String?,
    val valueCents: Long?,
    val confirmedAt: Instant?,
    val processedAt: Instant,
)

data class ReceiptListResponse(
    val receipts: List<ReceiptResponse>,
)

@RestController
class ReceiptController(
    private val actors: SubscriptionActorResolver,
    private val listReceipts: ListReceipts,
) {
    @GetMapping("/subscriptions/me/receipts")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestParam(defaultValue = "1000") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ReceiptListResponse {
        val ownerUserId = actors.resolve(identity)
        return ReceiptListResponse(listReceipts.execute(ownerUserId, limit, offset).map { it.toResponse() })
    }

    private fun Receipt.toResponse() = ReceiptResponse(
        asaasEventId = asaasEventId,
        asaasPaymentId = asaasPaymentId,
        valueCents = valueCents,
        confirmedAt = confirmedAt,
        processedAt = processedAt,
    )
}
