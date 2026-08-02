package br.com.saqz.groups.application.athlete

import br.com.saqz.groups.domain.AthleteLevel
import br.com.saqz.groups.domain.AthletePosition
import br.com.saqz.groups.domain.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupModality

object AthleteAttributeValidator {
    fun validate(
        modality: GroupModality?,
        nickname: String?,
        position: AthletePosition?,
        secondaryPosition: AthletePosition?,
        level: AthleteLevel?,
        preferredSide: AthletePreferredSide?,
        heightCm: Int?,
        monthlyFeeCents: Long?,
        monthlyDueDay: Int?,
    ): Map<String, List<String>> = buildMap {
        fun addError(field: String, message: String) {
            put(field, get(field).orEmpty() + message)
        }

        if (modality != GroupModality.COURT_VOLLEYBALL) {
            if (position != null) addError("position", "is only available for COURT_VOLLEYBALL")
            if (secondaryPosition != null) addError("secondaryPosition", "is only available for COURT_VOLLEYBALL")
            if (heightCm != null) addError("heightCm", "is only available for COURT_VOLLEYBALL")
        }
        if (secondaryPosition != null && secondaryPosition == position) {
            addError("secondaryPosition", "must differ from position")
        }
        if (modality != GroupModality.BEACH_VOLLEYBALL && modality != GroupModality.FOOTVOLLEY && preferredSide != null) {
            addError("preferredSide", "is only available for BEACH_VOLLEYBALL or FOOTVOLLEY")
        }
        validateNickname(nickname)?.let { addError("nickname", it) }
        if (heightCm != null && heightCm !in 100..250) {
            addError("heightCm", "must be between 100 and 250")
        }
        if (monthlyFeeCents != null && monthlyFeeCents <= 0) {
            addError("monthlyFeeCents", "must be greater than 0")
        }
        if (monthlyDueDay != null && monthlyDueDay !in 1..28) {
            addError("monthlyDueDay", "must be between 1 and 28")
        }
    }

    private fun validateNickname(value: String?): String? {
        if (value == null) return null
        val codePointCount = value.codePointCount(0, value.length)
        if (value.trim(' ') != value) return "must not have leading or trailing spaces"
        if (codePointCount !in 2..40) return "must be between 2 and 40 characters"
        if (value.codePoints().anyMatch { it == 0 || Character.isISOControl(it) }) {
            return "must not contain control characters"
        }
        return null
    }
}
