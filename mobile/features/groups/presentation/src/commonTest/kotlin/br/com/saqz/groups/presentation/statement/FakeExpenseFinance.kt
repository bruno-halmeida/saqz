package br.com.saqz.groups.presentation.statement

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseList
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.finance.VersionedExpense
import kotlinx.coroutines.CompletableDeferred

/** Só as rotas de lançamento importam ao extrato; as de cobrança não são usadas aqui. */
internal class FakeExpenseFinance(
    var stored: List<Expense> = emptyList(),
    var voidResult: SaqzResult<VersionedExpense, FinanceError>? = null,
    var readFailure: FinanceError? = null,
    var voidGate: CompletableDeferred<Unit>? = null,
) : OrganizerFinanceGateway {
    data class VoidCall(val expenseId: String, val version: FinanceVersionToken)

    val voids = mutableListOf<VoidCall>()
    var reads = 0

    override suspend fun expenses(groupId: GroupId): SaqzResult<ExpenseList, FinanceError> {
        reads++
        readFailure?.let { return SaqzResult.Failure(it) }
        return SaqzResult.Success(ExpenseList(stored, stored.sumOf { it.amountCents }))
    }

    override suspend fun voidExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
    ): SaqzResult<VersionedExpense, FinanceError> {
        voids += VoidCall(expenseId, version)
        voidGate?.await()
        val expense = stored.first { it.id == expenseId }
        return voidResult
            ?: SaqzResult.Success(VersionedExpense(expense.copy(status = ExpenseStatus.Voided), version))
    }

    override suspend fun charges(groupId: GroupId) = error("not used")
    override suspend fun generateMonthly(groupId: GroupId, command: MonthlyChargeCommand) = error("not used")
    override suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ) = error("not used")
    override suspend fun resolveChargeReview(groupId: GroupId, chargeId: String, version: FinanceVersionToken) =
        error("not used")
    override suspend fun createExpense(groupId: GroupId, command: ExpenseWriteCommand) = error("not used")
    override suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ) = error("not used")
    override suspend fun totals(groupId: GroupId) = error("not used")
}

internal fun storedExpense(id: String = "expense-1", version: Long = 3, status: ExpenseStatus = ExpenseStatus.Active) =
    Expense(
        id = id,
        groupId = GroupId("group-1"),
        description = "Aluguel da quadra",
        amountCents = 32_000L,
        expenseDate = "2026-08-03",
        category = ExpenseCategory.Venue,
        status = status,
        version = version,
        audit = emptyList(),
    )
