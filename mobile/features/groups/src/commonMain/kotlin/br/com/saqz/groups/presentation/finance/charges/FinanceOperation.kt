package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.FinanceVersionToken

internal sealed interface FinanceOperation {
    data class Monthly(val draft: MonthlyChargeDraft) : FinanceOperation
    data class Status(
        val chargeId: String,
        val version: FinanceVersionToken,
        val command: ChargeStatusCommand,
    ) : FinanceOperation
}
