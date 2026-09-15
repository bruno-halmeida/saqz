package br.com.saqz.composeapp.receivables

import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.MemberPaymentCommand
import br.com.saqz.receivables.domain.MemberPaymentOrder
import br.com.saqz.receivables.domain.MemberPaymentPage
import br.com.saqz.receivables.domain.MemberPaymentsGateway

internal class EmptyMemberPaymentsGateway : MemberPaymentsGateway {
    override suspend fun orders(after: String?) = SaqzResult.Success(MemberPaymentPage(emptyList(), null))
    override suspend fun detail(orderId: String) = error("No existing payments")
    override suspend fun instrument(order: MemberPaymentOrder, command: MemberPaymentCommand) = error("No existing payments")
    override suspend fun reconcile(orderId: String, requestId: String) = error("No existing payments")
}
