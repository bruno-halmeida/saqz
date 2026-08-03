package br.com.saqz.groups.presentation.gamedetail

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GroupUiError

/** 4c — detalhe do jogo. Roster nominal e contagens ausentes ficam vazios/nulos até o domínio expô-los. */
@Immutable
data class GameDetailState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val header: GameDetailHeaderUi? = null,
    val attendance: GameDetailAttendanceUi? = null,
    val confirmedRoster: List<GameDetailConfirmedUi> = emptyList(),
    val waitlist: List<GameDetailWaitlistUi> = emptyList(),
    val mensalistaPriority: Boolean = false,
    val promotionMode: PromotionMode = PromotionMode.FIFO,
    val isAdmin: Boolean = false,
    val cancelling: Boolean = false,
    val cancelFailed: Boolean = false,
    val cancelDialogOpen: Boolean = false,
    val promotingMemberId: String? = null,
    val promotionFailed: Boolean = false,
    val capacitySheetOpen: Boolean = false,
    val capacityDraft: Int = 2,
    val savingCapacity: Boolean = false,
    val capacityFailed: Boolean = false,
)
@Immutable
data class GameDetailHeaderUi(
    val statusTone: GameDetailStatusTone,
    val confirmationDeadline: String,
    val weekday: GroupWeekday?,
    val dateTime: String,
    val venue: String,
    val durationMinutes: Int,
    val availableSpots: Int,
    val confirmationDeadlineWeekday: GroupWeekday? = null,
)
enum class GameDetailStatusTone { Draft, Published, Cancelled, Completed }

@Immutable
data class GameDetailAttendanceUi(
    val confirmed: Int,
    val capacity: Int,
    val availableSpots: Int,
    val declined: Int = 0,
    val pending: Int = 0,
)
@Immutable
data class GameDetailConfirmedUi(
    val id: String,
    val name: String,
    val isYou: Boolean,
    val position: String,
)
@Immutable
data class GameDetailWaitlistUi(
    val id: String,
    val name: String,
    val queuePosition: Long?,
    val athletePosition: AthletePosition?,
    val isMensalista: Boolean,
)
sealed interface GameDetailIntent {
    data object Retry : GameDetailIntent
    data object Edit : GameDetailIntent
    data object RequestCancel : GameDetailIntent
    data object ConfirmCancel : GameDetailIntent
    data object DismissCancel : GameDetailIntent
    data class Promote(val memberId: String, val reason: String) : GameDetailIntent
    data object OpenCapacitySheet : GameDetailIntent
    data class UpdateCapacity(val value: Int) : GameDetailIntent
    data object SaveCapacity : GameDetailIntent
    data object DismissCapacitySheet : GameDetailIntent
}
sealed interface GameDetailEffect {
    data object OpenEditor : GameDetailEffect
    data object Cancelled : GameDetailEffect
}
