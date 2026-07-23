package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.groups.domain.finance.ChargeStatus

sealed interface FinanceEffect {
    data class MonthlyGenerated(val count: Int) : FinanceEffect

    data class StatusRecorded(val chargeId: String, val status: ChargeStatus) : FinanceEffect
}
