package br.com.saqz.groups.presentation.finance.charges

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.MembershipDto
import br.com.saqz.groups.data.finance.ChargeDto
import br.com.saqz.groups.data.finance.ChargeListDto
import br.com.saqz.groups.data.finance.ChargeStatusDto
import kotlinx.serialization.Serializable

@Serializable
data class MonthlyChargeDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val commandKey: String,
    val month: String = "",
    val amountBrl: String = "",
    val dueDate: String = "",
    val selectedMemberIds: Set<String> = emptySet(),
    val reviewed: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

sealed interface MonthlyDraftReadResult {
    data class Success(val draft: MonthlyChargeDraft?) : MonthlyDraftReadResult
    data object Failure : MonthlyDraftReadResult
}

sealed interface MonthlyDraftWriteResult {
    data object Success : MonthlyDraftWriteResult
    data object Failure : MonthlyDraftWriteResult
}

interface MonthlyChargeDraftStorePort {
    fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit)
    fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit)
    fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit)
}

fun interface FinanceCommandKeyFactory {
    fun create(): String
}

enum class FinanceError {
    UNAVAILABLE,
    DRAFT_UNAVAILABLE,
    VALIDATION,
    CONFLICT,
    HIDDEN,
    FORBIDDEN,
    INVALID_LIFECYCLE,
}

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

@Immutable
data class ChargeTotalsState(
    val pendingCents: Long,
    val paidCents: Long,
    val waivedCents: Long,
    val cancelledCents: Long,
)

sealed interface FinanceIntent {
    data object Refresh : FinanceIntent
    data class UpdateMonthly(
        val month: String,
        val amountBrl: String,
        val dueDate: String,
        val memberIds: Set<String>,
    ) : FinanceIntent

    data object ReviewMonthly : FinanceIntent
    data object GenerateMonthly : FinanceIntent
    data class UpdateStatus(
        val chargeId: String,
        val status: ChargeStatusDto,
        val note: String? = null,
    ) : FinanceIntent

    data object Retry : FinanceIntent
}

sealed interface FinanceEffect {
    data class MonthlyGenerated(val count: Int) : FinanceEffect
    data class StatusRecorded(val chargeId: String, val status: ChargeStatusDto) : FinanceEffect
}

internal fun ChargeListDto.totals(): ChargeTotalsState? {
    if (
        pendingTotalCents == null ||
        paidTotalCents == null ||
        waivedTotalCents == null ||
        cancelledTotalCents == null
    ) {
        return null
    }

    return ChargeTotalsState(
        pendingTotalCents,
        paidTotalCents,
        waivedTotalCents,
        cancelledTotalCents,
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
