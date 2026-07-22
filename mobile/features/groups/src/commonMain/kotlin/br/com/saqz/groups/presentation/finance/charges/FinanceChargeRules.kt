package br.com.saqz.groups.presentation.finance.charges

import br.com.saqz.groups.data.finance.ChargeListDto

internal fun ChargeListDto.toChargeTotalsState(): ChargeTotalsState? {
    if (
        pendingTotalCents == null ||
        paidTotalCents == null ||
        waivedTotalCents == null ||
        cancelledTotalCents == null
    ) {
        return null
    }

    return ChargeTotalsState(
        pendingCents = pendingTotalCents,
        paidCents = paidTotalCents,
        waivedCents = waivedTotalCents,
        cancelledCents = cancelledTotalCents,
    )
}

internal fun MonthlyChargeDraft.validate(): Map<String, List<String>> = buildMap {
    if (!month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) {
        put("month", listOf("is invalid"))
    }

    val amount = amountBrl.brlToCents()
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

internal fun String.brlToCents(): Long? {
    val clean = trim().replace("R$", "").trim()
    if (!clean.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))) return null

    val parts = clean.replace('.', ',').split(',')
    return parts[0].toLongOrNull()
        ?.times(100)
        ?.plus(parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0)
}
