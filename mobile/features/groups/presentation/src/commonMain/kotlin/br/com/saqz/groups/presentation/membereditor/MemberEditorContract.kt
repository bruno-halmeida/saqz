package br.com.saqz.groups.presentation.membereditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError

enum class MemberEditorOperation { Save, Billing, Role, Remove }

@Immutable
data class MemberEditorState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val name: String = "",
    val displayName: String = "",
    val nickname: String = "",
    val joinedAtLabel: String = "",
    val games: Int = 0,
    val attendanceRate: Int? = null,
    val absences: Int = 0,
    val modality: GroupModality? = null,
    val composition: GroupComposition? = null,
    val position: AthletePosition? = null,
    val secondaryPosition: AthletePosition? = null,
    val level: AthleteLevel? = null,
    val preferredSide: AthletePreferredSide? = null,
    val heightCm: Int? = null,
    val heightText: String = "",
    val membershipType: AthleteMembershipType = AthleteMembershipType.AVULSO,
    val active: Boolean = true,
    val monthlyFeeOverrideCents: Long? = null,
    val monthlyDueDayOverride: Int? = null,
    val defaultMonthlyFeeCents: Long? = null,
    val defaultMonthlyDueDay: Int? = null,
    val billingAmountText: String = "",
    val billingDueDay: Int = 10,
    val role: GroupRole = GroupRole.ATHLETE,
    val canManageRoles: Boolean = true,
    val billingSheetOpen: Boolean = false,
    val removeSheetOpen: Boolean = false,
    val operation: MemberEditorOperation? = null,
) {
    val effectiveMonthlyFeeCents: Long? get() = monthlyFeeOverrideCents ?: defaultMonthlyFeeCents
    val effectiveMonthlyDueDay: Int? get() = monthlyDueDayOverride ?: defaultMonthlyDueDay
    val isOwner: Boolean get() = role == GroupRole.OWNER
    val isCourt: Boolean get() = modality == GroupModality.COURT_VOLLEYBALL
}

sealed interface MemberEditorIntent {
    data object Retry : MemberEditorIntent
    data class NicknameChanged(val value: String) : MemberEditorIntent
    data class PositionSelected(val value: AthletePosition?) : MemberEditorIntent
    data class SecondaryPositionSelected(val value: AthletePosition?) : MemberEditorIntent
    data class LevelSelected(val value: AthleteLevel?) : MemberEditorIntent
    data class PreferredSideSelected(val value: AthletePreferredSide?) : MemberEditorIntent
    data class HeightChanged(val value: String) : MemberEditorIntent
    data class MembershipSelected(val value: AthleteMembershipType) : MemberEditorIntent
    data object OpenBilling : MemberEditorIntent
    data object DismissBilling : MemberEditorIntent
    data class BillingAmountChanged(val value: String) : MemberEditorIntent
    data class BillingDueDaySelected(val value: Int) : MemberEditorIntent
    data object SaveBilling : MemberEditorIntent
    data class AdminChanged(val value: Boolean) : MemberEditorIntent
    data object Save : MemberEditorIntent
    data object OpenRemove : MemberEditorIntent
    data object DismissRemove : MemberEditorIntent
    data object ConfirmRemove : MemberEditorIntent
}

sealed interface MemberEditorEffect {
    data object Close : MemberEditorEffect
    data object Removed : MemberEditorEffect
}
