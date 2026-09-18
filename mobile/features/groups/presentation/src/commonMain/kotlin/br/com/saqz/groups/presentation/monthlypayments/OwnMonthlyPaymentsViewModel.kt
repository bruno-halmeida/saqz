package br.com.saqz.groups.presentation.monthlypayments

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.finance.AthleteFinanceGateway
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.statement.toStatementDateLabel
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.own_charges_monthly
import br.com.saqz.groups.resources.own_charges_monthly_unknown
import br.com.saqz.groups.resources.own_charges_due_history
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

class OwnMonthlyPaymentsViewModel(
    private val athletes: AthleteGateway,
    private val finance: AthleteFinanceGateway,
) : MviViewModel<OwnMonthlyPaymentsState, OwnMonthlyPaymentsIntent, OwnMonthlyPaymentsEffect>(OwnMonthlyPaymentsState()) {
    private var generation = 0

    init { load() }

    override fun handleIntent(intent: OwnMonthlyPaymentsIntent) = when (intent) {
        OwnMonthlyPaymentsIntent.Retry -> load()
        is OwnMonthlyPaymentsIntent.OpenGroup -> emit(OwnMonthlyPaymentsEffect.OpenGroup(intent.groupId))
    }

    private fun load() {
        val request = ++generation
        update { OwnMonthlyPaymentsState() }
        viewModelScope.launch {
            val profile = athletes.ownProfile()
            if (request != generation) return@launch
            if (profile is SaqzResult.Failure) {
                update { it.copy(loading = false, error = profile.error.toUiError()) }
                return@launch
            }
            val own = (profile as SaqzResult.Success).value
            val groups = own.memberships.map { membership ->
                val result = finance.ownCharges(membership.groupId)
                if (request != generation) return@launch
                val charges = when (result) {
                    is SaqzResult.Failure -> OwnChargesUi(failed = true)
                    is SaqzResult.Success -> {
                        val monthly = result.value.charges.filter {
                            it.kind == ChargeKind.Monthly && it.memberId == own.userId && it.groupId == membership.groupId
                        }
                        OwnChargesUi(
                            pending = monthly.filter { it.status == ChargeStatus.Pending }.sortedBy { it.dueDate }.map { it.toUi() },
                            history = monthly.filterNot { it.status == ChargeStatus.Pending }
                                .sortedByDescending { it.dueDate }.map { it.toUi() },
                        )
                    }
                }
                MonthlyPaymentsGroupUi(membership.groupId.value, membership.groupName, charges)
            }
            if (request == generation) update { OwnMonthlyPaymentsState(loading = false, groups = groups) }
        }
    }

    private suspend fun Charge.toUi() = OwnChargeUi(
        id = id,
        title = month?.let { getString(Res.string.own_charges_monthly, it.split('-').reversed().joinToString("/")) }
            ?: getString(Res.string.own_charges_monthly_unknown),
        dueLabel = getString(Res.string.own_charges_due_history, dueDate.toStatementDateLabel()),
        amountLabel = formatBrl(amountCents),
        status = when (status) {
            ChargeStatus.Pending -> OwnChargeStatusUi.Pending
            ChargeStatus.Paid -> OwnChargeStatusUi.Paid
            ChargeStatus.Waived -> OwnChargeStatusUi.Waived
            ChargeStatus.Cancelled -> OwnChargeStatusUi.Cancelled
        },
    )
}
