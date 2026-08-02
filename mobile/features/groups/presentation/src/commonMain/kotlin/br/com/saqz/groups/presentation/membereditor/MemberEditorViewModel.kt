package br.com.saqz.groups.presentation.membereditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.Athlete
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class MemberEditorViewModel(
    private val groupId: String,
    private val userId: String,
    private val savedState: SavedStateHandle,
    private val athleteGateway: AthleteGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val groupGateway: GroupGateway,
) : MviViewModel<MemberEditorState, MemberEditorIntent, MemberEditorEffect>(
    MemberEditorState().restoreDraft(savedState),
) {
    private var generation = 0
    private var member: AthleteRosterEntry? = null

    init {
        load()
    }

    override fun onIntent(intent: MemberEditorIntent) {
        when (intent) {
            MemberEditorIntent.Retry -> load()
            is MemberEditorIntent.NicknameChanged -> {
                savedState[KEY_NICKNAME] = intent.value
                update { it.copy(nickname = intent.value, error = null) }
            }
            is MemberEditorIntent.PositionSelected -> {
                val secondary = state.value.secondaryPosition.takeUnless { it == intent.value }
                savedState[KEY_POSITION] = intent.value?.name
                savedState[KEY_SECONDARY_POSITION] = secondary?.name
                update { it.copy(position = intent.value, secondaryPosition = secondary, error = null) }
            }
            is MemberEditorIntent.SecondaryPositionSelected -> {
                savedState[KEY_SECONDARY_POSITION] = intent.value?.name
                update { it.copy(secondaryPosition = intent.value, error = null) }
            }
            is MemberEditorIntent.LevelSelected -> {
                savedState[KEY_LEVEL] = intent.value?.name
                update { it.copy(level = intent.value, error = null) }
            }
            is MemberEditorIntent.PreferredSideSelected -> {
                savedState[KEY_PREFERRED_SIDE] = intent.value?.name
                update { it.copy(preferredSide = intent.value, error = null) }
            }
            is MemberEditorIntent.HeightChanged -> {
                savedState[KEY_HEIGHT] = intent.value
                update { it.copy(heightText = intent.value, heightCm = intent.value.toIntOrNull(), error = null) }
            }
            is MemberEditorIntent.MembershipSelected -> selectMembership(intent.value)
            MemberEditorIntent.OpenBilling -> openBilling()
            MemberEditorIntent.DismissBilling -> update { it.copy(billingSheetOpen = false) }
            is MemberEditorIntent.BillingAmountChanged -> {
                savedState[KEY_BILLING_AMOUNT] = intent.value
                update { it.copy(billingAmountText = intent.value, error = null) }
            }
            is MemberEditorIntent.BillingDueDaySelected -> {
                savedState[KEY_BILLING_DUE_DAY] = intent.value
                update { it.copy(billingDueDay = intent.value, error = null) }
            }
            MemberEditorIntent.SaveBilling -> saveBilling()
            is MemberEditorIntent.AdminChanged -> changeRole(intent.value)
            MemberEditorIntent.Save -> save()
            MemberEditorIntent.OpenRemove -> update { it.copy(removeSheetOpen = true) }
            MemberEditorIntent.DismissRemove -> update { it.copy(removeSheetOpen = false) }
            MemberEditorIntent.ConfirmRemove -> remove()
        }
    }

    private fun load() {
        val requestGeneration = nextGeneration()
        update { it.copy(isLoading = true, loadFailed = false, error = null, operation = null) }
        viewModelScope.launch {
            val group = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value.group
            }
            if (requestGeneration != generation) return@launch

            val roster = when (val result = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter(includeInactive = true))) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value
            }
            val entry = roster.firstOrNull { it.userId == userId }
                ?: return@launch showLoadFailure(requestGeneration, GroupUiError.NotFound)
            if (requestGeneration != generation) return@launch

            val stats = when (val result = athleteGateway.stats(GroupId(groupId), userId)) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value
            }
            if (requestGeneration != generation) return@launch

            val role = when (val result = membershipGateway.listMemberships(GroupId(groupId))) {
                is SaqzResult.Failure -> return@launch showLoadFailure(requestGeneration, result.error.toUiError())
                is SaqzResult.Success -> result.value.firstOrNull { it.userId == userId }?.role ?: GroupRole.ATHLETE
            }
            if (requestGeneration != generation) return@launch

            member = entry
            val defaultFee = group.financeDefaults?.monthlyFeeCents
            val defaultDueDay = group.financeDefaults?.monthlyDueDay
            val currentFee = entry.monthlyFeeCents ?: defaultFee
            val currentDueDay = entry.monthlyDueDay ?: defaultDueDay ?: DEFAULT_DUE_DAY
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    name = entry.nickname?.takeIf(String::isNotBlank) ?: entry.displayName,
                    displayName = entry.displayName,
                    nickname = entry.nickname.orEmpty(),
                    joinedAtLabel = joinedAtMonth(entry.joinedAt),
                    games = stats.games,
                    attendanceRate = stats.attendanceRate,
                    absences = stats.absences,
                    modality = group.profile?.modality,
                    composition = group.profile?.composition,
                    position = entry.position,
                    secondaryPosition = entry.secondaryPosition,
                    level = entry.level,
                    preferredSide = entry.preferredSide,
                    heightCm = entry.heightCm,
                    heightText = entry.heightCm?.toString().orEmpty(),
                    membershipType = entry.membershipType,
                    active = entry.active,
                    monthlyFeeOverrideCents = entry.monthlyFeeCents,
                    monthlyDueDayOverride = entry.monthlyDueDay,
                    defaultMonthlyFeeCents = defaultFee,
                    defaultMonthlyDueDay = defaultDueDay,
                    billingAmountText = formatCents(currentFee),
                    billingDueDay = currentDueDay,
                    role = role,
                ).restoreDraft(savedState)
            }
        }
    }

    private fun selectMembership(value: AthleteMembershipType) {
        if (state.value.operation != null) return
        savedState[KEY_MEMBERSHIP] = value.name
        update { it.copy(membershipType = value, error = null) }
        if (value == AthleteMembershipType.MENSALISTA) openBilling()
    }

    private fun openBilling() {
        val current = state.value
        if (current.operation != null) return
        update {
            it.copy(
                billingSheetOpen = true,
                billingAmountText = current.billingAmountText.ifBlank { formatCents(current.effectiveMonthlyFeeCents) },
                billingDueDay = if (current.billingAmountText.isBlank()) {
                    current.effectiveMonthlyDueDay ?: DEFAULT_DUE_DAY
                } else {
                    current.billingDueDay
                },
            )
        }
    }

    private fun saveBilling() {
        val current = state.value
        val amount = parseCents(current.billingAmountText)
        if (amount == null || amount <= 0L) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }
        val persisted = member ?: return
        patch(
            operation = MemberEditorOperation.Billing,
            command = persisted.toBillingUpdateCommand(
                membershipType = AthleteMembershipType.MENSALISTA,
                monthlyFeeCents = amount,
                monthlyDueDay = current.billingDueDay,
            ),
        )
    }

    private fun save() {
        val current = state.value
        if (current.heightText.isNotBlank() && current.heightCm == null) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }
        patch(MemberEditorOperation.Save, current.toUpdateCommand())
    }

    private fun patch(operation: MemberEditorOperation, command: UpdateAthleteCommand) {
        if (state.value.operation != null) return
        val requestGeneration = nextGeneration()
        update { it.copy(operation = operation, error = null) }
        viewModelScope.launch {
            when (val result = athleteGateway.updateAthlete(command)) {
                is SaqzResult.Failure -> {
                    if (requestGeneration != generation) return@launch
                    update { it.copy(operation = null, error = result.error.toUiError()) }
                }
                is SaqzResult.Success -> {
                    if (requestGeneration != generation) return@launch
                    applyAthlete(result.value, operation)
                }
            }
        }
    }

    private fun applyAthlete(athlete: Athlete, operation: MemberEditorOperation) {
        member = member?.copy(
            nickname = athlete.nickname,
            position = athlete.position,
            secondaryPosition = athlete.secondaryPosition,
            level = athlete.level,
            preferredSide = athlete.preferredSide,
            heightCm = athlete.heightCm,
            membershipType = athlete.membershipType,
            active = athlete.active,
            monthlyFeeCents = athlete.monthlyFeeCents,
            monthlyDueDay = athlete.monthlyDueDay,
        )
        update {
            if (operation == MemberEditorOperation.Billing) {
                it.copy(
                    operation = null,
                    error = null,
                    membershipType = athlete.membershipType,
                    monthlyFeeOverrideCents = athlete.monthlyFeeCents,
                    monthlyDueDayOverride = athlete.monthlyDueDay,
                    billingSheetOpen = false,
                )
            } else {
                it.copy(
                    operation = null,
                    error = null,
                    name = athlete.nickname?.takeIf(String::isNotBlank) ?: it.displayName,
                    nickname = athlete.nickname.orEmpty(),
                    position = athlete.position,
                    secondaryPosition = athlete.secondaryPosition,
                    level = athlete.level,
                    preferredSide = athlete.preferredSide,
                    heightCm = athlete.heightCm,
                    heightText = athlete.heightCm?.toString().orEmpty(),
                    membershipType = athlete.membershipType,
                    active = athlete.active,
                    monthlyFeeOverrideCents = athlete.monthlyFeeCents,
                    monthlyDueDayOverride = athlete.monthlyDueDay,
                    billingSheetOpen = it.billingSheetOpen,
                )
            }
        }
        if (operation == MemberEditorOperation.Save) emit(MemberEditorEffect.Close)
        if (operation == MemberEditorOperation.Save) clearDraft()
    }

    private fun changeRole(admin: Boolean) {
        val current = state.value
        if (current.isOwner || current.operation != null) return
        val requestGeneration = nextGeneration()
        update { it.copy(operation = MemberEditorOperation.Role, error = null) }
        viewModelScope.launch {
            val requestedRole = if (admin) AssignableGroupRole.ADMIN else AssignableGroupRole.ATHLETE
            when (val result = membershipGateway.changeRole(
                ChangeMembershipRoleCommand(GroupId(groupId), userId, requestedRole),
            )) {
                is SaqzResult.Failure -> {
                    if (requestGeneration != generation) return@launch
                    update { it.copy(operation = null, error = result.error.toUiError()) }
                }
                is SaqzResult.Success -> {
                    if (requestGeneration != generation) return@launch
                    update { it.copy(operation = null, role = result.value.role, error = null) }
                }
            }
        }
    }

    private fun remove() {
        if (state.value.operation != null) return
        val requestGeneration = nextGeneration()
        update { it.copy(operation = MemberEditorOperation.Remove, error = null) }
        viewModelScope.launch {
            when (val result = athleteGateway.removeAthlete(GroupId(groupId), userId)) {
                is SaqzResult.Failure -> {
                    if (requestGeneration != generation) return@launch
                    update { it.copy(operation = null, error = result.error.toUiError()) }
                }
                is SaqzResult.Success -> {
                    if (requestGeneration != generation) return@launch
                    update { it.copy(operation = null, removeSheetOpen = false, error = null) }
                    emit(MemberEditorEffect.Removed)
                }
            }
        }
    }

    private fun showLoadFailure(requestGeneration: Int, error: GroupUiError) {
        if (requestGeneration != generation) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun nextGeneration(): Int {
        generation += 1
        return generation
    }

    private fun MemberEditorState.toUpdateCommand(
        membershipType: AthleteMembershipType = this.membershipType,
        monthlyFeeCents: Long? = monthlyFeeOverrideCents,
        monthlyDueDay: Int? = monthlyDueDayOverride,
    ) = UpdateAthleteCommand(
        groupId = GroupId(groupId),
        userId = this@MemberEditorViewModel.userId,
        position = position,
        membershipType = membershipType,
        active = active,
        nickname = nickname.trim().takeIf(String::isNotEmpty),
        secondaryPosition = secondaryPosition.takeUnless { it == position },
        level = level,
        preferredSide = preferredSide,
        heightCm = heightText.toIntOrNull(),
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )

    private fun AthleteRosterEntry.toBillingUpdateCommand(
        membershipType: AthleteMembershipType,
        monthlyFeeCents: Long,
        monthlyDueDay: Int,
    ) = UpdateAthleteCommand(
        groupId = GroupId(groupId),
        userId = userId,
        position = position,
        membershipType = membershipType,
        active = active,
        nickname = nickname,
        secondaryPosition = secondaryPosition,
        level = level,
        preferredSide = preferredSide,
        heightCm = heightCm,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )

    private fun joinedAtMonth(joinedAt: String): String {
        val month = runCatching {
            Instant.parse(joinedAt).toLocalDateTime(TimeZone.UTC).month.ordinal + 1
        }.getOrNull() ?: return ""
        return MONTH_NAMES[month - 1]
    }

    private fun formatCents(cents: Long?): String {
        if (cents == null) return ""
        val reais = cents / 100
        val centavos = (cents % 100).toString().padStart(2, '0')
        return "$reais,$centavos"
    }

    private fun parseCents(value: String): Long? {
        val normalized = value.trim().removePrefix("R$").replace(" ", "")
        if (normalized.isEmpty()) return null
        val parts = normalized.replace('.', ',').split(',')
        if (parts.size > 2 || parts.any { it.isEmpty() }) return null
        val reais = parts[0].toLongOrNull() ?: return null
        val centavos = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0
        return reais * 100 + centavos
    }

    private fun clearDraft() {
        listOf(
            KEY_NICKNAME,
            KEY_POSITION,
            KEY_SECONDARY_POSITION,
            KEY_LEVEL,
            KEY_PREFERRED_SIDE,
            KEY_HEIGHT,
            KEY_MEMBERSHIP,
            KEY_BILLING_AMOUNT,
            KEY_BILLING_DUE_DAY,
        ).forEach { key -> savedState.remove<Any>(key) }
    }

    private companion object {
        val MONTH_NAMES = listOf(
            "janeiro", "fevereiro", "março", "abril", "maio", "junho",
            "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
        )
        const val DEFAULT_DUE_DAY = 10
    }
}

private fun MemberEditorState.restoreDraft(savedState: SavedStateHandle): MemberEditorState {
    val restoredHeight = savedState.get<String>(KEY_HEIGHT)
    val restoredMembership = savedState.get<String>(KEY_MEMBERSHIP).toEnumOrNull<AthleteMembershipType>()
    return copy(
        nickname = savedState.get<String>(KEY_NICKNAME) ?: nickname,
        position = savedState.restoreNullableEnum(KEY_POSITION, position),
        secondaryPosition = savedState.restoreNullableEnum(KEY_SECONDARY_POSITION, secondaryPosition),
        level = savedState.restoreNullableEnum(KEY_LEVEL, level),
        preferredSide = savedState.restoreNullableEnum(KEY_PREFERRED_SIDE, preferredSide),
        heightText = restoredHeight ?: heightText,
        heightCm = restoredHeight?.toIntOrNull() ?: if (savedState.contains(KEY_HEIGHT)) null else heightCm,
        membershipType = if (savedState.contains(KEY_MEMBERSHIP)) restoredMembership ?: membershipType else membershipType,
        billingAmountText = savedState.get<String>(KEY_BILLING_AMOUNT) ?: billingAmountText,
        billingDueDay = savedState.get<Int>(KEY_BILLING_DUE_DAY) ?: billingDueDay,
    )
}

private inline fun <reified T : Enum<T>> SavedStateHandle.restoreNullableEnum(
    key: String,
    fallback: T?,
): T? = if (contains(key)) get<String>(key).toEnumOrNull() else fallback

private inline fun <reified T : Enum<T>> String?.toEnumOrNull(): T? =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

private const val KEY_NICKNAME = "member-editor-nickname"
private const val KEY_POSITION = "member-editor-position"
private const val KEY_SECONDARY_POSITION = "member-editor-secondary-position"
private const val KEY_LEVEL = "member-editor-level"
private const val KEY_PREFERRED_SIDE = "member-editor-preferred-side"
private const val KEY_HEIGHT = "member-editor-height"
private const val KEY_MEMBERSHIP = "member-editor-membership"
private const val KEY_BILLING_AMOUNT = "member-editor-billing-amount"
private const val KEY_BILLING_DUE_DAY = "member-editor-billing-due-day"
