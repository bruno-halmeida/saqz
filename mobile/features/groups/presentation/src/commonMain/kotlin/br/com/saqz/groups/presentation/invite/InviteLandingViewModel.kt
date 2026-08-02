package br.com.saqz.groups.presentation.invite

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.InviteCode
import br.com.saqz.groups.domain.membership.InviteError
import br.com.saqz.groups.domain.membership.InviteGateway
import br.com.saqz.groups.domain.membership.InvitePreview
import br.com.saqz.groups.domain.membership.InviteRedeemStatus
import br.com.saqz.groups.domain.membership.InviteRegularSlot
import kotlinx.coroutines.launch

class InviteLandingViewModel(
    private val code: String,
    private val inviteGateway: InviteGateway,
) : MviViewModel<InviteLandingState, InviteLandingIntent, InviteLandingEffect>(InviteLandingState()) {
    private var generation = 0L

    init {
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
                    update { it.copy(isLoading = false, preview = result.value.toUi(), error = null) }
                }
                is SaqzResult.Failure -> if (isCurrent(requestGeneration)) {
                    update { it.copy(isLoading = false, error = result.error.toUiError()) }
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
                    update { it.copy(isRedeeming = false, error = result.error.toUiError()) }
                }
            }
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = requestGeneration == generation
}

private fun InvitePreview.toUi() = InvitePreviewUi(
    groupName = groupName,
    city = city?.takeIf(String::isNotBlank),
    composition = composition.toCompositionLabel(),
    level = level.toLevelLabel(),
    memberCount = memberCount,
    regularSchedule = regularSlots.toScheduleLabel(),
    inviterName = inviterName,
    nextGame = nextGame?.let { InviteNextGameUi(formatDateTime(it.startsAt), it.venueName, it.court) },
    entryRequiresApproval = entryRequiresApproval,
)

private fun InviteError.toUiError(): InviteLandingError = when (this) {
    InviteError.InvalidOrExpired -> InviteLandingError.Invalid
    is InviteError.Expired -> InviteLandingError.Expired(formatDate(expiredAt))
    is InviteError.RateLimited -> InviteLandingError.RateLimited(retryAfterSeconds)
    InviteError.PlanLimit -> InviteLandingError.PlanLimit
    is InviteError.DataFailure -> InviteLandingError.Network
    InviteError.GroupDeleted -> InviteLandingError.Invalid
}

private fun String?.toCompositionLabel(): String? = when (this) {
    "WOMEN" -> "Feminino"
    "MEN" -> "Masculino"
    "MIXED" -> "Misto"
    else -> null
}

private fun String?.toLevelLabel(): String? = when (this) {
    "BEGINNER" -> "Iniciante"
    "INTERMEDIATE" -> "Intermediário"
    "ADVANCED" -> "Avançado"
    "MIXED_LEVELS" -> "Níveis mistos"
    "CUSTOM" -> "Personalizada"
    else -> null
}

private fun List<InviteRegularSlot>.toScheduleLabel(): String? {
    val days = map { it.weekday }.distinct().mapNotNull { it.toDayLabel() }
    if (days.isEmpty()) return null
    return days.joinToString(" e ").replaceFirstChar(Char::uppercaseChar)
}

private fun String.toDayLabel(): String? = when (this) {
    "SUNDAY" -> "domingos"
    "MONDAY" -> "segundas"
    "TUESDAY" -> "terças"
    "WEDNESDAY" -> "quartas"
    "THURSDAY" -> "quintas"
    "FRIDAY" -> "sextas"
    "SATURDAY" -> "sábados"
    else -> null
}

private fun formatDateTime(value: String): String {
    val date = formatDate(value)
    val time = value.substringAfter('T', "").take(5)
    return if (date != value && time.length == 5) "$date · $time" else value
}

private fun formatDate(value: String): String {
    val date = value.take(10)
    if (date.length != 10 || date[4] != '-' || date[7] != '-') return value
    return "${date.substring(8, 10)}/${date.substring(5, 7)}/${date.substring(0, 4)}"
}
