package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.game.AttendanceHistory
import br.com.saqz.groups.application.game.AttendanceHistoryCursor
import br.com.saqz.groups.application.game.AttendanceHistoryEntry
import br.com.saqz.groups.application.game.AttendanceHistoryResult
import br.com.saqz.groups.application.photo.MemberPhotoDirectory
import br.com.saqz.groups.application.photo.NoMemberPhotos
import br.com.saqz.groups.application.photo.tagsOf
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AttendanceHistoryEntryResponse(
    val id: UUID,
    val occurredAt: Instant,
    val kind: String,
    val memberId: UUID,
    val memberName: String,
    /** Foto do membro (MEM-006); convidado não tem. */
    val photoTag: String?,
    val guestSeq: Int,
    val guestName: String?,
    val actorId: UUID,
    val actorName: String,
    val source: String,
    val oldStatus: String?,
    val newStatus: String,
    /** Só quando o organizador mudou. */
    val reason: String?,
    val afterDeadline: Boolean,
    /** Subida automática: de quem era a vaga, quando dá para saber. */
    val freedByName: String?,
    val freedByGuest: Boolean,
)

data class AttendanceHistoryPageResponse(val entries: List<AttendanceHistoryEntryResponse>, val nextCursor: String?)

/** JOGO-012 (histórico de respostas): rota só de dono e admin; atleta 403, quem não é do grupo 404. */
@RestController
class AttendanceHistoryController(
    private val actors: VerifiedGroupActorResolver,
    private val history: AttendanceHistory,
    private val photos: MemberPhotoDirectory = NoMemberPhotos,
) {
    @GetMapping("/api/groups/{groupId}/games/{gameId}/attendance/history")
    fun list(
        @AuthenticationPrincipal identity: RequestIdentity,
        @PathVariable groupId: String,
        @PathVariable gameId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) limit: Int?,
    ): AttendanceHistoryPageResponse {
        val after = cursor?.takeIf { it.isNotBlank() }?.let {
            AttendanceHistoryCursor.decode(it) ?: throw InvalidGroupRequestException(mapOf("cursor" to listOf("is required or invalid")))
        }
        val actor = actors.resolve(identity)
        val group = uuid(groupId)
        return when (val result = history.list(actor, group, uuid(gameId), after, limit ?: AttendanceHistory.PAGE_SIZE)) {
            is AttendanceHistoryResult.Success -> {
                val tags = photos.tagsOf(actor, group, result.entries.map { it.row.memberId })
                AttendanceHistoryPageResponse(result.entries.map { it.response(tags) }, result.nextCursor?.encode())
            }
            AttendanceHistoryResult.Hidden -> throw GameNotFoundException()
            AttendanceHistoryResult.Forbidden -> throw AccessForbiddenException()
        }
    }

    private fun uuid(value: String): UUID = runCatching { UUID.fromString(value) }.getOrElse { throw GameNotFoundException() }
}

private fun AttendanceHistoryEntry.response(tags: Map<UUID, String>) = AttendanceHistoryEntryResponse(
    id = row.id,
    occurredAt = row.occurredAt,
    kind = kind.name,
    memberId = row.memberId,
    memberName = row.memberName,
    photoTag = tags[row.memberId].takeIf { row.guestSeq == 0 },
    guestSeq = row.guestSeq,
    guestName = row.guestName,
    actorId = row.actorId,
    actorName = row.actorName,
    source = row.source.name,
    oldStatus = row.oldStatus?.name,
    newStatus = row.newStatus.name,
    reason = row.reason,
    afterDeadline = row.afterDeadline,
    freedByName = row.freedByName,
    freedByGuest = row.freedByGuest,
)
