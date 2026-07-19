package br.com.saqz.groups.domain.group

import java.time.DayOfWeek
import java.time.LocalTime

enum class GroupModality {
    COURT_VOLLEYBALL,
    BEACH_VOLLEYBALL,
    FOOTVOLLEY,
}

enum class GroupComposition {
    WOMEN,
    MEN,
    MIXED,
}

enum class GroupLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    MIXED_LEVELS,
    CUSTOM,
}

enum class CourtPlayStyle {
    SIX_ZERO,
    FOUR_TWO,
    FIVE_ONE,
    CUSTOM,
}

data class GroupValidationError(
    val field: String,
    val message: String,
)

data class ValidGroupProfileDefaults(
    val name: String,
    val modality: GroupModality,
    val composition: GroupComposition,
    val description: String?,
    val city: String?,
    val level: GroupLevel?,
    val customLevel: String?,
    val playStyle: CourtPlayStyle?,
    val customPlayStyle: String?,
    val defaultVenue: ValidGroupVenue?,
    val regularSlots: List<ValidRegularSlot>,
    val defaultCapacity: Int?,
    val defaultConfirmationLeadMinutes: Int?,
    val defaultGameFeeCents: Long?,
    val monthlyFeeCents: Long?,
    val monthlyDueDay: Int?,
)

data class ValidGroupVenue(
    val name: String,
    val address: String,
    val court: String?,
)

data class ValidRegularSlot(
    val weekday: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int,
)

data class GroupProfileDefaultsInput(
    val name: String?,
    val modality: GroupModality?,
    val composition: GroupComposition?,
    val description: String? = null,
    val city: String? = null,
    val level: GroupLevel? = null,
    val customLevel: String? = null,
    val playStyle: CourtPlayStyle? = null,
    val customPlayStyle: String? = null,
    val defaultVenue: GroupVenueInput? = null,
    val regularSlots: List<RegularSlotInput> = emptyList(),
    val defaultCapacity: Int? = null,
    val defaultConfirmationLeadMinutes: Int? = null,
    val defaultGameFeeCents: Long? = null,
    val monthlyFeeCents: Long? = null,
    val monthlyDueDay: Int? = null,
)

data class GroupVenueInput(
    val name: String?,
    val address: String?,
    val court: String? = null,
)

data class RegularSlotInput(
    val weekday: DayOfWeek?,
    val startTime: LocalTime?,
    val durationMinutes: Int?,
)

sealed interface GroupProfileDefaultsValidation {
    data class Valid(val value: ValidGroupProfileDefaults) : GroupProfileDefaultsValidation

    data class Invalid(val errors: List<GroupValidationError>) : GroupProfileDefaultsValidation
}

object GroupProfileDefaultsValidator {
    fun validate(input: GroupProfileDefaultsInput): GroupProfileDefaultsValidation {
        val errors = mutableListOf<GroupValidationError>()
        val name = requiredText(input.name, "name", 2, 80, errors)
        val description = optionalText(input.description, "description", 2, 500, errors)
        val city = optionalText(input.city, "city", 2, 80, errors)
        val customLevel = optionalText(input.customLevel, "customLevel", 2, 40, errors)
        val customPlayStyle = optionalText(input.customPlayStyle, "customPlayStyle", 2, 40, errors)
        val venue = validateVenue(input.defaultVenue, errors)
        val slots = validateSlots(input.regularSlots, errors)

        if (input.modality == null) errors += required("modality")
        if (input.composition == null) errors += required("composition")
        validateLevel(input.level, customLevel, errors)
        validatePlayStyle(input.modality, input.playStyle, customPlayStyle, errors)
        validateRange(input.defaultCapacity, "defaultCapacity", 2, 100, errors)
        validateRange(input.defaultConfirmationLeadMinutes, "defaultConfirmationLeadMinutes", 0, 10080, errors)
        validateMoney(input.defaultGameFeeCents, "defaultGameFeeCents", errors)
        validateMoney(input.monthlyFeeCents, "monthlyFeeCents", errors)
        validateMonthly(input.monthlyFeeCents, input.monthlyDueDay, errors)

        return if (errors.isNotEmpty()) {
            GroupProfileDefaultsValidation.Invalid(errors)
        } else {
            GroupProfileDefaultsValidation.Valid(
                ValidGroupProfileDefaults(
                    name = name!!,
                    modality = input.modality!!,
                    composition = input.composition!!,
                    description = description,
                    city = city,
                    level = input.level,
                    customLevel = customLevel,
                    playStyle = input.playStyle,
                    customPlayStyle = customPlayStyle,
                    defaultVenue = venue,
                    regularSlots = slots,
                    defaultCapacity = input.defaultCapacity,
                    defaultConfirmationLeadMinutes = input.defaultConfirmationLeadMinutes,
                    defaultGameFeeCents = input.defaultGameFeeCents,
                    monthlyFeeCents = input.monthlyFeeCents,
                    monthlyDueDay = input.monthlyDueDay,
                ),
            )
        }
    }

    fun cleaned(input: GroupProfileDefaultsInput): GroupProfileDefaultsInput =
        input.copy(
            description = blankToNull(input.description)?.trim(),
            city = blankToNull(input.city)?.trim(),
            customLevel = if (input.level == GroupLevel.CUSTOM) blankToNull(input.customLevel)?.trim() else null,
            playStyle = if (input.modality == GroupModality.COURT_VOLLEYBALL) input.playStyle else null,
            customPlayStyle = if (
                input.modality == GroupModality.COURT_VOLLEYBALL &&
                input.playStyle == CourtPlayStyle.CUSTOM
            ) {
                blankToNull(input.customPlayStyle)?.trim()
            } else {
                null
            },
        )

    private fun validateVenue(input: GroupVenueInput?, errors: MutableList<GroupValidationError>): ValidGroupVenue? {
        if (input == null) return null
        val name = requiredText(input.name, "defaultVenue.name", 2, 120, errors)
        val address = requiredText(input.address, "defaultVenue.address", 5, 300, errors)
        val court = optionalText(input.court, "defaultVenue.court", 1, 80, errors)
        return if (name != null && address != null) ValidGroupVenue(name, address, court) else null
    }

    private fun validateSlots(
        inputs: List<RegularSlotInput>,
        errors: MutableList<GroupValidationError>,
    ): List<ValidRegularSlot> =
        inputs.mapIndexedNotNull { index, input ->
            val prefix = "regularSlots[$index]"
            if (input.weekday == null) errors += required("$prefix.weekday")
            if (input.startTime == null) errors += required("$prefix.startTime")
            validateRange(input.durationMinutes, "$prefix.durationMinutes", 15, 480, errors, required = true)
            if (input.weekday != null && input.startTime != null && input.durationMinutes != null) {
                ValidRegularSlot(input.weekday, input.startTime, input.durationMinutes)
            } else {
                null
            }
        }

    private fun validateLevel(level: GroupLevel?, customLevel: String?, errors: MutableList<GroupValidationError>) {
        if (level == GroupLevel.CUSTOM && customLevel == null) errors += required("customLevel")
        if (level != GroupLevel.CUSTOM && customLevel != null) {
            errors += GroupValidationError("customLevel", "must be empty unless level is CUSTOM")
        }
    }

    private fun validatePlayStyle(
        modality: GroupModality?,
        playStyle: CourtPlayStyle?,
        customPlayStyle: String?,
        errors: MutableList<GroupValidationError>,
    ) {
        if (modality != GroupModality.COURT_VOLLEYBALL && playStyle != null) {
            errors += GroupValidationError("playStyle", "must be empty unless modality is COURT_VOLLEYBALL")
        }
        if (modality != GroupModality.COURT_VOLLEYBALL && customPlayStyle != null) {
            errors += GroupValidationError("customPlayStyle", "must be empty unless modality is COURT_VOLLEYBALL")
        }
        if (playStyle == CourtPlayStyle.CUSTOM && customPlayStyle == null) errors += required("customPlayStyle")
        if (playStyle != CourtPlayStyle.CUSTOM && customPlayStyle != null) {
            errors += GroupValidationError("customPlayStyle", "must be empty unless playStyle is CUSTOM")
        }
    }

    private fun validateMonthly(
        monthlyFeeCents: Long?,
        monthlyDueDay: Int?,
        errors: MutableList<GroupValidationError>,
    ) {
        if (monthlyFeeCents == null && monthlyDueDay != null) {
            errors += GroupValidationError("monthlyDueDay", "must be empty unless monthlyFeeCents is present")
        }
        if (monthlyFeeCents != null) validateRange(monthlyDueDay, "monthlyDueDay", 1, 28, errors, required = true)
    }

    private fun requiredText(
        raw: String?,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GroupValidationError>,
    ): String? {
        val normalized = blankToNull(raw)?.trim()
        if (normalized == null) {
            errors += required(field)
            return null
        }
        validateText(normalized, field, min, max, errors)
        return normalized
    }

    private fun optionalText(
        raw: String?,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GroupValidationError>,
    ): String? {
        val normalized = blankToNull(raw)?.trim() ?: return null
        validateText(normalized, field, min, max, errors)
        return normalized
    }

    private fun validateText(
        value: String,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GroupValidationError>,
    ) {
        val length = value.codePointCount(0, value.length)
        if (length !in min..max) {
            errors += GroupValidationError(field, "must be between $min and $max characters")
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            errors += GroupValidationError(field, "must not contain control characters")
        }
    }

    private fun validateMoney(value: Long?, field: String, errors: MutableList<GroupValidationError>) {
        if (value != null && value !in 1..99999999) {
            errors += GroupValidationError(field, "must be between 1 and 99999999 cents")
        }
    }

    private fun validateRange(
        value: Int?,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GroupValidationError>,
        required: Boolean = false,
    ) {
        if (value == null) {
            if (required) errors += required(field)
            return
        }
        if (value !in min..max) errors += GroupValidationError(field, "must be between $min and $max")
    }

    private fun required(field: String) = GroupValidationError(field, "is required")

    private fun blankToNull(raw: String?): String? = raw?.takeUnless { it.isBlank() }
}
