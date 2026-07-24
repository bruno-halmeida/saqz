package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import java.time.Instant
import java.util.UUID

sealed interface UndoPromotionResult {
    data class Success(val attendance: AttendanceRecord, val event: AttendanceEvent) : UndoPromotionResult
    data object Hidden : UndoPromotionResult
    data object Forbidden : UndoPromotionResult
    data object Frozen : UndoPromotionResult
    data object NotAutomaticPromotion : UndoPromotionResult
}

// Desfaz uma promoção que o próprio sistema concedeu (prazo expirado, desconfirmação
// ou aumento de capacidade): o atleta volta para a espera na posição de origem. O que
// tornou a promoção "automática" é o evento mais recente do atleta ter source=SYSTEM;
// promoções manuais do organizador ficam de fora por design (o épico só cobre "desfazer
// liberação automática"), então o undo só reusa attendance_events como origem da verdade.
class UndoAutomaticPromotion(
    private val transaction: TransactionRunner,
    private val repository: AttendanceCommandRepository,
    private val now: () -> Instant,
    private val ids: () -> UUID = UUID::randomUUID,
) {
    fun execute(actorId: UUID, groupId: UUID, gameId: UUID, memberId: UUID): UndoPromotionResult = transaction.inTransaction {
        val aggregate = repository.lock(groupId, gameId, memberId, actorId)
            ?: return@inTransaction UndoPromotionResult.Hidden
        if (aggregate.actorRole != GroupRole.OWNER && aggregate.actorRole != GroupRole.ADMIN) {
            return@inTransaction UndoPromotionResult.Forbidden
        }
        if (aggregate.gameStatus != GameStatus.PUBLISHED) return@inTransaction UndoPromotionResult.Frozen
        val current = aggregate.current
        if (current?.status != AttendanceStatus.CONFIRMED) return@inTransaction UndoPromotionResult.NotAutomaticPromotion
        val promotion = repository.latestEvent(groupId, gameId, memberId)
            ?: return@inTransaction UndoPromotionResult.NotAutomaticPromotion
        val isAutomaticPromotion = promotion.source == AttendanceSource.SYSTEM &&
            promotion.oldStatus == AttendanceStatus.WAITLISTED &&
            promotion.newStatus == AttendanceStatus.CONFIRMED &&
            promotion.previousWaitlistSequence != null
        if (!isAutomaticPromotion) return@inTransaction UndoPromotionResult.NotAutomaticPromotion
        val timestamp = now()
        val restored = current.copy(
            status = AttendanceStatus.WAITLISTED,
            waitlistSequence = promotion.previousWaitlistSequence,
            updatedAt = timestamp,
            version = current.version + 1,
        )
        repository.save(restored)
        val event = AttendanceEvent(
            ids(),
            aggregate.gameId,
            aggregate.groupId,
            aggregate.memberId,
            actorId,
            AttendanceSource.ORGANIZER,
            AttendanceStatus.CONFIRMED,
            AttendanceStatus.WAITLISTED,
            "Promoção automática desfeita pelo organizador",
            timestamp,
        )
        repository.append(event)
        UndoPromotionResult.Success(restored, event)
    }
}
