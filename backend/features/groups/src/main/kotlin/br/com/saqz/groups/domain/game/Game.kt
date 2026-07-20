package br.com.saqz.groups.domain.game

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.IanaTimeZone
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

enum class GameStatus { DRAFT, PUBLISHED, CANCELLED, COMPLETED }
enum class GameMutation { EDIT, PUBLISH, CANCEL, COMPLETE }

data class GameValidationError(val field: String, val message: String)

data class GameVenueInput(
    val venueId: UUID? = null,
    val name: String?,
    val address: String?,
    val court: String? = null,
)

data class GameDraftInput(
    val title: String?,
    val venue: GameVenueInput?,
    val localDate: LocalDate?,
    val localTime: LocalTime?,
    val zoneId: String?,
    val startsAt: Instant?,
    val durationMinutes: Int?,
    val capacity: Int?,
    val confirmationDeadline: Instant?,
    val gameFeeCents: Long? = null,
    val notes: String? = null,
)

data class GameVenueSnapshot(
    val venueId: UUID?,
    val name: String,
    val address: String,
    val court: String?,
)

data class GameSnapshot(
    val title: String,
    val venue: GameVenueSnapshot,
    val localDate: LocalDate,
    val localTime: LocalTime,
    val zoneId: IanaTimeZone,
    val startsAt: Instant,
    val durationMinutes: Int,
    val capacity: Int,
    val confirmationDeadline: Instant,
    val gameFeeCents: Long?,
    val notes: String?,
)

sealed interface GameDraftValidation {
    data class Valid(val snapshot: GameSnapshot) : GameDraftValidation
    data class Invalid(val errors: List<GameValidationError>) : GameDraftValidation
}

object GameDraftValidator {
    fun validate(input: GameDraftInput): GameDraftValidation {
        val errors = mutableListOf<GameValidationError>()
        val title = requiredText(input.title, "title", 2, 120, errors)
        val venue = validateVenue(input.venue, errors)
        val zone = input.zoneId?.let { runCatching { IanaTimeZone.from(it.trim()) }.getOrNull() }
        if (input.localDate == null) errors += required("localDate")
        if (input.localTime == null) errors += required("localTime")
        if (input.zoneId.isNullOrBlank()) errors += required("zoneId")
        else if (zone == null) errors += GameValidationError("zoneId", "must be a valid IANA timezone")
        if (input.startsAt == null) errors += required("startsAt")
        range(input.durationMinutes, "durationMinutes", 15, 480, errors)
        range(input.capacity, "capacity", 2, 100, errors)
        if (input.confirmationDeadline == null) errors += required("confirmationDeadline")
        if (input.startsAt != null && input.confirmationDeadline != null && input.confirmationDeadline > input.startsAt) {
            errors += GameValidationError("confirmationDeadline", "must be at or before startsAt")
        }
        if (input.gameFeeCents != null && input.gameFeeCents !in 1..99_999_999) {
            errors += GameValidationError("gameFeeCents", "must be between 1 and 99999999 cents")
        }
        val notes = optionalText(input.notes, "notes", 2, 500, errors)

        return if (errors.isNotEmpty()) GameDraftValidation.Invalid(errors) else GameDraftValidation.Valid(
            GameSnapshot(
                title = title!!,
                venue = venue!!,
                localDate = input.localDate!!,
                localTime = input.localTime!!,
                zoneId = zone!!,
                startsAt = input.startsAt!!,
                durationMinutes = input.durationMinutes!!,
                capacity = input.capacity!!,
                confirmationDeadline = input.confirmationDeadline!!,
                gameFeeCents = input.gameFeeCents,
                notes = notes,
            ),
        )
    }

    private fun validateVenue(input: GameVenueInput?, errors: MutableList<GameValidationError>): GameVenueSnapshot? {
        if (input == null) {
            errors += required("venue")
            return null
        }
        val name = requiredText(input.name, "venue.name", 2, 120, errors)
        val address = requiredText(input.address, "venue.address", 5, 300, errors)
        val court = optionalText(input.court, "venue.court", 1, 80, errors)
        return if (name != null && address != null) GameVenueSnapshot(input.venueId, name, address, court) else null
    }

    private fun requiredText(
        raw: String?,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GameValidationError>,
    ): String? {
        val value = raw?.takeUnless(String::isBlank)?.trim()
        if (value == null) {
            errors += required(field)
            return null
        }
        text(value, field, min, max, errors)
        return value
    }

    private fun optionalText(
        raw: String?,
        field: String,
        min: Int,
        max: Int,
        errors: MutableList<GameValidationError>,
    ): String? {
        val value = raw?.takeUnless(String::isBlank)?.trim() ?: return null
        text(value, field, min, max, errors)
        return value
    }

    private fun text(value: String, field: String, min: Int, max: Int, errors: MutableList<GameValidationError>) {
        if (value.codePointCount(0, value.length) !in min..max) {
            errors += GameValidationError(field, "must be between $min and $max characters")
        }
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            errors += GameValidationError(field, "must not contain control characters")
        }
    }

    private fun range(value: Int?, field: String, min: Int, max: Int, errors: MutableList<GameValidationError>) {
        if (value == null) errors += required(field)
        else if (value !in min..max) errors += GameValidationError(field, "must be between $min and $max")
    }

    private fun required(field: String) = GameValidationError(field, "is required")
}

data class GroupGameDefaults(
    val title: String? = null,
    val venue: GameVenueSnapshot? = null,
    val durationMinutes: Int? = null,
    val capacity: Int? = null,
    val confirmationLeadMinutes: Int? = null,
    val gameFeeCents: Long? = null,
)

sealed interface NullableGameFeeOverride {
    data object UseDefault : NullableGameFeeOverride
    data class Value(val cents: Long?) : NullableGameFeeOverride
}

data class CreateGameInput(
    val title: String? = null,
    val venue: GameVenueInput? = null,
    val localDate: LocalDate?,
    val localTime: LocalTime?,
    val zoneId: String?,
    val startsAt: Instant?,
    val durationMinutes: Int? = null,
    val capacity: Int? = null,
    val confirmationDeadline: Instant? = null,
    val gameFee: NullableGameFeeOverride = NullableGameFeeOverride.UseDefault,
    val notes: String? = null,
)

object GameDefaultSnapshotFactory {
    fun copy(defaults: GroupGameDefaults, input: CreateGameInput): GameDraftInput {
        val startsAt = input.startsAt
        return GameDraftInput(
            title = input.title ?: defaults.title,
            venue = input.venue ?: defaults.venue?.let { GameVenueInput(it.venueId, it.name, it.address, it.court) },
            localDate = input.localDate,
            localTime = input.localTime,
            zoneId = input.zoneId,
            startsAt = startsAt,
            durationMinutes = input.durationMinutes ?: defaults.durationMinutes,
            capacity = input.capacity ?: defaults.capacity,
            confirmationDeadline = input.confirmationDeadline ?: startsAt?.let { start ->
                defaults.confirmationLeadMinutes?.let { start.minusSeconds(it.toLong() * 60) }
            },
            gameFeeCents = when (val fee = input.gameFee) {
                NullableGameFeeOverride.UseDefault -> defaults.gameFeeCents
                is NullableGameFeeOverride.Value -> fee.cents
            },
            notes = input.notes,
        )
    }
}

data class Game(
    val id: UUID,
    val groupId: UUID,
    val snapshot: GameSnapshot,
    val status: GameStatus = GameStatus.DRAFT,
    val version: Long = 1,
    val detachedFromSeries: Boolean = false,
)

object GameLifecyclePolicy {
    fun canMutate(status: GameStatus, mutation: GameMutation): Boolean = when (mutation) {
        GameMutation.EDIT -> status == GameStatus.DRAFT || status == GameStatus.PUBLISHED
        GameMutation.PUBLISH -> status == GameStatus.DRAFT
        GameMutation.CANCEL -> status == GameStatus.PUBLISHED
        GameMutation.COMPLETE -> status == GameStatus.PUBLISHED
    }

    fun target(mutation: GameMutation): GameStatus? = when (mutation) {
        GameMutation.EDIT -> null
        GameMutation.PUBLISH -> GameStatus.PUBLISHED
        GameMutation.CANCEL -> GameStatus.CANCELLED
        GameMutation.COMPLETE -> GameStatus.COMPLETED
    }

    fun visibleTo(status: GameStatus, role: GroupRole?): Boolean = when {
        role == null -> false
        status == GameStatus.DRAFT -> role == GroupRole.OWNER || role == GroupRole.ADMIN
        else -> true
    }
}
