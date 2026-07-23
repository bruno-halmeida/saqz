package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.groups.domain.finance.ChargeList

internal fun ChargeList.toChargeTotalsState(): ChargeTotalsState? {
    val value = totals ?: return null

    return ChargeTotalsState(
        pendingCents = value.pendingCents,
        paidCents = value.paidCents,
        waivedCents = value.waivedCents,
        cancelledCents = value.cancelledCents,
    )
}

internal fun MonthlyChargeDraft.validate(): Map<String, List<String>> = buildMap {
    if (!month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
        put("month", listOf("is invalid"))
    }

    val amount = parseBrlToCents(amountBrl)
    if (amount == null || amount !in 1..99_999_999) {
        put("amountBrl", listOf("is invalid"))
    }

    if (!dueDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
        put("dueDate", listOf("is invalid"))
    }

    if (selectedMemberIds.isEmpty()) {
        put("memberIds", listOf("is required"))
    }
}
