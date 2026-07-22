package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.data.GroupDto
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupTimeZone
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.network.NetworkError

internal fun validateGroupSetup(state: GroupSetupState): Map<String, List<String>> = buildMap {
    val form = state.form
    if (!form.name.trim().hasLength(2, 80)) put("name", listOf("must be between 2 and 80 characters"))
    if (form.modality == null) put("modality", listOf("is required"))
    if (form.composition == null) put("composition", listOf("is required"))
    if (form.level == GroupLevel.CUSTOM && !form.customLevel.hasLength(2, 40)) put("customLevel", listOf("is required"))
    if (form.playStyle == GroupPlayStyle.CUSTOM && !form.customPlayStyle.hasLength(2, 40)) {
        put("customPlayStyle", listOf("is required"))
    }
    form.defaultVenue?.let { venue ->
        if (!venue.name.trim().hasLength(2, 120)) put("defaultVenue.name", listOf("is required"))
        if (!venue.address.trim().hasLength(5, 300)) put("defaultVenue.address", listOf("is required"))
    }
    form.regularSlots.forEachIndexed { index, slot ->
        if (slot.startTime.isBlank()) put("regularSlots[$index].startTime", listOf("is required"))
        if (slot.durationMinutes !in 15..480) put("regularSlots[$index].durationMinutes", listOf("must be between 15 and 480"))
    }
    if (form.defaultCapacity != null && form.defaultCapacity !in 2..100) put("defaultCapacity", listOf("must be between 2 and 100"))
    if (form.defaultConfirmationLeadMinutes != null && form.defaultConfirmationLeadMinutes !in 0..10080) {
        put("defaultConfirmationLeadMinutes", listOf("must be between 0 and 10080"))
    }
    if (form.defaultGameFeeCents != null && form.defaultGameFeeCents !in 1..99999999) put("defaultGameFeeCents", listOf("invalid"))
    if (form.monthlyFeeCents != null && form.monthlyFeeCents !in 1..99999999) put("monthlyFeeCents", listOf("invalid"))
    if (form.monthlyFeeCents != null && form.monthlyDueDay !in 1..28) put("monthlyDueDay", listOf("is required"))
    if (state.mode == GroupSetupMode.CREATE && state.timeZone == null) put("timeZone", listOf("is required"))
}

internal fun newGroupDefaults() = GroupSetupForm(
    modality = GroupModality.COURT_VOLLEYBALL,
    composition = GroupComposition.MIXED,
    level = GroupLevel.MIXED_LEVELS,
    defaultCapacity = 12,
    defaultConfirmationLeadMinutes = 360,
)

internal fun String.toGroupTimeZone(): GroupTimeZone? =
    (GroupTimeZone.parse(this) as? GroupTimeZone.ParseResult.Valid)?.value

internal fun GroupDto.toForm() = GroupSetupForm(
    name = name,
    modality = profile?.modality?.name?.let(GroupModality::valueOf),
    composition = profile?.composition?.name?.let(GroupComposition::valueOf),
    description = profile?.description,
    city = profile?.city,
    level = profile?.level?.name?.let(GroupLevel::valueOf),
    customLevel = profile?.customLevel,
    playStyle = profile?.playStyle?.name?.let(GroupPlayStyle::valueOf),
    customPlayStyle = profile?.customPlayStyle,
    defaultVenue = profile?.defaultVenue?.let { GroupVenueForm(it.id, it.name, it.address, it.court) },
    regularSlots = profile?.regularSlots.orEmpty().map {
        GroupRegularSlotForm(it.id, GroupWeekday.valueOf(it.weekday.name), it.startTime, it.durationMinutes)
    },
    defaultCapacity = profile?.defaultCapacity,
    defaultConfirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes,
    defaultGameFeeCents = financeDefaults?.defaultGameFeeCents,
    monthlyFeeCents = financeDefaults?.monthlyFeeCents,
    monthlyDueDay = financeDefaults?.monthlyDueDay,
)

internal fun GroupSetupState.toDraft(draftKey: GroupDraftKey) = GroupSetupDraft(
    resource = draftKey.resource,
    groupId = groupId,
    groupVersion = groupVersion,
    etag = etag,
    commandKey = commandKey,
    form = form,
)

internal fun NetworkError.isProblem(status: Int, code: String): Boolean =
    this is NetworkError.ApiProblemError && problem.status == status && problem.code == code

private fun String?.hasLength(min: Int, max: Int): Boolean {
    val value = this?.trim() ?: return false
    return value.length in min..max && value.none(Char::isISOControl)
}
