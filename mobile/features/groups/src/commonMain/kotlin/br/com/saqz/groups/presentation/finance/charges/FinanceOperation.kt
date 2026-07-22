package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.groups.data.finance.ChargeStatusCommandDto

internal sealed interface FinanceOperation {
    data class Monthly(val draft: MonthlyChargeDraft) : FinanceOperation
    data class Status(
        val chargeId: String,
        val etag: String,
        val command: ChargeStatusCommandDto,
    ) : FinanceOperation
}
