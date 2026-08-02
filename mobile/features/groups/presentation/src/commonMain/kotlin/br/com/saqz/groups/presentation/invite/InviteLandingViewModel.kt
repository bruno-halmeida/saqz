package br.com.saqz.groups.presentation.invite

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.port.GroupSystemTimeZonePort
import br.com.saqz.groups.port.GroupSystemTimeZoneResult
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

class InviteLandingViewModel(
    private val code: String,
    private val inviteGateway: InviteGateway,
    private val timeZonePort: GroupSystemTimeZonePort,
) : MviViewModel<InviteLandingState, InviteLandingIntent, InviteLandingEffect>(InviteLandingState()) {
    private var generation = 0L
    // UTC keeps invite instants renderable when the platform cannot provide a valid timezone.
    private var timeZone: TimeZone = TimeZone.UTC

    init {
        timeZonePort.detect { result -> timeZone = result.toTimeZoneOrUtc() }
        loadPreview()
    }

    override fun onIntent(intent: InviteLandingIntent) {
        when (intent) {
            InviteLandingIntent.Retry -> retry()
            InviteLandingIntent.PrimaryAction -> redeem()
            InviteLandingIntent.BrowseOtherGroups -> emit(InviteLandingEffect.BrowseOtherGroups)
            InviteLandingIntent.ExploreApp -> emit(InviteLandingEffect.ExploreApp)
            InviteLandingIntent.OpenAnotherGroup -> emit(InviteLandingEffect.OpenAnotherGroup)
            InviteLandingIntent.RequestNewInvite -> emit(InviteLandingEffect.RequestNewInvite)
        }
    }

    private fun retry() {
        if (state.value.preview == null) loadPreview() else redeem()
    }

    private fun loadPreview() {
        val requestGeneration = ++generation
        update { it.copy(isLoading = true, isRedeeming = false, preview = null, requestSent = false, error = null) }
        viewModelScope.launch {
            when (val result = inviteGateway.preview(InviteCode(code))) {
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isLoading = false, preview = result.value.toUi(timeZone), error = null) }
                }
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isLoading = false, error = result.error.toUiError(timeZone)) }
                }
            }
        }
    }

    private fun redeem() {
        if (state.value.preview == null || state.value.isRedeeming || state.value.requestSent) return
        val requestGeneration = ++generation
        update { it.copy(isRedeeming = true, error = null) }
        viewModelScope.launch {
            when (val result = inviteGateway.redeem(InviteCode(code))) {
                is SaqzResult.Success -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isRedeeming = false) }
                    when (result.value.status) {
                        InviteRedeemStatus.JOINED -> emit(InviteLandingEffect.Joined(result.value.groupId.value))
                        InviteRedeemStatus.PENDING -> {
                            update { it.copy(requestSent = true) }
                            emit(InviteLandingEffect.RequestSent)
                        }
                    }
                }
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isRedeeming = false, error = result.error.toUiError(timeZone)) }
                }
            }
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}

private fun InvitePreview.toUi(timeZone: TimeZone) = InvitePreviewUi(
    groupName = groupName,
    city = city?.takeIf(String::isNotBlank),
    compositionCode = composition,
    levelCode = level,
    memberCount = memberCount,
    regularWeekdays = regularSlots.map { it.weekday }.distinct(),
    inviterName = inviterName,
    nextGame = nextGame?.let { formatDateTime(it.startsAt, timeZone, it.venueName, it.court) },
    entryRequiresApproval = entryRequiresApproval,
)

private fun InviteError.toUiError(timeZone: TimeZone): InviteLandingError = when (this) {
    InviteError.InvalidOrExpired -> InviteLandingError.Invalid
    is InviteError.Expired -> InviteLandingError.Expired(formatDate(expiredAt, timeZone))
    is InviteError.RateLimited -> InviteLandingError.RateLimited(retryAfterSeconds)
    InviteError.PlanLimit -> InviteLandingError.PlanLimit
    is InviteError.DataFailure -> InviteLandingError.Network
    InviteError.GroupDeleted -> InviteLandingError.Invalid
}

private fun GroupSystemTimeZoneResult.toTimeZoneOrUtc(): TimeZone = when (this) {
    is GroupSystemTimeZoneResult.Available -> runCatching { TimeZone.of(value.id) }
        .getOrDefault(TimeZone.UTC)
    GroupSystemTimeZoneResult.Unavailable -> TimeZone.UTC
}

private fun formatDateTime(
    value: String,
    timeZone: TimeZone,
    venueName: String,
    court: String?,
): InviteNextGameUi {
    val localDateTime = runCatching { Instant.parse(value).toLocalDateTime(timeZone) }.getOrNull()
    return if (localDateTime == null) {
        InviteNextGameUi("", value, "", venueName, court)
    } else {
        InviteNextGameUi(
            weekdayCode = localDateTime.date.dayOfWeek.name,
            date = localDateTime.date.toDayMonth(),
            time = "${localDateTime.hour.toString().padStart(2, '0')}h${localDateTime.minute.toString().padStart(2, '0')}",
            venueName = venueName,
            court = court,
        )
    }
}

private fun formatDate(value: String, timeZone: TimeZone): String {
    val date = runCatching { Instant.parse(value).toLocalDateTime(timeZone).date }.getOrNull()
        ?: runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
        ?: return value
    return "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
}

private fun LocalDate.toDayMonth(): String =
    "${day.toString().padStart(2, '0')}/${(month.ordinal + 1).toString().padStart(2, '0')}"
