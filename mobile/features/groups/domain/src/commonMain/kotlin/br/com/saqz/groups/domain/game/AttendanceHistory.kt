package br.com.saqz.groups.domain.game

import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult

/** O que aconteceu numa linha do histórico de respostas (JOGO-012); o servidor classifica. */
enum class AttendanceHistoryKind {
    Confirmed,
    Waitlisted,
    Declined,
    GaveUp,
    LeftWaitlist,
    Promoted,
    OrganizerAdded,
    OrganizerWaitlisted,
    OrganizerRemoved,
    SystemRemoved,
    SystemWaitlisted,
    GuestJoined,
    GuestLeft,
}

enum class AttendanceHistorySource { Self, Organizer, System }

/** Estado em que a pessoa ficou depois da mudança. */
enum class AttendanceHistoryStatus { Confirmed, Waitlisted, Declined }

data class AttendanceHistoryEntry(
    val id: String,
    val occurredAt: String,
    val kind: AttendanceHistoryKind,
    val memberId: String,
    val memberName: String,
    val photoTag: String?,
    /** 0 = o próprio membro; 1..n = convidado dele. */
    val guestSeq: Int,
    val guestName: String?,
    val actorName: String,
    val source: AttendanceHistorySource,
    val oldStatus: AttendanceHistoryStatus?,
    val newStatus: AttendanceHistoryStatus,
    /** Só quando o organizador mudou. */
    val reason: String?,
    /** Mudou depois do prazo de confirmação (a espera não foi por jogo cheio). */
    val afterDeadline: Boolean,
    /** Subida automática: de quem era a vaga, quando o servidor sabe. */
    val freedByName: String?,
    val freedByGuest: Boolean,
)

/** Uma página, do mais recente para trás; [nextCursor] nulo no fim. */
data class AttendanceHistoryPage(val entries: List<AttendanceHistoryEntry>, val nextCursor: String?)

/** JOGO-012: só dono e admin; atleta recebe 403. */
interface AttendanceHistoryGateway {
    suspend fun page(groupId: GroupId, gameId: String, cursor: String?): SaqzResult<AttendanceHistoryPage, GameError>
}
