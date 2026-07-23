package br.com.saqz.groups.presentation.finance.charges

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.GroupMembership

@Immutable
data class FinanceState(
    val groupId: String,
    val role: GroupRole,
    val charges: List<Charge> = emptyList(),
    val members: List<GroupMembership> = emptyList(),
    val totals: ChargeTotalsState? = null,
    val monthlyDraft: MonthlyChargeDraft? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val error: FinanceError? = null,
    val reloadAvailable: Boolean = false,
    val retryAvailable: Boolean = false,
    val lastManualOutcome: String? = null,
) {
    val organizer: Boolean
        get() = role == GroupRole.OWNER || role == GroupRole.ADMIN

    val manualTrackingNotice: String
        get() = "Controle manual: o app apenas registra cobranças e seus status."
}
