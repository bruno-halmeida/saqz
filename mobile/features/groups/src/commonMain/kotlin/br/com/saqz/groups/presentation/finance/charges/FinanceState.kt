package br.com.saqz.groups.presentation.finance.charges

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.data.finance.ChargeDto

@Immutable
data class FinanceState(
    val groupId: String,
    val role: GroupRoleDto,
    val charges: List<ChargeDto> = emptyList(),
    val members: List<MembershipDto> = emptyList(),
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
        get() = role == GroupRoleDto.OWNER || role == GroupRoleDto.ADMIN

    val manualTrackingNotice: String
        get() = "Controle manual: o app apenas registra cobranças e seus status."
}
