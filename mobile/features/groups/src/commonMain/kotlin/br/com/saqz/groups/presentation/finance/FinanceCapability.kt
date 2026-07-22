package br.com.saqz.groups.presentation.finance

import br.com.saqz.groups.data.finance.OrganizerFinanceGateway

sealed interface FinanceCapability {
    data object Athlete : FinanceCapability

    data class Organizer(val gateway: OrganizerFinanceGateway) : FinanceCapability
}
