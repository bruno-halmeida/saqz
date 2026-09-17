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
    val guest: GameGuestUi = GameGuestUi(),
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
    val confirmationOpen: Boolean = true,
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
    /** `null` = membro. Convidado: de quem é e o que quem olha pode fazer com ele. */
    val guest: GameGuestRowUi? = null,
)
@Immutable
data class GameDetailWaitlistUi(
    val id: String,
    val name: String,
    val queuePosition: Long?,
    val athletePosition: AthletePosition?,
    val isMensalista: Boolean,
    /** `null` = membro. Convidado: de quem é e o que quem olha pode fazer com ele. */
    val guest: GameGuestRowUi? = null,
)

@Immutable
data class GameGuestRowUi(
    val hostId: String,
    val guestSeq: Int,
    val hostName: String,
    /** O convidado é de quem está olhando. */
    val isYours: Boolean,
    /** Anfitrião com confirmações abertas, ou gestor com jogo publicado. */
    val canRemove: Boolean,
)

enum class GameGuestHint { Default, NeedAnswer, Closed }

@Immutable
data class GameGuestRemovalUi(
    val rowId: String,
    val hostId: String,
    val guestSeq: Int,
    val name: String,
    val confirmed: Boolean,
)

@Immutable
data class GameGuestUi(
    /** Botão aparece só com jogo publicado e quem olha sendo membro que joga (tem `memberId`). */
    val visible: Boolean = false,
    val enabled: Boolean = false,
    val hint: GameGuestHint = GameGuestHint.Default,
    /** "R$ 25,00" quando o jogo tem taxa; `null` = a folha não fala de cobrança. */
    val feeLabel: String? = null,
    val sheetOpen: Boolean = false,
    val name: String = "",
    val adding: Boolean = false,
    val addFailed: Boolean = false,
    val removal: GameGuestRemovalUi? = null,
    val removing: Boolean = false,
    val removeFailed: Boolean = false,
    /** Nome de quem acabou de entrar (`joined = true`) ou sair; a UI mostra o toast e manda `DismissGuestNotice`. */
    val noticeName: String? = null,
    val noticeJoined: Boolean = true,
) {
    val canSubmit: Boolean get() = name.trim().length in MIN_NAME..MAX_NAME && !adding

    private companion object {
        const val MIN_NAME = 2
        const val MAX_NAME = 80
    }
}

sealed interface GameDetailIntent {
    data object Retry : GameDetailIntent
    data object Edit : GameDetailIntent
    data object OpenSettlement : GameDetailIntent
    data object RequestCancel : GameDetailIntent
    data object ConfirmCancel : GameDetailIntent
    data object DismissCancel : GameDetailIntent
    data class Promote(val memberId: String, val reason: String, val guestSeq: Int = 0) : GameDetailIntent
    data object OpenCapacitySheet : GameDetailIntent
    data class UpdateCapacity(val value: Int) : GameDetailIntent
    data object SaveCapacity : GameDetailIntent
    data object DismissCapacitySheet : GameDetailIntent
    data object OpenGuestSheet : GameDetailIntent
    data class UpdateGuestName(val value: String) : GameDetailIntent
    data object SubmitGuest : GameDetailIntent
    data object DismissGuestSheet : GameDetailIntent
    data class RequestRemoveGuest(val rowId: String) : GameDetailIntent
    data object ConfirmRemoveGuest : GameDetailIntent
    data object DismissRemoveGuest : GameDetailIntent
    data object DismissGuestNotice : GameDetailIntent
}
sealed interface GameDetailEffect {
    data object OpenEditor : GameDetailEffect
    data object OpenSettlement : GameDetailEffect
    data object Cancelled : GameDetailEffect
}
