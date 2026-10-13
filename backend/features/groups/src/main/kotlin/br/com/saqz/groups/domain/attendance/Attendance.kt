package br.com.saqz.groups.domain.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import java.time.Instant

enum class AttendanceStatus { CONFIRMED, DECLINED, WAITLISTED }
enum class AttendanceIntent { CONFIRM, DECLINE, PROMOTE }
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
    val membershipType: AthleteMembershipType,
    val mensalistaPriority: Boolean = true,
    val guest: Boolean = false,
    val promotionMode: PromotionMode = PromotionMode.FIFO,
    /** Início do jogo; depois dele a resposta do próprio atleta é negada. `null` = sem checagem. */
    val startsAt: Instant? = null,
)

enum class AttendanceDenial {
    NOT_PUBLISHED,
    FROZEN,
    REASON_REQUIRED,
    REASON_INVALID,
    NOT_WAITLISTED,
    NO_CAPACITY,
    MANUAL_PROMOTION_ONLY,
    HOST_NOT_GOING,
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
        // Depois do início do jogo ninguém mais responde por conta própria; antes disso o prazo
        // só muda para onde vai o "Vou" (sempre para a espera), nunca nega a resposta.
        if (context.source == AttendanceSource.SELF && context.startsAt != null && context.now >= context.startsAt) {
            return AttendanceDecision.Denied(AttendanceDenial.FROZEN)
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
            AttendanceIntent.PROMOTE -> when (context.currentStatus) {
                AttendanceStatus.WAITLISTED -> if (context.confirmedCount < context.capacity) {
                    AttendanceStatus.CONFIRMED
                } else {
                    return AttendanceDecision.Denied(AttendanceDenial.NO_CAPACITY)
                }
                else -> return AttendanceDecision.Denied(AttendanceDenial.NOT_WAITLISTED)
            }
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

    /**
     * Regra do grupo (decisão de 13/10/2026), valendo para o "Vou" do próprio atleta:
     * - depois do prazo: sempre a espera, mensalista inclusive; a promoção segue o grupo (FIFO/MANUAL);
     * - antes do prazo, FIFO: com prioridade ligada o avulso espera até o prazo; sem ela todos entram se houver vaga;
     * - antes do prazo, MANUAL: com prioridade ligada só o mensalista confirma direto; desligada, todos esperam.
     */
    private fun confirmationTarget(context: AttendanceDecisionContext): AttendanceStatus = when (context.currentStatus) {
        AttendanceStatus.CONFIRMED -> AttendanceStatus.CONFIRMED
        AttendanceStatus.WAITLISTED -> AttendanceStatus.WAITLISTED
        AttendanceStatus.DECLINED, null -> when {
            // Convidado entra sempre pela lista de espera (VUL-239), mesmo com vaga.
            context.guest -> AttendanceStatus.WAITLISTED
            context.source == AttendanceSource.SELF && !entersDirectly(context) -> AttendanceStatus.WAITLISTED
            context.confirmedCount < context.capacity -> AttendanceStatus.CONFIRMED
            else -> AttendanceStatus.WAITLISTED
        }
    }

    private fun entersDirectly(context: AttendanceDecisionContext): Boolean {
        if (context.now > context.confirmationDeadline) return false
        val mensalista = context.membershipType == AthleteMembershipType.MENSALISTA
        return when (context.promotionMode) {
            PromotionMode.FIFO -> mensalista || !context.mensalistaPriority
            PromotionMode.MANUAL -> mensalista && context.mensalistaPriority
        }
    }

    private fun validateOrganizerReason(raw: String?): String? {
        val reason = raw?.trim()?.takeUnless(String::isBlank) ?: return null
        if (reason.codePointCount(0, reason.length) !in 2..500) return null
        if (reason.codePoints().anyMatch(Character::isISOControl)) return null
        return reason
    }
}
