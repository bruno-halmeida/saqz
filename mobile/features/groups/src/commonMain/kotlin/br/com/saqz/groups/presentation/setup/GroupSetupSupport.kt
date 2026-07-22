package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.model.GroupDraftKey
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.model.GroupSetupDraft
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.domain.group.GroupTimeZone
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.model.GroupSetupForm as DraftGroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm as DraftGroupVenue
import br.com.saqz.groups.model.GroupRegularSlotForm as DraftGroupRegularSlot

internal object GroupSetupRules {
    val capacityRange = 2..100
    const val defaultCapacity = 12
    const val defaultMonthlyDueDay = 10

    fun isEditable(isOrganizer: Boolean, successGroupId: String?): Boolean =
        isOrganizer && successGroupId == null
}

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
    if (form.defaultCapacity != null && form.defaultCapacity !in GroupSetupRules.capacityRange) {
        put("defaultCapacity", listOf("must be between 2 and 100"))
    }
    if (form.defaultConfirmationLeadMinutes != null && form.defaultConfirmationLeadMinutes !in 0..10080) {
        put("defaultConfirmationLeadMinutes", listOf("must be between 0 and 10080"))
    }
    if (form.defaultGameFeeCents != null && form.defaultGameFeeCents !in 1L..99999999L) {
        put("defaultGameFeeCents", listOf("invalid"))
    }
    if (form.monthlyFeeCents != null && form.monthlyFeeCents !in 1L..99999999L) {
        put("monthlyFeeCents", listOf("invalid"))
    }
    if (form.monthlyFeeCents != null && form.monthlyDueDay !in 1..28) put("monthlyDueDay", listOf("is required"))
    if (state.mode == GroupSetupMode.CREATE && state.timeZone == null) put("timeZone", listOf("is required"))
}

internal fun newGroupDefaults() = GroupSetupForm(
    modality = GroupModality.COURT_VOLLEYBALL,
    composition = GroupComposition.MIXED,
    level = GroupLevel.MIXED_LEVELS,
    defaultCapacity = GroupSetupRules.defaultCapacity,
    defaultConfirmationLeadMinutes = 360,
)

internal fun String.toGroupTimeZone(): GroupTimeZone? = runCatching { kotlinx.datetime.TimeZone.of(this) }
    .getOrNull()
    ?.let { GroupTimeZone(it.id) }

internal fun Group.toForm() = GroupSetupForm(
    name = name,
    modality = profile?.modality,
    composition = profile?.composition,
    description = profile?.description,
    city = profile?.city,
    level = profile?.level,
    customLevel = profile?.customLevel,
    playStyle = profile?.playStyle,
    customPlayStyle = profile?.customPlayStyle,
    defaultVenue = profile?.defaultVenue?.let { GroupVenue(it.id, it.name, it.address, it.court) },
    regularSlots = profile?.regularSlots.orEmpty().map {
        GroupRegularSlot(it.id, it.weekday, it.startTime, it.durationMinutes)
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
    form = form.toDraftForm(),
)

internal fun DraftGroupSetupForm.toDomainForm() = GroupSetupForm(
    name = name,
    modality = modality?.name?.let(GroupModality::valueOf),
    composition = composition?.name?.let(GroupComposition::valueOf),
    description = description,
    city = city,
    level = level?.name?.let(GroupLevel::valueOf),
    customLevel = customLevel,
    playStyle = playStyle?.name?.let(GroupPlayStyle::valueOf),
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.let { GroupVenue(it.id, it.name, it.address, it.court) },
    regularSlots = regularSlots.map {
        GroupRegularSlot(it.id, GroupWeekday.valueOf(it.weekday.name), it.startTime, it.durationMinutes)
    },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    defaultGameFeeCents = defaultGameFeeCents,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
)

internal fun GroupSetupForm.toDraftForm() = DraftGroupSetupForm(
    name = name,
    modality = modality?.name?.let(br.com.saqz.groups.model.GroupModality::valueOf),
    composition = composition?.name?.let(br.com.saqz.groups.model.GroupComposition::valueOf),
    description = description,
    city = city,
    level = level?.name?.let(br.com.saqz.groups.model.GroupLevel::valueOf),
    customLevel = customLevel,
    playStyle = playStyle?.name?.let(br.com.saqz.groups.model.GroupPlayStyle::valueOf),
    customPlayStyle = customPlayStyle,
    defaultVenue = defaultVenue?.let { DraftGroupVenue(it.id, it.name, it.address, it.court) },
    regularSlots = regularSlots.map {
        DraftGroupRegularSlot(
            it.id,
            br.com.saqz.groups.model.GroupWeekday.valueOf(it.weekday.name),
            it.startTime,
            it.durationMinutes,
        )
    },
    defaultCapacity = defaultCapacity,
    defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
    defaultGameFeeCents = defaultGameFeeCents,
    monthlyFeeCents = monthlyFeeCents,
    monthlyDueDay = monthlyDueDay,
)

private fun String?.hasLength(min: Int, max: Int): Boolean {
    val value = this?.trim() ?: return false
    return value.length in min..max && value.none(Char::isISOControl)
}
