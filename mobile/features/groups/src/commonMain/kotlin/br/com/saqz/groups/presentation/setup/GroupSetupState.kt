package br.com.saqz.groups.presentation.setup

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone

@Immutable
data class GroupSetupState(
    val mode: GroupSetupMode,
    val form: GroupSetupForm,
    val commandKey: String,
    val groupId: String? = null,
    val groupVersion: Long? = null,
    val etag: String? = null,
    val timeZone: GroupTimeZone? = null,
    val timezoneSelectionRequired: Boolean = false,
    val validationAttempted: Boolean = false,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = false,
    val conflict: Boolean = false,
    val successGroupId: String? = null,
    val photoPending: Boolean = false,
    val photoRetryAvailable: Boolean = false,
    val error: GroupSetupError? = null,
    val isOrganizer: Boolean = true,
) {
    val canEdit: Boolean get() = GroupSetupRules.isEditable(isOrganizer, successGroupId)
    val capacityRange: IntRange get() = GroupSetupRules.capacityRange
    val defaultCapacity: Int get() = GroupSetupRules.defaultCapacity
    val defaultMonthlyDueDay: Int get() = GroupSetupRules.defaultMonthlyDueDay
    val showPlayStyle: Boolean get() = form.modality == GroupModality.COURT_VOLLEYBALL
    val showCustomLevel: Boolean get() = form.level == GroupLevel.CUSTOM
    val showCustomPlayStyle: Boolean get() = showPlayStyle && form.playStyle == GroupPlayStyle.CUSTOM
}
