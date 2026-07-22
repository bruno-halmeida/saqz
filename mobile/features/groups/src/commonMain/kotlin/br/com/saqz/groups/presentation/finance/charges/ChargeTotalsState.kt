package br.com.saqz.groups.presentation.finance.charges

import androidx.compose.runtime.Immutable

@Immutable
data class ChargeTotalsState(
    val pendingCents: Long,
    val paidCents: Long,
    val waivedCents: Long,
    val cancelledCents: Long,
)
