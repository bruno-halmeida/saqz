package br.com.saqz.groups.domain.finance

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import kotlin.jvm.JvmInline

enum class ChargeKind {
    Game,
    Monthly,
}

enum class ChargeStatus {
    Pending,
    Paid,
    Waived,
    Cancelled,
}

enum class ExpenseCategory {
    Venue,
    Equipment,
    Referee,
    Other,
}

enum class ExpenseStatus {
    Active,
    Voided,
}

enum class ExpenseAction {
    Created,
    Edited,
    Voided,
}

@JvmInline
value class FinanceVersionToken(val value: String)

data class ChargeAudit(
    val actorId: String,
    val oldStatus: ChargeStatus,
    val newStatus: ChargeStatus,
    val note: String? = null,
    val occurredAt: String,
)

data class Charge(
    val id: String,
    val groupId: GroupId,
    val memberId: String,
    val kind: ChargeKind,
    val gameId: String? = null,
    val month: String? = null,
    val amountCents: Long,
    val dueDate: String,
    val status: ChargeStatus,
    val reviewRequired: Boolean = false,
    val version: Long,
    val audit: List<ChargeAudit>,
)

data class ChargeTotals(
    val pendingCents: Long,
    val paidCents: Long,
    val waivedCents: Long,
    val cancelledCents: Long,
)

data class ChargeList(
    val charges: List<Charge>,
    val totals: ChargeTotals? = null,
)

data class VersionedCharge(
    val charge: Charge,
    val version: FinanceVersionToken,
)

data class MonthlyChargeCommand(
    val requestId: String,
    val month: String,
    val amountCents: Long,
    val dueDate: String,
    val memberIds: Set<String>,
)

data class ChargeStatusCommand(
    val status: ChargeStatus,
    val note: String? = null,
)

data class ExpenseAudit(
    val actorId: String,
    val action: ExpenseAction,
    val occurredAt: String,
)

data class Expense(
    val id: String,
    val groupId: GroupId,
    val description: String,
    val amountCents: Long,
    val expenseDate: String,
    val category: ExpenseCategory,
    val customCategory: String? = null,
    val notes: String? = null,
    val status: ExpenseStatus,
    val version: Long,
    val audit: List<ExpenseAudit>,
)

data class ExpenseList(
    val expenses: List<Expense>,
    val activeTotalCents: Long,
)

data class VersionedExpense(
    val expense: Expense,
    val version: FinanceVersionToken,
)

data class ExpenseWriteCommand(
    val requestId: String? = null,
    val description: String,
    val amountCents: Long,
    val expenseDate: String,
    val category: ExpenseCategory,
    val customCategory: String? = null,
    val notes: String? = null,
)

data class FinanceTotals(
    val pendingChargeCents: Long,
    val paidChargeCents: Long,
    val waivedChargeCents: Long,
    val cancelledChargeCents: Long,
    val activeExpenseCents: Long,
)

sealed interface FinanceError : SaqzError {
    data class Validation(val error: DataError.Validation) : FinanceError
    data object HiddenResource : FinanceError
    data object Forbidden : FinanceError
    data object Conflict : FinanceError
    data object PreconditionRequired : FinanceError
    data object InvalidLifecycle : FinanceError
    data object Authentication : FinanceError
    data class Data(val error: DataError) : FinanceError
}

interface AthleteFinanceGateway {
    suspend fun ownCharges(groupId: GroupId): SaqzResult<ChargeList, FinanceError>
}

interface OrganizerFinanceGateway {
    suspend fun charges(groupId: GroupId): SaqzResult<ChargeList, FinanceError>

    suspend fun generateMonthly(
        groupId: GroupId,
        command: MonthlyChargeCommand,
    ): SaqzResult<ChargeList, FinanceError>

    suspend fun updateChargeStatus(
        groupId: GroupId,
        chargeId: String,
        version: FinanceVersionToken,
        command: ChargeStatusCommand,
    ): SaqzResult<VersionedCharge, FinanceError>

    suspend fun expenses(groupId: GroupId): SaqzResult<ExpenseList, FinanceError>

    suspend fun createExpense(
        groupId: GroupId,
        command: ExpenseWriteCommand,
    ): SaqzResult<VersionedExpense, FinanceError>

    suspend fun editExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
        command: ExpenseWriteCommand,
    ): SaqzResult<VersionedExpense, FinanceError>

    suspend fun voidExpense(
        groupId: GroupId,
        expenseId: String,
        version: FinanceVersionToken,
    ): SaqzResult<VersionedExpense, FinanceError>

    suspend fun totals(groupId: GroupId): SaqzResult<FinanceTotals, FinanceError>
}
