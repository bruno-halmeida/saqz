package br.com.saqz.groups.domain.game.recurrence

import br.com.saqz.groups.domain.IanaTimeZone
import br.com.saqz.groups.domain.game.GameVenueSnapshot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

data class WeeklySlotRule(
    val slotKey: UUID,
    val weekday: DayOfWeek,
    val localTime: LocalTime,
    val durationMinutes: Int,
    val venue: GameVenueSnapshot,
    val capacity: Int,
    val confirmationLeadMinutes: Int,
    val gameFeeCents: Long? = null,
    val title: String,
)

data class WeeklySeriesRule(
    val groupId: UUID,
    val seriesId: UUID,
    val revisionId: UUID,
    val zoneId: String,
    val localStartDate: LocalDate,
    val localEndDate: LocalDate? = null,
    val activeThroughDate: LocalDate? = null,
    val slots: List<WeeklySlotRule>,
)

data class RecurrenceValidationError(val field: String, val message: String)

data class ResolvedWeeklyOccurrence(
    val groupId: UUID,
    val seriesId: UUID,
    val revisionId: UUID,
    val slot: WeeklySlotRule,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: IanaTimeZone,
    val startsAt: Instant,
) {
    val confirmationDeadline: Instant = startsAt.minusSeconds(slot.confirmationLeadMinutes.toLong() * 60)
}

sealed interface WeeklyRecurrenceResult {
    data class Valid(val occurrences: List<ResolvedWeeklyOccurrence>) : WeeklyRecurrenceResult
    data class Invalid(val errors: List<RecurrenceValidationError>) : WeeklyRecurrenceResult
}

object WeeklyRecurrenceResolver {
    const val HORIZON_WEEKS: Long = 12

    fun resolve(rule: WeeklySeriesRule, from: LocalDate): WeeklyRecurrenceResult {
        val errors = validate(rule)
        if (errors.isNotEmpty()) return WeeklyRecurrenceResult.Invalid(errors)
        val zone = IanaTimeZone.from(rule.zoneId.trim())
        val zoneId = ZoneId.of(zone.value)
        val first = maxOf(from, rule.localStartDate)
        val exclusiveHorizon = from.plusWeeks(HORIZON_WEEKS)
        val ruleEnd = listOfNotNull(rule.localEndDate, rule.activeThroughDate).minOrNull()
        val exclusiveEnd = ruleEnd?.plusDays(1)?.let { minOf(it, exclusiveHorizon) } ?: exclusiveHorizon
        if (!first.isBefore(exclusiveEnd)) return WeeklyRecurrenceResult.Valid(emptyList())

        val slotsByDay = rule.slots.groupBy(WeeklySlotRule::weekday)
        val occurrences = buildList {
            var date = first
            while (date.isBefore(exclusiveEnd)) {
                slotsByDay[date.dayOfWeek].orEmpty().forEach { slot ->
                    add(
                        ResolvedWeeklyOccurrence(
                            rule.groupId,
                            rule.seriesId,
                            rule.revisionId,
                            slot,
                            date,
                            slot.localTime,
                            zone,
                            resolveInstant(date, slot.localTime, zoneId),
                        ),
                    )
                }
                date = date.plusDays(1)
            }
        }
        return WeeklyRecurrenceResult.Valid(occurrences)
    }

    fun resolveInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
        val local = LocalDateTime.of(date, time)
        val offsets = zone.rules.getValidOffsets(local)
        return when {
            offsets.size == 1 -> local.atOffset(offsets.single()).toInstant()
            offsets.size >= 2 -> local.atOffset(offsets.first()).toInstant()
            else -> {
                val transition = requireNotNull(zone.rules.getTransition(local))
                local.plusSeconds(transition.duration.seconds).atOffset(transition.offsetAfter).toInstant()
            }
        }
    }

    private fun validate(rule: WeeklySeriesRule): List<RecurrenceValidationError> = buildList {
        if (runCatching { IanaTimeZone.from(rule.zoneId.trim()) }.isFailure) {
            add(RecurrenceValidationError("zoneId", "must be a valid IANA timezone"))
        }
        if (rule.localEndDate != null && rule.localEndDate < rule.localStartDate) {
            add(RecurrenceValidationError("localEndDate", "must be on or after localStartDate"))
        }
        if (rule.activeThroughDate != null && rule.activeThroughDate < rule.localStartDate) {
            add(RecurrenceValidationError("activeThroughDate", "must be on or after localStartDate"))
        }
        if (rule.slots.isEmpty()) add(RecurrenceValidationError("slots", "must not be empty"))
        rule.slots.groupBy(WeeklySlotRule::slotKey).filterValues { it.size > 1 }.keys.forEach {
            add(RecurrenceValidationError("slots", "slot keys must be unique"))
        }
        rule.slots.forEachIndexed { index, slot ->
            validateText(slot.title, "slots[$index].title", 2, 120)?.let(::add)
            validateText(slot.venue.name, "slots[$index].venue.name", 2, 120)?.let(::add)
            validateText(slot.venue.address, "slots[$index].venue.address", 5, 300)?.let(::add)
            slot.venue.court?.let { validateText(it, "slots[$index].venue.court", 1, 80)?.let(::add) }
            if (slot.durationMinutes !in 15..480) add(RecurrenceValidationError("slots[$index].durationMinutes", "must be between 15 and 480"))
            if (slot.capacity !in 2..100) add(RecurrenceValidationError("slots[$index].capacity", "must be between 2 and 100"))
            if (slot.confirmationLeadMinutes !in 0..10080) add(RecurrenceValidationError("slots[$index].confirmationLeadMinutes", "must be between 0 and 10080"))
            if (slot.gameFeeCents != null && slot.gameFeeCents !in 1..99_999_999) add(RecurrenceValidationError("slots[$index].gameFeeCents", "must be between 1 and 99999999"))
        }
    }

    private fun validateText(value: String, field: String, min: Int, max: Int): RecurrenceValidationError? =
        if (
            value != value.trim() ||
            value.codePointCount(0, value.length) !in min..max ||
            value.codePoints().anyMatch(Character::isISOControl)
        ) {
            RecurrenceValidationError(field, "must be trimmed, contain no controls, and be between $min and $max characters")
        } else null
}
