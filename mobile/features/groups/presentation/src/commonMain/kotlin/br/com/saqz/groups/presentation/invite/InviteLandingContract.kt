package br.com.saqz.groups.presentation.invite

import androidx.compose.runtime.Immutable

@Immutable
data class InviteLandingState(
    val isLoading: Boolean = true,
    val isRedeeming: Boolean = false,
    val preview: InvitePreviewUi? = null,
    val requestSent: Boolean = false,
    val error: InviteLandingError? = null,
)

@Immutable
data class InvitePreviewUi(
    val groupName: String,
    val city: String?,
    val compositionCode: String?,
    val levelCode: String?,
    val memberCount: Int,
    val regularWeekdays: List<String>,
    val inviterName: String,
    val nextGame: InviteNextGameUi?,
    val entryRequiresApproval: Boolean,
)

@Immutable
data class InviteNextGameUi(
    val weekdayCode: String,
    val date: String,
    val time: String,
    val venueName: String,
    val court: String?,
)

sealed interface InviteLandingError {
    data object Invalid : InviteLandingError
    data class Expired(val expiredAt: String) : InviteLandingError
    data class RateLimited(val retryAfterSeconds: Int?) : InviteLandingError
    data object PlanLimit : InviteLandingError
    data object Network : InviteLandingError
}

sealed interface InviteLandingIntent {
    data object Retry : InviteLandingIntent
    data object PrimaryAction : InviteLandingIntent
    data object BrowseOtherGroups : InviteLandingIntent
    data object ExploreApp : InviteLandingIntent
    data object OpenAnotherGroup : InviteLandingIntent
    data object RequestNewInvite : InviteLandingIntent
}

sealed interface InviteLandingEffect {
    data class Joined(val groupId: String) : InviteLandingEffect
    data object RequestSent : InviteLandingEffect
    data object BrowseOtherGroups : InviteLandingEffect
    data object ExploreApp : InviteLandingEffect
    data object OpenAnotherGroup : InviteLandingEffect
    data object RequestNewInvite : InviteLandingEffect
}
