package br.com.saqz.groups.application.invite.preview

import br.com.saqz.groups.application.invite.InviteTokenDigest
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

data class PreviewInviteCard(
    val groupName: String,
    val city: String?,
    val composition: GroupComposition?,
    val level: GroupLevel?,
    val memberCount: Int,
    val regularSlots: List<PreviewRegularSlot>,
    val inviterName: String?,
    val entryRequiresApproval: Boolean,
    val expiresAt: Instant?,
    val nextGame: PreviewNextGame?,
)

data class PreviewRegularSlot(
    val weekday: DayOfWeek,
    val startTime: LocalTime,
)

data class PreviewNextGame(
    val startsAt: Instant,
    val venueName: String,
    val court: String?,
)

data class PreviewableInvite(
    val groupDeleted: Boolean,
    val expiredAt: Instant?,
    val card: PreviewInviteCard,
)

data class PreviewInviteAttemptWindow(
    val windowStartedAt: Instant,
    val invalidCount: Int,
) {
    init {
        require(invalidCount in 0..10) { "Invalid invite count must be between zero and ten" }
    }
}

data class RecordInvalidPreviewInviteAttempt(
    val userId: UUID,
    val windowStartedAt: Instant,
    val invalidCount: Int,
)

interface PreviewInviteRepository {
    fun lockAttemptWindow(userId: UUID, initializedAt: Instant): PreviewInviteAttemptWindow

    fun recordInvalidAttempt(command: RecordInvalidPreviewInviteAttempt)

    fun findInvite(digest: InviteTokenDigest, now: Instant): PreviewableInvite?
}

sealed interface PreviewInviteResult {
    data class Success(val card: PreviewInviteCard) : PreviewInviteResult

    data object Invalid : PreviewInviteResult

    data class Expired(val expiredAt: Instant) : PreviewInviteResult

    data class AttemptLimit(val retryAfterSeconds: Int) : PreviewInviteResult
}
