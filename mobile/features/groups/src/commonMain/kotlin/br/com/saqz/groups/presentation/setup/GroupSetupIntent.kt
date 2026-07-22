package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupVenue

sealed interface GroupSetupIntent {
    data class UpdateName(val value: String) : GroupSetupIntent
    data class UpdateModality(val value: GroupModality) : GroupSetupIntent
    data class UpdateComposition(val value: GroupComposition) : GroupSetupIntent
    data class UpdateDescription(val value: String) : GroupSetupIntent
    data class UpdateCity(val value: String) : GroupSetupIntent
    data class UpdateLevel(val value: GroupLevel?) : GroupSetupIntent
    data class UpdateCustomLevel(val value: String) : GroupSetupIntent
    data class UpdatePlayStyle(val value: GroupPlayStyle?) : GroupSetupIntent
    data class UpdateCustomPlayStyle(val value: String) : GroupSetupIntent
    data class UpdateVenue(val value: GroupVenue?) : GroupSetupIntent
    data class UpdateSlots(val value: List<GroupRegularSlot>) : GroupSetupIntent
    data class UpdateDefaultCapacity(val value: Int?) : GroupSetupIntent
    data class UpdateConfirmationLeadMinutes(val value: Int?) : GroupSetupIntent
    data class UpdateDefaultGameFeeCents(val value: Long?) : GroupSetupIntent
    data class UpdateMonthlyFee(val cents: Long?, val dueDay: Int?) : GroupSetupIntent
    data class SelectFallbackTimeZone(val identifier: String) : GroupSetupIntent
    data class SetPhotoPending(val value: Boolean) : GroupSetupIntent
    data object Submit : GroupSetupIntent
    data object ReloadConflict : GroupSetupIntent
    data object RetryPhotoUpload : GroupSetupIntent
    data object PhotoUploadFailed : GroupSetupIntent
    data object PhotoUploadSucceeded : GroupSetupIntent
}
