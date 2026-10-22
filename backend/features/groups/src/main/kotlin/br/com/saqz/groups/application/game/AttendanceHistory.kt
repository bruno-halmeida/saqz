package br.com.saqz.groups.application.game

import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** O que aconteceu numa linha do histórico (JOGO-012); o app escolhe a frase por aqui. */
enum class AttendanceHistoryKind {
    CONFIRMED,
    WAITLISTED,
    DECLINED,
    GAVE_UP,
    LEFT_WAITLIST,
    PROMOTED,
    ORGANIZER_ADDED,
    ORGANIZER_WAITLISTED,
    ORGANIZER_REMOVED,
    SYSTEM_REMOVED,
    SYSTEM_WAITLISTED,
    GUEST_JOINED,
    GUEST_LEFT,
    ;

    companion object {
        /** Cada `source` × transição gravada em `attendance_events` cai num tipo só. */
        fun of(source: AttendanceSource, old: AttendanceStatus?, new: AttendanceStatus, guest: Boolean): AttendanceHistoryKind = when {
            old == AttendanceStatus.WAITLISTED && new == AttendanceStatus.CONFIRMED && source == AttendanceSource.SYSTEM -> PROMOTED
            guest -> if (new == AttendanceStatus.DECLINED) GUEST_LEFT else GUEST_JOINED
            source == AttendanceSource.ORGANIZER -> when (new) {
                AttendanceStatus.CONFIRMED -> ORGANIZER_ADDED
                AttendanceStatus.WAITLISTED -> ORGANIZER_WAITLISTED
                AttendanceStatus.DECLINED -> ORGANIZER_REMOVED
            }
            source == AttendanceSource.SYSTEM -> when (new) {
                AttendanceStatus.DECLINED -> SYSTEM_REMOVED
                AttendanceStatus.WAITLISTED -> SYSTEM_WAITLISTED
                AttendanceStatus.CONFIRMED -> PROMOTED
            }
            else -> when (new) {
                AttendanceStatus.CONFIRMED -> CONFIRMED
                AttendanceStatus.WAITLISTED -> WAITLISTED
                AttendanceStatus.DECLINED -> when (old) {
                    AttendanceStatus.CONFIRMED -> GAVE_UP
                    AttendanceStatus.WAITLISTED -> LEFT_WAITLIST
                    else -> DECLINED
                }
            }
        }
    }
}

/**
 * Posição na lista (`occurred_at DESC`, subida automática antes da saída que abriu a vaga no mesmo instante,
 * `id DESC`): a página seguinte começa depois deste evento.
 */
data class AttendanceHistoryCursor(val occurredAt: Instant, val rank: Int, val id: UUID) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$occurredAt|$rank|$id".toByteArray())

    companion object {
        fun decode(value: String): AttendanceHistoryCursor? = runCatching {
            val (occurredAt, rank, id) = String(Base64.getUrlDecoder().decode(value)).split('|', limit = 3)
            AttendanceHistoryCursor(Instant.parse(occurredAt), rank.toInt(), UUID.fromString(id))
        }.getOrNull()
    }
}

data class AttendanceHistoryRow(
    val id: UUID,
    val occurredAt: Instant,
    val rank: Int,
    val memberId: UUID,
    val memberName: String,
    val guestSeq: Int,
    val guestName: String?,
    val actorId: UUID,
    val actorName: String,
    val source: AttendanceSource,
    val oldStatus: AttendanceStatus?,
    val newStatus: AttendanceStatus,
    val reason: String?,
    /** Depois do prazo de confirmação do jogo (a espera não foi por jogo cheio). */
    val afterDeadline: Boolean,
    /** Subida automática: quem saiu no mesmo instante, quando foi uma pessoa só. */
    val freedByName: String?,
    val freedByGuest: Boolean,
)

data class AttendanceHistoryEntry(val row: AttendanceHistoryRow, val kind: AttendanceHistoryKind)

interface AttendanceHistoryRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?
    fun gameExists(groupId: UUID, gameId: UUID): Boolean
    fun page(groupId: UUID, gameId: UUID, after: AttendanceHistoryCursor?, limit: Int): List<AttendanceHistoryRow>
}

sealed interface AttendanceHistoryResult {
    data class Success(val entries: List<AttendanceHistoryEntry>, val nextCursor: AttendanceHistoryCursor?) : AttendanceHistoryResult
    data object Hidden : AttendanceHistoryResult
    data object Forbidden : AttendanceHistoryResult
}

/** JOGO-012: histórico de respostas do jogo, só dono e admin; jogo aberto, realizado ou cancelado. */
class AttendanceHistory(private val repository: AttendanceHistoryRepository) {
    fun list(actorId: UUID, groupId: UUID, gameId: UUID, after: AttendanceHistoryCursor?, limit: Int = PAGE_SIZE): AttendanceHistoryResult {
        when (repository.role(actorId, groupId)) {
            null -> return AttendanceHistoryResult.Hidden
            GroupRole.ATHLETE -> return AttendanceHistoryResult.Forbidden
            GroupRole.OWNER, GroupRole.ADMIN -> Unit
        }
        if (!repository.gameExists(groupId, gameId)) return AttendanceHistoryResult.Hidden
        val size = limit.coerceIn(1, MAX_PAGE_SIZE)
        val rows = repository.page(groupId, gameId, after, size + 1)
        val page = rows.take(size)
        val next = if (rows.size > size) page.last().let { AttendanceHistoryCursor(it.occurredAt, it.rank, it.id) } else null
        return AttendanceHistoryResult.Success(
            page.map { row ->
                val kind = AttendanceHistoryKind.of(row.source, row.oldStatus, row.newStatus, row.guestSeq > 0)
                // Motivo só do organizador (o banco também exige; a conta excluída vira "Conta excluída").
                AttendanceHistoryEntry(row.copy(reason = row.reason.takeIf { row.source == AttendanceSource.ORGANIZER }), kind)
            },
            next,
        )
    }

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 100
    }
}
