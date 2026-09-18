package br.com.saqz.groups.presentation.monthlygeneration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.newentry.formatEntryCents
import br.com.saqz.groups.presentation.newentry.parseEntryCents
import br.com.saqz.groups.presentation.newentry.parseEntryDate
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class MonthlyGenerationViewModel(
    private val groupId: String,
    private val saved: SavedStateHandle,
    private val groups: GroupGateway,
    private val athletes: AthleteGateway,
    private val finance: OrganizerFinanceGateway,
    private val now: GroupNowPort,
) : MviViewModel<MonthlyGenerationState, MonthlyGenerationIntent, MonthlyGenerationEffect>(MonthlyGenerationState()) {
    private var generation = 0
    private var completed = false
    private var requestId = saved.get<String>(KEY_REQUEST) ?: newRequestId()

    init { load() }

    override fun handleIntent(intent: MonthlyGenerationIntent) {
        if (state.value.isSaving || completed) return
        if (intent == MonthlyGenerationIntent.Retry) { load(); return }
        if (state.value.isLoading || state.value.loadFailed) return
        val form = state.value.form
        when (intent) {
            is MonthlyGenerationIntent.MonthChanged -> edit(form.copy(month = intent.value))
            is MonthlyGenerationIntent.AmountChanged -> edit(form.copy(amount = intent.value))
            is MonthlyGenerationIntent.DueDateChanged -> edit(form.copy(dueDate = intent.value))
            is MonthlyGenerationIntent.ToggleMember -> toggleMember(intent.id)
            MonthlyGenerationIntent.Review -> review()
            MonthlyGenerationIntent.Edit -> update { it.copy(reviewing = false, error = null) }
            MonthlyGenerationIntent.Confirm -> confirm()
            MonthlyGenerationIntent.Retry -> Unit
        }
    }

    private fun load() {
        val current = ++generation
        update { it.copy(isLoading = true, loadFailed = false, reviewing = false, error = null) }
        viewModelScope.launch {
            val groupResult = groups.read(GroupId(groupId))
            if (current != generation) return@launch
            val group = (groupResult as? SaqzResult.Success)?.value?.group
                ?: return@launch failLoad(GroupUiError.Network)
            if (group.role == GroupRole.ATHLETE) return@launch failLoad(GroupUiError.AccessDenied)
            val roster = athletes.roster(GroupId(groupId), AthleteRosterFilter(membershipType = AthleteMembershipType.MENSALISTA))
            if (current != generation) return@launch
            val members = (roster as? SaqzResult.Success)?.value
                ?.filter { it.active && it.membershipType == AthleteMembershipType.MENSALISTA }
                ?.map { MonthlyMemberUi(it.userId, it.displayName) }
                ?: return@launch failLoad(GroupUiError.Network)
            val restored = restoredForm() ?: defaults(group)
            val form = restored.copy(selectedIds = restored.selectedIds.intersect(members.map { it.id }.toSet()))
            if (form != restored) requestId = newRequestId()
            persist(form)
            update { MonthlyGenerationState(isLoading = false, members = members, form = form) }
        }
    }

    private fun defaults(group: Group): MonthlyForm {
        val date = now.now().toLocalDateTime(TimeZone.of(group.timeZone.id)).date
        val month = date.toString().take(MONTH_LENGTH)
        val day = group.financeDefaults?.monthlyDueDay ?: date.day
        val dueDate = (day.coerceIn(1, MAX_DAY) downTo 1).firstNotNullOf { candidate ->
            runCatching { LocalDate.parse("$month-${candidate.toString().padStart(2, '0')}") }.getOrNull()
        }
        return MonthlyForm(
            month = month,
            amount = group.financeDefaults?.monthlyFeeCents?.let(::formatEntryCents).orEmpty(),
            dueDate = dueDate.toString().split('-').reversed().joinToString("/"),
        )
    }

    private fun failLoad(error: GroupUiError) = update { it.copy(isLoading = false, loadFailed = true, error = error) }

    private fun toggleMember(id: String) {
        if (state.value.members.none { it.id == id }) return
        val form = state.value.form
        edit(form.copy(selectedIds = if (id in form.selectedIds) form.selectedIds - id else form.selectedIds + id))
    }

    private fun edit(form: MonthlyForm) {
        if (form != state.value.form) requestId = newRequestId()
        persist(form)
        update { it.copy(form = form, reviewing = false, error = null) }
    }

    private fun review() {
        val valid = state.value.form.command(requestId) != null
        update { it.copy(reviewing = valid, error = if (valid) null else GroupUiError.Validation) }
    }

    private fun confirm() {
        if (!state.value.reviewing) return
        val command = state.value.form.command(requestId) ?: return review()
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = finance.generateMonthly(GroupId(groupId), command)) {
                is SaqzResult.Success -> {
                    completed = true
                    listOf(KEY_MONTH, KEY_AMOUNT, KEY_DUE, KEY_MEMBERS).forEach { saved.remove<Any>(it) }
                    saved.remove<String>(KEY_REQUEST)
                    update { it.copy(isSaving = false) }
                    emit(MonthlyGenerationEffect.Generated)
                }
                is SaqzResult.Failure -> update { it.copy(isSaving = false, error = result.error.monthlyUiError()) }
            }
        }
    }

    private fun newRequestId(): String = Uuid.random().toString().also { saved[KEY_REQUEST] = it }

    private fun restoredForm(): MonthlyForm? = saved.get<String>(KEY_MONTH)?.let { month ->
        MonthlyForm(month, saved[KEY_AMOUNT] ?: "", saved[KEY_DUE] ?: "", saved.get<List<String>>(KEY_MEMBERS)?.toSet().orEmpty())
    }

    private fun persist(form: MonthlyForm) {
        saved[KEY_MONTH] = form.month
        saved[KEY_AMOUNT] = form.amount
        saved[KEY_DUE] = form.dueDate
        saved[KEY_MEMBERS] = form.selectedIds.toList()
    }
}

internal fun MonthlyForm.command(requestId: String): MonthlyChargeCommand? {
    val monthStart = month.takeIf { Regex("[0-9]{4}-[0-9]{2}").matches(it) }
        ?.let { runCatching { LocalDate.parse("$it-01") }.getOrNull() }
        ?.takeIf { it.year in 1..MAX_YEAR }
    val due = parseEntryDate(dueDate)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val sameMonth = due != null && due.year == monthStart?.year && due.month == monthStart?.month
    // Limit input before parsing: the shared parser multiplies reais by 100.
    val cents = amount.trim().takeIf { Regex("[0-9]{1,6}([,.][0-9]{1,2})?").matches(it) }
        ?.let(::parseEntryCents)?.takeIf { it in 1..MAX_CENTS }
    return if (sameMonth && cents != null && selectedIds.isNotEmpty()) {
        MonthlyChargeCommand(requestId, month, cents, due.toString(), selectedIds)
    } else null
}

private fun FinanceError.monthlyUiError(): GroupUiError = when (this) {
    is FinanceError.Validation -> GroupUiError.Validation
    FinanceError.Forbidden, FinanceError.Authentication -> GroupUiError.AccessDenied
    FinanceError.HiddenResource -> GroupUiError.NotFound
    FinanceError.Conflict -> GroupUiError.Conflict
    else -> GroupUiError.Network
}

private const val KEY_MONTH = "monthly-generation-month"
private const val KEY_AMOUNT = "monthly-generation-amount"
private const val KEY_DUE = "monthly-generation-due"
private const val KEY_MEMBERS = "monthly-generation-members"
private const val KEY_REQUEST = "monthly-generation-request"
private const val MONTH_LENGTH = 7
private const val MAX_DAY = 31
private const val MAX_YEAR = 9999
private const val MAX_CENTS = 99_999_999L
