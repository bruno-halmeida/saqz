package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant

enum class AttendanceStatus { CONFIRMED, DECLINED, WAITLISTED }
enum class AttendanceIntent { CONFIRM, DECLINE }
enum class AttendanceSource { SELF, ORGANIZER, SYSTEM }

data class AttendanceDecisionContext(
    val gameStatus: GameStatus,
    val confirmationDeadline: Instant,
    val now: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val currentStatus: AttendanceStatus?,
    val source: AttendanceSource,
    val reason: String? = null,
)

enum class AttendanceDenial {
    NOT_PUBLISHED,
    FROZEN,
    DEADLINE_PASSED,
    REASON_REQUIRED,
    REASON_INVALID,
}

sealed interface AttendanceDecision {
    data class Transition(
        val oldStatus: AttendanceStatus?,
        val newStatus: AttendanceStatus,
        val source: AttendanceSource,
        val reason: String?,
        val changed: Boolean,
        val allocateWaitlistSequence: Boolean,
        val createGameCharge: Boolean,
    ) : AttendanceDecision

    data class Denied(val reason: AttendanceDenial) : AttendanceDecision
}

object AttendanceTransitionPolicy {
    fun decide(context: AttendanceDecisionContext, intent: AttendanceIntent): AttendanceDecision {
        require(context.capacity >= 0)
        require(context.confirmedCount >= 0)

        when (context.gameStatus) {
            GameStatus.DRAFT -> return AttendanceDecision.Denied(AttendanceDenial.NOT_PUBLISHED)
            GameStatus.CANCELLED, GameStatus.COMPLETED ->
                return AttendanceDecision.Denied(AttendanceDenial.FROZEN)
            GameStatus.PUBLISHED -> Unit
        }
        if (context.source == AttendanceSource.SELF && context.now > context.confirmationDeadline) {
            return AttendanceDecision.Denied(AttendanceDenial.DEADLINE_PASSED)
        }
        val reason = when (context.source) {
            AttendanceSource.ORGANIZER -> validateOrganizerReason(context.reason)
                ?: return AttendanceDecision.Denied(
                    if (context.reason.isNullOrBlank()) AttendanceDenial.REASON_REQUIRED else AttendanceDenial.REASON_INVALID,
                )
            AttendanceSource.SELF, AttendanceSource.SYSTEM -> null
        }
        val target = when (intent) {
            AttendanceIntent.DECLINE -> AttendanceStatus.DECLINED
            AttendanceIntent.CONFIRM -> confirmationTarget(context)
        }
        val changed = context.currentStatus != target
        return AttendanceDecision.Transition(
            oldStatus = context.currentStatus,
            newStatus = target,
            source = context.source,
            reason = reason,
            changed = changed,
            allocateWaitlistSequence = changed && target == AttendanceStatus.WAITLISTED,
            createGameCharge = changed && target == AttendanceStatus.CONFIRMED,
        )
    }

    private fun confirmationTarget(context: AttendanceDecisionContext): AttendanceStatus = when (context.currentStatus) {
        AttendanceStatus.CONFIRMED -> AttendanceStatus.CONFIRMED
        AttendanceStatus.WAITLISTED -> AttendanceStatus.WAITLISTED
        AttendanceStatus.DECLINED, null ->
            if (context.confirmedCount < context.capacity) AttendanceStatus.CONFIRMED else AttendanceStatus.WAITLISTED
    }

    private fun validateOrganizerReason(raw: String?): String? {
        val reason = raw?.trim()?.takeUnless(String::isBlank) ?: return null
        if (reason.codePointCount(0, reason.length) !in 2..500) return null
        if (reason.codePoints().anyMatch(Character::isISOControl)) return null
        return reason
    }
}
